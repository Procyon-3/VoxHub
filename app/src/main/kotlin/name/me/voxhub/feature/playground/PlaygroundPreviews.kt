package name.me.voxhub.feature.playground

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import name.me.voxhub.core.tts.TtsFailure
import name.me.voxhub.domain.model.ProviderConfigId
import name.me.voxhub.domain.model.ProviderConfigRecord
import name.me.voxhub.domain.model.ProviderTestState
import name.me.voxhub.provider.mimo.tts.MIMO_DEFAULT_VOICE_ID
import name.me.voxhub.provider.mimo.tts.MIMO_VOICES
import name.me.voxhub.provider.mimo.voiceclone.VOICECLONE_DEFAULT_VOICE_ID
import name.me.voxhub.provider.mimo.voiceclone.VOICECLONE_VOICES
import name.me.voxhub.provider.mimo.voicedesign.VOICEDESIGN_DEFAULT_VOICE_ID
import name.me.voxhub.provider.mimo.voicedesign.VOICEDESIGN_VOICES
import name.me.voxhub.ui.theme.VoxHubTheme

internal class PlaygroundPreviewStates : PreviewParameterProvider<PlaygroundUiState> {
    private val config = ProviderConfigRecord(
        ProviderConfigId("preview"), "mimo", "个人主力", MIMO_DEFAULT_VOICE_ID, ProviderTestState.NotTested,
    )
    private val idle = PlaygroundUiState(
        configs = listOf(config), activeId = config.id, selectedId = config.id,
        selectedVoiceId = config.defaultVoiceId, text = "这是一个语音合成测试",
    )
    private fun specialized(providerId: String, name: String, voiceId: String) = idle.copy(
        configs = listOf(config.copy(providerId = providerId, displayName = name, defaultVoiceId = voiceId)),
        selectedVoiceId = voiceId,
    )
    override val values = sequenceOf(
        idle,
        idle.copy(phase = PlaygroundPhase.CONNECTING),
        idle.copy(phase = PlaygroundPhase.PLAYING),
        idle.copy(lastFailure = TtsFailure.NETWORK),
        PlaygroundUiState(),
        idle.copy(text = ""),
        specialized("mimo-voicedesign", "声音设计", VOICEDESIGN_DEFAULT_VOICE_ID),
        specialized("mimo-voiceclone", "声音克隆", VOICECLONE_DEFAULT_VOICE_ID),
        idle.copy(configs = listOf(config.copy(displayName = "用于长文本试听的个人服务配置名称"))),
    )
}

@Preview(name = "Light", widthDp = 360, heightDp = 720)
@Preview(name = "Dark", widthDp = 360, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large text", widthDp = 320, heightDp = 640, fontScale = 2f)
@Preview(name = "Tablet", widthDp = 1000, heightDp = 800)
@Preview(name = "Landscape", widthDp = 720, heightDp = 360)
@Composable
internal fun PlaygroundPreview(@PreviewParameter(PlaygroundPreviewStates::class) state: PlaygroundUiState) {
    VoxHubTheme {
        Surface {
            PlaygroundScreen(
                state = state,
                voices = when (state.configs.firstOrNull()?.providerId) {
                    "mimo-voicedesign" -> VOICEDESIGN_VOICES
                    "mimo-voiceclone" -> VOICECLONE_VOICES
                    else -> MIMO_VOICES
                },
                providerNames = mapOf(
                    "mimo" to "MiMo TTS v2.5",
                    "mimo-voicedesign" to "MiMo TTS v2.5 VoiceDesign",
                    "mimo-voiceclone" to "MiMo TTS v2.5 VoiceClone",
                ),
                sampleText = "这是一个语音合成测试",
                contentPadding = PaddingValues(),
                onManageProviders = {}, onProviderChange = {}, onVoiceChange = {},
                onTextChange = {}, onClear = {}, onRestore = {}, onSpeak = {}, onStop = {},
            )
        }
    }
}
