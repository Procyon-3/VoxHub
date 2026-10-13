package name.me.voxhub.data.audio

import java.io.RandomAccessFile
import java.util.UUID
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VoiceCloneAudioStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun truncatedAndOversizedFilesAreRejectedBeforeDecryption() {
        val store = VoiceCloneAudioStore(temporary.root)
        for (size in listOf(0L, 28L, 7_500_029L)) {
            val version = UUID.randomUUID().toString()
            val file = temporary.newFile(version)
            RandomAccessFile(file, "rw").use { it.setLength(size) }
            assertThrows(IllegalArgumentException::class.java) {
                store.read(version, "audio/wav")
            }
        }
    }
}
