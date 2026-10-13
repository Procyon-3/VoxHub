package name.me.voxhub.diagnostics

import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The same lock coordinates log rotation, completed reports, export and deletion. */
internal class DiagnosticStore(val root: File) {
    val lock = Any()
    val logs = File(root, "logs")
    val reports = File(root, "reports")
    val sdk = File(root, "sdk")

    fun prepare() {
        check(logs.isDirectory || logs.mkdirs())
        check(reports.isDirectory || reports.mkdirs())
        check(sdk.isDirectory || sdk.mkdirs())
        // A process can die before the atomic rename; incomplete reports are never reusable.
        reports.listFiles().orEmpty().filter { it.extension == "tmp" }.forEach { it.delete() }
    }

    fun saveReport(id: String, write: (OutputStream) -> Unit) = synchronized(lock) {
        prepare()
        val destination = File(reports, "$id.envelope")
        val temporary = File(reports, "$id.tmp")
        try {
            temporary.outputStream().use { output ->
                write(output)
                output.fd.sync()
            }
            check(temporary.renameTo(destination)) { "Cannot commit diagnostic report" }
            reports.listFiles().orEmpty().filter { it.extension == "envelope" }
                .sortedByDescending(File::lastModified).drop(10).forEach {
                    check(it.delete()) { "Cannot remove expired report" }
                }
        } finally {
            temporary.delete()
        }
    }

    fun summary(): DiagnosticSummary = synchronized(lock) {
        DiagnosticSummary(
            root.walkTopDown().filter(File::isFile).sumOf(File::length),
            reports.listFiles().orEmpty().count { it.extension == "envelope" },
        )
    }

    /** SDK working files are never exported: only atomically completed reports and app logs. */
    fun export(output: OutputStream, metadata: String) = synchronized(lock) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("application.txt"))
            zip.write(metadata.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            listOf(logs, reports).flatMap { it.listFiles().orEmpty().toList() }
                .filter { it.isFile && it.extension != "tmp" }.sortedBy(File::getName)
                .forEach { file ->
                    zip.putNextEntry(ZipEntry(file.relativeTo(root).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
    }

    /** Caller stops SDK and closes the file appender before entering. */
    fun clear() = synchronized(lock) {
        check(!root.exists() || root.deleteRecursively()) { "Cannot clear diagnostics" }
        prepare()
    }
}

data class DiagnosticSummary(val bytes: Long = 0, val reportCount: Int = 0)
