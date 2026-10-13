package name.me.voxhub.platform.tts

import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import name.me.voxhub.VoxHubApplication
import name.me.voxhub.core.tts.AudioSink
import name.me.voxhub.core.tts.PcmFormat
import name.me.voxhub.core.tts.TtsDiagnostic
import name.me.voxhub.core.tts.TtsError
import name.me.voxhub.core.tts.TtsFailure
import name.me.voxhub.core.tts.TtsStage
import name.me.voxhub.domain.model.ProviderConfigRecord
import name.me.voxhub.domain.model.UsageAttribution
import name.me.voxhub.domain.model.UsageSource

class VoxHubTextToSpeechService : TextToSpeechService() {
    private data class Active(val job: Job)

    private val container by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        (application as VoxHubApplication).container
    }
    private val active = AtomicReference<Active?>()
    private val loadedLanguage = AtomicReference(AndroidTtsLocaleCodes("zho", "CHN", ""))

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        val record = activeRecord()
        val registration = record?.let { container.providerCatalog[it.providerId] }
        val locales = registration?.voices?.mapTo(mutableSetOf()) { it.locale } ?: container.providerCatalog.locales
        return AndroidLocaleMapper.availability(
            lang,
            country,
            variant,
            locales,
            configured = record != null && registration != null && (container.synthesisResolver.hasCompleteConfig(record.id)),
        )
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        val availability = onIsLanguageAvailable(lang, country, variant)
        if (availability >= TextToSpeech.LANG_AVAILABLE) {
            AndroidLocaleMapper.fromTtsCodes(lang, country, variant)
                ?.let(AndroidLocaleMapper::toTtsCodes)
                ?.let(loadedLanguage::set)
        }
        return availability
    }

    override fun onGetLanguage(): Array<String> = loadedLanguage.get().toArray()

    override fun onGetVoices(): List<Voice> {
        val record = activeRecord() ?: return emptyList()
        val registration = container.providerCatalog[record.providerId] ?: return emptyList()
        return registration.voices.map { voice ->
            Voice(
                voice.id,
                Locale.forLanguageTag(voice.locale.value),
                Voice.QUALITY_NORMAL,
                Voice.LATENCY_NORMAL,
                true,
                emptySet(),
            )
        }
    }

    override fun onIsValidVoiceName(voiceName: String?): Int =
        if (onGetVoices().any { it.name == voiceName }) TextToSpeech.SUCCESS else TextToSpeech.ERROR

    override fun onLoadVoice(voiceName: String?): Int {
        val record = activeRecord() ?: return TextToSpeech.ERROR
        val registration = container.providerCatalog[record.providerId] ?: return TextToSpeech.ERROR
        val voice = registration.voices.firstOrNull { it.id == voiceName } ?: return TextToSpeech.ERROR
        if (!container.synthesisResolver.hasCompleteConfig(record.id)) return TextToSpeech.ERROR
        AndroidLocaleMapper.toTtsCodes(voice.locale)?.let(loadedLanguage::set)
        return TextToSpeech.SUCCESS
    }

    override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String {
        if (onIsLanguageAvailable(lang, country, variant) < TextToSpeech.LANG_AVAILABLE) return ""
        val record = activeRecord() ?: return ""
        val registration = container.providerCatalog[record.providerId] ?: return ""
        if (record.providerId in setOf("mimo-voicedesign", "mimo-voiceclone")) {
            val locale = AndroidLocaleMapper.fromTtsCodes(lang, country, variant)
            return registration.voices.firstOrNull { it.locale == locale }?.id ?: registration.defaultVoiceId
        }
        return record.defaultVoiceId.takeIf { id -> registration.voices.any { it.id == id } }
            ?: registration.defaultVoiceId
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        val requestStartedAt = System.currentTimeMillis()
        val requestStartedElapsed = android.os.SystemClock.elapsedRealtime()
        val mine = Active(Job())
        active.set(mine)
        val callbackSink = AndroidCallbackSink(callback)
        Timber.i("System synthesis started")
        try {
            runBlocking(mine.job) {
                val initialized = container.awaitInitialization()
                val record = activeRecord()
                container.usageRecorder.record(
                    UsageAttribution(record?.providerId, record?.id?.value, record?.displayName.orEmpty(), UsageSource.SYSTEM_TTS),
                    request.charSequenceText.toString(), callbackSink,
                    startedAt = requestStartedAt, startedElapsed = requestStartedElapsed,
                ) { observed ->
                    if (!initialized) {
                        observed.fail(serviceError("INITIALIZATION_FAILED"))
                        return@record
                    }
                    val resolved = record?.let { container.synthesisResolver.resolve(it.id) }
                    if (resolved == null) {
                        val error = serviceError("ACTIVE_PROVIDER_UNAVAILABLE")
                        observed.fail(error)
                        Timber.e("system_tts failed failure=%s diagnostic=%s", error.failure, error.diagnostic)
                        return@record
                    }
                    val sink = FailureLoggingAudioSink(observed) { error ->
                        Timber.e("system_tts failed provider=%s failure=%s diagnostic=%s", resolved.providerId, error.failure, error.diagnostic)
                    }
                    name.me.voxhub.core.tts.TtsEngine(resolved.provider).synthesize(
                        request.toCoreRequest(
                            currentLocale = resolved.defaultLocale,
                            supportedLocales = resolved.supportedLocales,
                            currentVoiceId = resolved.defaultVoiceId,
                            knownVoiceIds = resolved.voiceIds,
                            reportUnknownVoice = { Timber.w("Unknown voice; using configured voice") },
                        ), sink,
                    )
                }
            }
        } catch (_: CancellationException) {
            // Framework stop and callback refusal are silent terminal paths.
        } catch (failure: Exception) {
            runBlocking {
                val error = (failure as? name.me.voxhub.core.tts.TtsException)?.error
                    ?: serviceError("SERVICE_BRIDGE_FAILURE")
                callbackSink.fail(error)
                Timber.e(failure, "system_tts bridge failed failure=%s diagnostic=%s", error.failure, error.diagnostic)
            }
        } finally {
            active.compareAndSet(mine, null)
        }
    }

    override fun onStop() {
        active.get()?.job?.cancel()
    }

    private fun activeRecord(): ProviderConfigRecord? = container.configRepository.currentState.activeConfig

    private fun serviceError(reasonCode: String) = TtsError(
        TtsFailure.SERVICE,
        TtsDiagnostic(stage = TtsStage.REQUEST_VALIDATION, reasonCode = reasonCode),
    )

}

private class FailureLoggingAudioSink(
    private val delegate: AudioSink,
    private val onFailure: suspend (TtsError) -> Unit,
) : AudioSink {
    override suspend fun start(format: PcmFormat) = delegate.start(format)

    override suspend fun write(bytes: ByteArray, offset: Int, length: Int) = delegate.write(bytes, offset, length)

    override suspend fun complete() = delegate.complete()

    override suspend fun fail(error: TtsError) {
        delegate.fail(error)
        onFailure(error)
    }
}
