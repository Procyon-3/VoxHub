package name.me.voxhub.core.tts


interface AudioSink {
    suspend fun start(format: PcmFormat)
    suspend fun write(bytes: ByteArray, offset: Int, length: Int)
    suspend fun complete()
    suspend fun fail(error: TtsError)
}

interface TtsProvider {
    val descriptor: TtsProviderDescriptor
    val voices: List<TtsVoice>

    /** Called by [TtsEngine] with non-empty text and a supported locale and voice. */
    suspend fun openPcmStream(request: TtsRequest): PcmStream
}

interface PcmStream : AutoCloseable {
    val format: PcmFormat

    /**
     * Reads 1..target.size bytes into a non-empty [target], or returns -1 at EOF.
     * A read may split a PCM frame across calls.
     */
    suspend fun read(target: ByteArray): Int

    override fun close()
}
