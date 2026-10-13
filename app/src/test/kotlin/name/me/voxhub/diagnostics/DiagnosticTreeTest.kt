package name.me.voxhub.diagnostics

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticTreeTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun loggingReturnsWhileExportHoldsTheStoreLock() = runBlocking {
        val store = DiagnosticStore(temporary.newFolder())
        val tree = DiagnosticTree(store, debug = false)
        val executor = Executors.newFixedThreadPool(2)
        val exporting = CountDownLatch(1)
        val finishExport = CountDownLatch(1)
        tree.open()
        try {
            assertFalse(tree.writeFailed)
            tree.w("Before export")
            tree.flush()
            val export = executor.submit {
                val output = object : ByteArrayOutputStream() {
                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        exporting.countDown()
                        check(finishExport.await(5, TimeUnit.SECONDS))
                        super.write(bytes, offset, length)
                    }
                }
                store.export(output, "version=1")
            }
            assertTrue(exporting.await(5, TimeUnit.SECONDS))
            executor.submit { tree.w("During export") }.get(1, TimeUnit.SECONDS)
            finishExport.countDown()
            export.get(5, TimeUnit.SECONDS)
            tree.flush()
            val log = File(store.logs, "application.log").readText()
            assertTrue(log.contains("Before export"))
            assertTrue(log.contains("During export"))
        } finally {
            finishExport.countDown()
            tree.close()
            executor.shutdownNow()
        }
    }

    @Test fun closeDrainsLogsAndClearDoesNotRestoreOldEvents() = runBlocking {
        val store = DiagnosticStore(temporary.newFolder())
        val tree = DiagnosticTree(store, debug = false)
        tree.open()
        try {
            assertFalse(tree.writeFailed)
            repeat(80) { tree.w("Before clear %d", it) }
            tree.close()
            val log = File(store.logs, "application.log").readText()
            assertTrue(log.contains("Before clear 79"))
            assertTrue(log.contains("[${Thread.currentThread().name}]"))
            store.clear()
            tree.open()
            assertFalse(tree.writeFailed)
            tree.w("After clear")
            tree.flush()
            val cleared = File(store.logs, "application.log").readText()
            assertTrue(cleared.contains("After clear"))
            assertFalse(cleared.contains("Before clear"))
        } finally {
            tree.close()
        }
    }

    @Test fun saturatedQueueDoesNotBlockTheCallerAndReportsFailure() = runBlocking {
        val store = DiagnosticStore(temporary.newFolder())
        val tree = DiagnosticTree(store, debug = false)
        val executor = Executors.newSingleThreadExecutor()
        tree.open()
        try {
            assertFalse(tree.writeFailed)
            synchronized(store.lock) {
                executor.submit { repeat(1_024) { tree.w("Queued %d", it) } }
                    .get(1, TimeUnit.SECONDS)
                assertTrue(tree.writeFailed)
            }
        } finally {
            tree.close()
            executor.shutdownNow()
        }
    }
}
