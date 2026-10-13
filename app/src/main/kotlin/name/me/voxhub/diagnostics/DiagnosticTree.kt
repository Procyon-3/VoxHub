package name.me.voxhub.diagnostics

import android.util.Log
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy
import ch.qos.logback.core.util.FileSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.Collections
import java.util.IdentityHashMap

internal class DiagnosticTree(
    private val store: DiagnosticStore,
    private val debug: Boolean,
) : Timber.DebugTree() {
    private val context = LoggerContext()
    init {
        context.statusManager.add(ch.qos.logback.core.status.StatusListener { status ->
            if (status.level >= ch.qos.logback.core.status.Status.ERROR) writeFailed = true
        })
    }
    private sealed interface Write {
        data class Event(val event: ILoggingEvent) : Write
        data class Flush(val completed: CompletableDeferred<Unit>) : Write
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var writes: Channel<Write>? = null
    private var writer: Job? = null
    @Volatile var writeFailed = false
        private set

    fun open() = synchronized(store.lock) {
        try {
            check(writes == null && writer?.isCompleted != false)
            store.prepare()
            val target = RollingFileAppender<ILoggingEvent>().apply {
                this.context = this@DiagnosticTree.context
                file = File(store.logs, "application.log").absolutePath
                encoder = PatternLayoutEncoder().apply {
                    this.context = this@DiagnosticTree.context
                    pattern = "%date{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level [%thread] %logger: %msg%n"
                    start()
                }
            }
            val rolling = FixedWindowRollingPolicy().apply {
                context = this@DiagnosticTree.context
                setParent(target)
                fileNamePattern = File(store.logs, "application.%i.log").absolutePath
                minIndex = 1
                maxIndex = 4
                start()
            }
            target.rollingPolicy = rolling
            target.triggeringPolicy = SizeBasedTriggeringPolicy<ILoggingEvent>().apply {
                context = this@DiagnosticTree.context
                maxFileSize = FileSize.valueOf("1MB")
                start()
            }
            target.start()
            writeFailed = !target.isStarted
            if (!target.isStarted) return@synchronized
            val pending = Channel<Write>(256)
            writer = scope.launch {
                try {
                    for (write in pending) {
                        when (write) {
                            is Write.Event -> synchronized(store.lock) {
                                try {
                                    target.doAppend(write.event)
                                    if (!target.isStarted) writeFailed = true
                                } catch (_: Exception) {
                                    writeFailed = true
                                }
                            }
                            is Write.Flush -> write.completed.complete(Unit)
                        }
                    }
                } finally {
                    try {
                        synchronized(store.lock) { target.stop() }
                    } catch (_: Exception) {
                        writeFailed = true
                    }
                }
            }
            writes = pending
        } catch (_: Exception) {
            writeFailed = true
        }
    }

    suspend fun flush() {
        val pending = writes ?: return
        val completed = CompletableDeferred<Unit>()
        pending.send(Write.Flush(completed))
        completed.await()
    }

    suspend fun close() {
        val pending = writes ?: return
        writes = null
        pending.close()
        // The worker needs store.lock to finish; callers must not hold it while joining.
        writer?.join()
        writer = null
    }

    override fun isLoggable(tag: String?, priority: Int): Boolean = debug || priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Timber appends Throwable.toString(), which can contain response bodies or user input.
        val safeMessage = if (t == null) message else message.substringBefore('\n')
            .takeUnless { it.startsWith(t.javaClass.name) }.orEmpty() + "\n" + safeStackTrace(t)
        if (debug) super.log(priority, tag, safeMessage, null)
        val pending = writes ?: return
        val level = when (priority) {
            Log.VERBOSE -> Level.TRACE
            Log.DEBUG -> Level.DEBUG
            Log.INFO -> Level.INFO
            Log.WARN -> Level.WARN
            else -> Level.ERROR
        }
        try {
            val event = LoggingEvent(javaClass.name, context.getLogger(tag ?: "VoxHub"), level,
                safeMessage, null, null)
            event.threadName = Thread.currentThread().name
            event.prepareForDeferredProcessing()
            // Saturation drops the event and marks diagnostics unavailable instead of blocking UI.
            if (pending.trySend(Write.Event(event)).isFailure) writeFailed = true
        } catch (_: Exception) {
            writeFailed = true
        }
    }
}

/** Preserve exception classes and frames, never exception messages (including causes). */
internal fun safeStackTrace(error: Throwable): String = buildString {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    fun appendError(value: Throwable, prefix: String) {
        if (!seen.add(value) || seen.size > 16) return
        append(prefix).append(value.javaClass.name).append('\n')
        value.stackTrace.take(128).forEach { append("\tat ").append(it).append('\n') }
        value.suppressed.forEach { appendError(it, "Suppressed: ") }
        value.cause?.let { appendError(it, "Caused by: ") }
    }
    appendError(error, "")
}
