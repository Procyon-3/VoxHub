package name.me.voxhub.diagnostics

import android.app.Application
import android.net.Uri
import android.os.Build
import io.sentry.IConnectionStatusProvider
import io.sentry.NoOpConnectionStatusProvider
import io.sentry.SendCachedEnvelopeFireAndForgetIntegration
import io.sentry.SendFireAndForgetEnvelopeSender
import io.sentry.SendFireAndForgetOutboxSender
import io.sentry.NoOpLogger
import io.sentry.Sentry
import io.sentry.UncaughtExceptionHandlerIntegration
import io.sentry.android.core.AnrIntegration
import io.sentry.android.core.AnrV2Integration
import io.sentry.android.core.AppLifecycleIntegration
import io.sentry.android.core.EnvelopeFileObserverIntegration
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import name.me.voxhub.BuildConfig
import timber.log.Timber
import java.io.File

class LocalDiagnostics(private val application: Application) {
    private val store = DiagnosticStore(File(application.filesDir, "diagnostics"))
    private val tree = DiagnosticTree(store, BuildConfig.DEBUG)
    private val operations = Mutex()
    private val preferences = application.getSharedPreferences("diagnostics", Application.MODE_PRIVATE)
    @Volatile private var clearedBefore = preferences.getLong("cleared_before", 0)
    @Volatile private var captureFailed = false
    @Volatile private var transport: LocalEnvelopeTransport? = null

    val isAvailable: Boolean get() = !tree.writeFailed && !captureFailed && transport?.isHealthy == true

    fun initialize() {
        application.cacheDir.listFiles().orEmpty()
            .filter { it.name.startsWith("diagnostics-") && it.extension == "zip" }
            .forEach { it.delete() }
        tree.open()
        Timber.plant(tree)
        startCapture()
        Timber.i("Application started version=%s build=%s", BuildConfig.VERSION_NAME, BuildConfig.BUILD_TYPE)
    }

    private fun startCapture() {
        try {
            SentryAndroid.init(application, NoOpLogger.getInstance()) { options ->
                // This is a format requirement of the SDK, not a remote destination.
                options.dsn = "https://local@diagnostics.invalid/1"
                options.setTransportFactory(io.sentry.ITransportFactory { configured, _ ->
                    LocalEnvelopeTransport(store, configured).also { transport = it }
                })
                options.cacheDirPath = store.sdk.absolutePath
                options.maxCacheItems = 10
                // Local queue draining must also work in airplane mode. A distinct no-op
                // provider prevents the SDK from substituting Android network monitoring.
                options.setConnectionStatusProvider(object : IConnectionStatusProvider by NoOpConnectionStatusProvider() {})
                options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
                options.environment = BuildConfig.BUILD_TYPE
                options.isDebug = false
                options.setLogger(NoOpLogger.getInstance())
                options.setFatalLogger(NoOpLogger.getInstance())
                options.dataCollection.userInfo = false
                options.dataCollection.httpBodies = emptySet()
                options.dataCollection.databaseQueryData = false
                options.dataCollection.filePaths = false
                options.isSendClientReports = false
                options.isSendModules = false
                options.isEnableAutoSessionTracking = false
                options.isEnableAutoActivityLifecycleTracing = false
                options.isEnablePerformanceV2 = false
                options.isEnableFramesTracking = false
                options.isEnableScopeSync = false
                options.isEnableScopePersistence = false
                options.tracesSampleRate = 0.0
                options.profilesSampleRate = 0.0
                options.metrics.isEnabled = false
                options.sessionReplay.sessionSampleRate = 0.0
                options.sessionReplay.onErrorSampleRate = 0.0
                options.isAttachScreenshot = false
                options.isAttachViewHierarchy = false
                options.isCollectAdditionalContext = false
                options.isCollectExternalStorageContext = false
                options.isEnableRootCheck = false
                options.isEnableAppLifecycleBreadcrumbs = false
                options.maxBreadcrumbs = 0
                options.isEnableNdk = false
                options.isAnrEnabled = true
                options.isAnrReportInDebug = true
                options.isAttachAnrThreadDump = false
                // Keep only capture and on-device queue processing. No Timber integration:
                // handled business errors belong to our bounded log, not crash reports.
                options.integrations.removeAll { integration ->
                    integration !is UncaughtExceptionHandlerIntegration &&
                        integration !is AnrIntegration && integration !is AnrV2Integration &&
                        integration !is AppLifecycleIntegration &&
                        integration !is EnvelopeFileObserverIntegration
                }
                // Public SDK queue integrations avoid depending on Android's package-private
                // integration name (which can change under R8).
                options.addIntegration(SendCachedEnvelopeFireAndForgetIntegration(
                    SendFireAndForgetEnvelopeSender { options.cacheDirPath },
                ))
                options.addIntegration(SendCachedEnvelopeFireAndForgetIntegration(
                    SendFireAndForgetOutboxSender { options.outboxPath },
                ))
                options.setBeforeSend { event, _ ->
                    // Android retains historical ANRs outside app storage. Do not resurrect
                    // records the user cleared when the SDK reads that history again.
                    if (event.timestamp.time <= clearedBefore) return@setBeforeSend null
                    event.user = null
                    event.request = null
                    event.message = null
                    event.breadcrumbs = null
                    event.extras = null
                    event.exceptions?.forEach { exception ->
                        exception.value = null
                        exception.stacktrace?.frames?.forEach { it.vars = null }
                    }
                    event.threads?.forEach { thread ->
                        thread.stacktrace?.frames?.forEach { it.vars = null }
                    }
                    event
                }
            }
            captureFailed = !Sentry.isEnabled()
        } catch (failure: Exception) {
            captureFailed = true
            Timber.e(failure, "Local crash capture initialization failed")
        }
    }

    suspend fun summary(): DiagnosticSummary = withContext(Dispatchers.IO) { store.summary() }

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        operations.withLock {
            Sentry.flush(2_000)
            tree.flush()
            val metadata = """
                VoxHub local diagnostics
                version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
                build=${BuildConfig.BUILD_TYPE}
                android=${Build.VERSION.RELEASE} api=${Build.VERSION.SDK_INT}
                abi=${Build.SUPPORTED_ABIS.joinToString()}
                exportedAtEpochMillis=${System.currentTimeMillis()}
                Reports are Sentry envelopes, stored locally. No data is uploaded.
            """.trimIndent()
            // Never hold the writer lock while a document provider performs arbitrary I/O.
            val snapshot = File.createTempFile("diagnostics-", ".zip", application.cacheDir)
            try {
                snapshot.outputStream().use { store.export(it, metadata) }
                checkNotNull(application.contentResolver.openOutputStream(uri, "wt")).use { output ->
                    snapshot.inputStream().use { it.copyTo(output) }
                }
            } finally {
                snapshot.delete()
            }
        }
    }

    // KTX edit returns Unit; deletion requires checking the durable commit result.
    @android.annotation.SuppressLint("UseKtx")
    suspend fun clear() = withContext(Dispatchers.IO) {
        operations.withLock {
            // Once shutdown starts, finish draining, deletion and restart even if the UI leaves.
            withContext(NonCancellable) {
                // Both workers need the store lock; never hold it while waiting for them.
                Sentry.close()
                try {
                    val cutoff = System.currentTimeMillis()
                    check(preferences.edit().putLong("cleared_before", cutoff).commit())
                    clearedBefore = cutoff
                    tree.close()
                    synchronized(store.lock) {
                        try {
                            store.clear()
                        } finally {
                            tree.open()
                        }
                    }
                } finally {
                    startCapture()
                }
            }
        }
    }
}
