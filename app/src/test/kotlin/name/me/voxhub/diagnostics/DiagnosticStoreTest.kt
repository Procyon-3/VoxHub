package name.me.voxhub.diagnostics

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun store() = DiagnosticStore(temporary.newFolder()).also { it.prepare() }

    @Test fun startupRemovesIncompleteReportsWithoutDeletingCommittedOnes() {
        val store = store()
        File(store.reports, "partial.tmp").writeText("unfinished")
        File(store.reports, "complete.envelope").writeText("committed")
        store.prepare()
        assertEquals(listOf("complete.envelope"), store.reports.list()!!.toList())
    }

    @Test fun keepsOnlyTenCompletedReports() {
        val store = store()
        repeat(15) { index ->
            store.saveReport("event-$index") { it.write("report-$index".toByteArray()) }
            File(store.reports, "event-$index.envelope").setLastModified(1_000L + index)
        }
        assertEquals(10, store.summary().reportCount)
        assertEquals((5..14).map { "event-$it.envelope" }.toSet(), store.reports.list()!!.toSet())
    }

    @Test fun failedWriteDoesNotPublishPartialReportOrLosePreviousReport() {
        val store = store()
        store.saveReport("same") { it.write("complete".toByteArray()) }
        try {
            store.saveReport("same") { it.write("partial".toByteArray()); throw IOException("disk full") }
            fail("Expected failure")
        } catch (_: IOException) { }
        assertEquals("complete", File(store.reports, "same.envelope").readText())
        assertEquals(listOf("same.envelope"), store.reports.list()!!.toList())
    }

    @Test fun concurrentExportContainsOnlyCompletePublicFiles() {
        val store = store()
        File(store.sdk, "private-working-file").writeText("not exportable")
        File(store.logs, "application.log").writeText("safe log")
        val executor = Executors.newFixedThreadPool(2)
        try {
            val writing = executor.submit { repeat(40) { i -> store.saveReport("event-$i") { it.write(ByteArray(8192) { 65 }) } } }
            val exporting = executor.submit {
                repeat(20) {
                    val bytes = ByteArrayOutputStream()
                    store.export(bytes, "version=1")
                    ZipInputStream(bytes.toByteArray().inputStream()).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            assertFalse(entry.name.startsWith("sdk/"))
                            assertFalse(entry.name.endsWith(".tmp"))
                            val data = zip.readBytes()
                            if (entry.name.startsWith("reports/")) assertEquals(8192, data.size)
                        }
                    }
                }
            }
            writing.get(); exporting.get()
        } finally { executor.shutdownNow() }
    }

    @Test fun clearDeletesReportsLogsAndSdkWorkingData() {
        val store = store()
        store.saveReport("one") { it.write(1) }
        File(store.logs, "application.log").writeText("log")
        File(store.sdk, "queued-event").writeText("sdk")
        store.clear()
        assertEquals(DiagnosticSummary(), store.summary())
        store.saveReport("after-clear") { it.write(2) }
        assertEquals(1, store.summary().reportCount)
    }

    @Test fun throwableMessagesAndNestedSecretsAreExcluded() {
        val exception = IllegalArgumentException("secret prompt", IOException("secret key"))
        exception.addSuppressed(IllegalStateException("secret body"))
        val text = safeStackTrace(exception)
        assertFalse(text.contains("secret"))
        assertTrue(text.contains("java.lang.IllegalArgumentException"))
        assertTrue(text.contains("java.io.IOException"))
        assertTrue(text.contains("DiagnosticStoreTest"))
    }
}
