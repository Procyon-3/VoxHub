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

    @Test fun unknownSseFieldsDoNotInterruptAudio() = runBlocking {
        val events = ": heartbeat\ntrace_id: test\nping\ntimestamp: 123\n\n" +
            "event: audio\nid: 1\nretry: 1000\ntrace_id: test\n" +
            "data: {\"choices\":[{\"delta\":{\"audio\":{\"data\":\"AAA=\"}}}]}\n\n"
        for (protocol in MimoStreamProtocol.entries) {
            withEvents(protocol, events) { stream ->
                val bytes = ByteArray(8192)
                assertEquals(2, stream.read(bytes))
                assertEquals(-1, stream.read(bytes))
            }
        }
    }

    @Test fun optionalEventsAreSkippedButWrongAudioTypesFail() = runBlocking {
        val events = "data: {}\n\n" +
            "data: {\"choices\":[{\"delta\":null}]}\n\n" +
            "data: {\"choices\":[{\"delta\":{\"audio\":null}}]}\n\n" +
            "data: {\"choices\":[{\"delta\":{\"audio\":{\"data\":null}}}]}\n\n"
        for (protocol in MimoStreamProtocol.entries) {
            for (invalidData in listOf("42", "true", "{}", "[]")) {
                withEvents(
                    protocol,
                    events + "data: {\"choices\":[{\"delta\":{\"audio\":{\"data\":$invalidData}}}]}\n\n",
                ) { stream ->
                    try {
                        stream.read(ByteArray(8192))
                        fail("Wrong audio types must not be silently skipped")
                    } catch (error: TtsException) {
                        assertEquals("INVALID_SSE_STRUCTURE", error.error.diagnostic?.reasonCode)
                    }
                }
            }
        }
    }

    private suspend fun withStream(
        protocol: MimoStreamProtocol,
        audio: String,
        block: suspend (MimoSsePcmStream) -> Unit,
    ) = withEvents(
        protocol,
        "data: {\"extra\":true,\"choices\":[{\"extra\":{},\"delta\":{\"extra\":[],\"audio\":{\"data\":\"$audio\",\"extra\":null}}}]}\n\n",
        block,
    )

    private suspend fun withEvents(
        protocol: MimoStreamProtocol,
        events: String,
        block: suspend (MimoSsePcmStream) -> Unit,
    ) {
        val request = Request.Builder().url("https://example.com/tts").build()
        val client = OkHttpClient()
        val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK").body("".toResponseBody()).build()
        val source = Buffer().writeUtf8(events + "data: [DONE]\n\n")
        val executor = Executors.newSingleThreadExecutor()
        val stream = MimoSsePcmStream(client.newCall(request), response, source,
            PcmFormat(24_000, 1, PcmEncoding.S16_LE), executor, protocol)
        try { block(stream) }
        finally { stream.close(); executor.shutdownNow() }
    }
}
