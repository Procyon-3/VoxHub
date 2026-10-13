package name.me.voxhub.feature.providers

import androidx.annotation.StringRes
import name.me.voxhub.R

internal data class VoiceDesignPromptTemplate(
    @param:StringRes @get:StringRes val summaryRes: Int,
    @param:StringRes @get:StringRes val promptRes: Int,
)

// Add each template's one-sentence summary and complete prompt to strings.xml,
// then reference both resources here.
internal val voiceDesignPromptTemplates: List<VoiceDesignPromptTemplate> = listOf(
    VoiceDesignPromptTemplate(R.string.voice_template_storyteller, R.string.voice_template_storyteller_prompt),
    VoiceDesignPromptTemplate(R.string.voice_template_audiobook, R.string.voice_template_audiobook_prompt),
    VoiceDesignPromptTemplate(R.string.voice_template_female_audiobook, R.string.voice_template_female_audiobook_prompt),
    VoiceDesignPromptTemplate(R.string.voice_template_late_night_radio, R.string.voice_template_late_night_radio_prompt),
)
