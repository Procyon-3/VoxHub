package name.me.voxhub.platform.tts

import android.speech.tts.SynthesisCallback
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.runBlocking
import name.me.voxhub.core.tts.*
import org.junit.Assert.*
import org.junit.Test

class AndroidCallbackCompletionTest {
    private val format = PcmFormat(24000, 1, PcmEncoding.S16_LE)

    @Test fun incompletePcmCannotBeReportedAsSuccessfulDelivery() = runBlocking {
        val callback = Callback()
        val sink = AndroidCallbackSink(callback)
        sink.start(format)
        sink.write(byteArrayOf(0), 0, 1)
        try { sink.complete(); fail("Incomplete frame must fail") }
        catch (error: TtsException) {
            assertEquals(TtsFailure.OUTPUT, error.error.failure)
            assertEquals(TtsStage.OUTPUT, error.error.diagnostic?.stage)
        }
        assertFalse(callback.finished)
        assertTrue(callback.failed)
    }

    @Test fun rejectedCompletionIsCancellation() = runBlocking {
        val sink = AndroidCallbackSink(Callback(TextToSpeech.ERROR))
        sink.start(format)
        try { sink.complete(); fail("Rejected callback must not count as success") }
        catch (_: CallbackTerminatedException) { }
    }

    @Test fun acceptedCompletionSucceeds() = runBlocking {
        val callback = Callback()
        val sink = AndroidCallbackSink(callback)
        sink.start(format)
        sink.write(byteArrayOf(0, 0), 0, 2)
        sink.complete()
        assertTrue(callback.finished)
        assertFalse(callback.failed)
    }

    @Test fun splitFramesPreserveAudioWithinCallbackBufferLimit() = runBlocking {
        for (limit in listOf(2, 5, 8192)) {
            val callback = Callback(bufferSize = limit)
            val sink = AndroidCallbackSink(callback)
            sink.start(format)
            sink.write(byteArrayOf(99, 0, 1, 2, 99), 1, 3)
            sink.write(byteArrayOf(), 0, 0)
            sink.write(byteArrayOf(3), 0, 1)
            sink.write(byteArrayOf(4, 5, 6, 7, 8), 0, 5)
            sink.write(byteArrayOf(9, 10, 11, 12, 13), 0, 5)
            sink.complete()
            assertEquals((0..13).map { it.toByte() }, callback.audio)
            assertTrue(callback.finished)
            assertFalse(callback.failed)
        }
    }

    private class Callback(
        private val result: Int = TextToSpeech.SUCCESS,
        private val bufferSize: Int = 8192,
    ) : SynthesisCallback {
        var finished = false
        var failed = false
        val audio = mutableListOf<Byte>()
        override fun start(sampleRateInHz: Int, audioFormat: Int, channelCount: Int) = TextToSpeech.SUCCESS
        override fun audioAvailable(buffer: ByteArray, offset: Int, length: Int): Int {
            assertTrue(length in 1..bufferSize)
            assertEquals(0, length % 2)
            audio.addAll(buffer.copyOfRange(offset, offset + length).toList())
            return TextToSpeech.SUCCESS
        }
        override fun done(): Int { finished = result == TextToSpeech.SUCCESS; return result }
        override fun error() { failed = true }
        override fun error(errorCode: Int) { failed = true }
        override fun getMaxBufferSize() = bufferSize
        override fun hasStarted() = true
        override fun hasFinished() = finished
    }
}
