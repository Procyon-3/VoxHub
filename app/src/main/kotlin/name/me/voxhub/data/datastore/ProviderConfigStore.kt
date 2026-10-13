package name.me.voxhub.data.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import name.me.voxhub.domain.model.ProviderConfigId
import name.me.voxhub.domain.model.ProviderConfigRecord

@Serializable
internal data class StoredProviderConfigs(
    val configs: List<StoredProviderConfig> = emptyList(),
    val activeId: ProviderConfigId? = null,
)

@Serializable
internal data class StoredProviderConfig(
    val record: ProviderConfigRecord,
    val endpoint: String,
    val encryptedApiKey: String,
    val settings: ProviderSettings,
)

@Serializable
internal sealed interface ProviderSettings {
    @Serializable
    data object Preset : ProviderSettings

    @Serializable
    data class Design(val prompt: String) : ProviderSettings

    @Serializable
    data class Clone(
        val audioVersion: String,
        val mimeType: String,
        val audioName: String,
        val audioSize: Int,
        val styleInstruction: String,
    ) : ProviderSettings
}

internal object ProviderConfigSerializer : Serializer<StoredProviderConfigs> {
    private val json = Json { encodeDefaults = true }
    override val defaultValue = StoredProviderConfigs()

    override suspend fun readFrom(input: InputStream): StoredProviderConfigs = try {
        json.decodeFromString(input.readBytes().decodeToString())
    } catch (error: SerializationException) {
        throw CorruptionException("Invalid provider configuration", error)
    }

    override suspend fun writeTo(t: StoredProviderConfigs, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}
