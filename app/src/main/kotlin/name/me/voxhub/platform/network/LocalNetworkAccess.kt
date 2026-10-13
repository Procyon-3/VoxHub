package name.me.voxhub.platform.network

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import name.me.voxhub.provider.mimo.protocol.normalizeMimoEndpoint

internal suspend fun endpointUsesLocalNetwork(endpoint: String): Boolean = withContext(Dispatchers.IO) {
    val normalized = normalizeMimoEndpoint(endpoint) ?: return@withContext false
    val host = URI(normalized).host ?: return@withContext false
    if (host.endsWith(".local", ignoreCase = true)) return@withContext true
    try {
        InetAddress.getAllByName(host).any { address ->
            address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress ||
                (address is Inet6Address && (address.address[0].toInt() and 0xfe) == 0xfc)
        }
    } catch (_: UnknownHostException) {
        false // Let the synthesis request report the DNS failure through the normal error flow.
    }
}
