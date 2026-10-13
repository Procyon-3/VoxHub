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

    suspend fun openPcmStream(request: TtsRequest): PcmStream
}

interface PcmStream : AutoCloseable {
    val format: PcmFormat

    suspend fun read(target: ByteArray): Int

    override fun close()
}
