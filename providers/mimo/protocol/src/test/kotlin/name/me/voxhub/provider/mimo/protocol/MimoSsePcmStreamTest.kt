package name.me.voxhub.provider.mimo.protocol

import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking
import name.me.voxhub.core.tts.TtsException
import name.me.voxhub.core.tts.PcmFormat
import name.me.voxhub.core.tts.PcmEncoding
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class MimoSsePcmStreamTest {
    @Test fun extraFieldsDoNotChangeAudioDecoding() = runBlocking {
        for (protocol in MimoStreamProtocol.entries) {
            withStream(protocol, "AAA=") { stream ->
                val bytes = ByteArray(1)
                assertEquals(1, stream.read(bytes))
                assertArrayEquals(byteArrayOf(0), bytes)
                assertEquals(1, stream.read(bytes))
                assertArrayEquals(byteArrayOf(0), bytes)
                assertEquals(-1, stream.read(bytes))
            }
        }
    }

    @Test fun generatedProtocolStillRejectsUnpaddedBase64() = runBlocking {
        withStream(MimoStreamProtocol.GENERATED, "AAA") { stream ->
            try {
                stream.read(ByteArray(2))
                fail("Generated audio requires padded Base64")
            } catch (error: TtsException) {
                assertEquals("INVALID_BASE64", error.error.diagnostic?.reasonCode)
            }
        }
    }

    @Test fun presetProtocolStillAcceptsUnpaddedBase64() = runBlocking {
        withStream(MimoStreamProtocol.PRESET, "AAA") { stream ->
            assertEquals(2, stream.read(ByteArray(2)))
            assertEquals(-1, stream.read(ByteArray(2)))
        }
    }

    @Test fun incompletePcmStillFailsAtCompletion() = runBlocking {
        for (protocol in MimoStreamProtocol.entries) {
            withStream(protocol, "AA==") { stream ->
                assertEquals(1, stream.read(ByteArray(2)))
                try {
                    stream.read(ByteArray(2))
                    fail("A partial PCM frame cannot complete")
                } catch (error: TtsException) {
                    assertEquals("INVALID_PCM_ALIGNMENT", error.error.diagnostic?.reasonCode)
                }
            }
        }
    }

    private suspend fun withStream(
        protocol: MimoStreamProtocol,
        audio: String,
        block: suspend (MimoSsePcmStream) -> Unit,
    ) {
        val request = Request.Builder().url("https://example.com/tts").build()
        val client = OkHttpClient()
        val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK").body("".toResponseBody()).build()
        val source = Buffer().writeUtf8(
            "data: {\"extra\":true,\"choices\":[{\"extra\":{},\"delta\":{\"extra\":[],\"audio\":{\"data\":\"$audio\",\"extra\":null}}}]}\n\n" +
                "data: [DONE]\n\n",
        )
        val executor = Executors.newSingleThreadExecutor()
        val stream = MimoSsePcmStream(client.newCall(request), response, source,
            PcmFormat(24_000, 1, PcmEncoding.S16_LE), executor, protocol)
        try { block(stream) }
        finally { stream.close(); executor.shutdownNow() }
    }
}
