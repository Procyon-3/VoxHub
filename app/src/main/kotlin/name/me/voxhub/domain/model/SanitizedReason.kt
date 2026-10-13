package name.me.voxhub.domain.model

import kotlinx.serialization.Serializable
import name.me.voxhub.core.tts.TtsDiagnostic
import name.me.voxhub.core.tts.TtsStage

@Serializable
data class SanitizedReason(
    val providerStatus: Int? = null,
    val providerCode: String? = null,
    val stage: TtsStage? = null,
    val reasonCode: String? = null,
)

fun TtsDiagnostic.toSanitizedReason() = SanitizedReason(
    providerStatus = providerStatus,
    providerCode = providerCode,
    stage = stage,
    reasonCode = reasonCode,
)
