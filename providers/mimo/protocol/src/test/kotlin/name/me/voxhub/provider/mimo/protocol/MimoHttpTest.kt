package name.me.voxhub.provider.mimo.protocol

import name.me.voxhub.core.tts.TtsException
import name.me.voxhub.core.tts.TtsFailure
import name.me.voxhub.core.tts.TtsStage
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class MimoHttpTest {
    @Test fun credentialErrorsUseRequestValidationDiagnostics() {
        for ((key, reason) in listOf(" " to "MISSING_CREDENTIAL", "test\nkey" to "INVALID_CREDENTIAL")) {
            try {
                Request.Builder().mimoApiKey(key)
                fail("Invalid credentials must fail before a request is sent")
            } catch (error: TtsException) {
                assertEquals(TtsFailure.SERVICE, error.error.failure)
                assertEquals(TtsStage.REQUEST_VALIDATION, error.error.diagnostic?.stage)
                assertEquals(reason, error.error.diagnostic?.reasonCode)
                assertNull(error.cause)
            }
        }
    }

    @Test fun headerCharactersFollowOkHttpRules() {
        val key = "test\tkey"
        val request = Request.Builder().url("https://example.com").mimoApiKey(key).build()
        assertEquals(key, request.header("api-key"))
    }
}
