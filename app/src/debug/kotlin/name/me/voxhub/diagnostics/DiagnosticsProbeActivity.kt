package name.me.voxhub.diagnostics

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import name.me.voxhub.VoxHubApplication
import timber.log.Timber
import java.io.File

/** Shell-only fault injection. No test controls are shipped in release. */
class DiagnosticsProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { setText(name.me.voxhub.R.string.diagnostics_title) })
        val diagnostics = (application as VoxHubApplication).diagnostics
        when (intent.getStringExtra("probe")) {
            "usage-fixture" -> CoroutineScope(Dispatchers.IO).launch {
                check(packageName.endsWith(".diagnosticstest"))
                val repository = (application as VoxHubApplication).container.usageRepository
                repository.clear()
                repeat(65) { index ->
                    val source = name.me.voxhub.domain.model.UsageSource.entries[index % 3]
                    val attribution = name.me.voxhub.domain.model.UsageAttribution("test-provider", "config-${index % 2}", "测试配置 ${index % 2}", source)
                    repository.begin("fixture-$index", System.currentTimeMillis() - index * 1000, attribution, 10)
                    val outcome = name.me.voxhub.domain.model.UsageOutcome.entries[index % 3]
                    repository.finish("fixture-$index", outcome, if (index % 3 == 0) 100 else null, 500,
                        if (index % 3 == 1) "NETWORK" else null, if (index % 3 == 1) "STREAM_READ" else null)
                }
                runOnUiThread { finish() }
            }
            "usage" -> CoroutineScope(Dispatchers.IO).launch {
                val result = try { name.me.voxhub.usage.runUsageProbe(this@DiagnosticsProbeActivity) }
                    catch (failure: Throwable) { "FAIL: ${failure.stackTraceToString()}" }
                File(filesDir, "usage-probe-result.txt").writeText(result)
                runOnUiThread { finish() }
            }
            "upstream" -> CoroutineScope(Dispatchers.IO).launch {
                val keyFile = File(filesDir, "diagnostics-test-key.txt")
                try {
                    val container = (application as VoxHubApplication).container
                    check(container.awaitInitialization())
                    val result = container.mimoConfigService.testDraft(
                        name.me.voxhub.domain.provider.MimoProviderFormData(
                            displayName = "Diagnostic test", apiKey = keyFile.readText().trim(),
                            defaultVoiceId = name.me.voxhub.provider.mimo.tts.MIMO_VOICES.first().id,
                        ),
                    )
                    // ProviderTestState contains only the already-sanitized diagnostic codes.
                    File(filesDir, "diagnostics-upstream-result.txt").writeText(result.toString())
                } finally {
                    keyFile.delete()
                    runOnUiThread { finish() }
                }
            }
            "java" -> Handler(Looper.getMainLooper()).postDelayed({ error("VOXHUB_SECRET_JAVA_MESSAGE") }, 1_000)
            "anr" -> Handler(Looper.getMainLooper()).postDelayed({ Thread.sleep(60_000) }, 1_000)
            "rotate" -> {
                repeat(7_000) { Timber.w("ROTATION %d %s", it, "x".repeat(1_024)) }
                finish()
            }
            "export" -> CoroutineScope(Dispatchers.Main).launch {
                diagnostics.export(Uri.fromFile(File(filesDir, "diagnostics-test.zip")))
                finish()
            }
            "clear" -> CoroutineScope(Dispatchers.Main).launch { diagnostics.clear(); finish() }
            else -> {
                Timber.v("DIAGNOSTIC_VERBOSE")
                Timber.d("DIAGNOSTIC_DEBUG")
                Timber.i("DIAGNOSTIC_INFO")
                Timber.w("DIAGNOSTIC_WARNING")
                Timber.e(IllegalArgumentException("VOXHUB_SECRET_EXCEPTION"), "DIAGNOSTIC_ERROR")
                File(filesDir, "diagnostics-health.txt").writeText("available=${diagnostics.isAvailable}")
                finish()
            }
        }
    }
}
