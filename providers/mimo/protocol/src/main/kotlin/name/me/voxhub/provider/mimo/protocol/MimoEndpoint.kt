package name.me.voxhub.provider.mimo.protocol

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

const val MIMO_API_ENDPOINT = "https://api.xiaomimimo.com/v1/chat/completions"

/** Full request URL, shared by the three MiMo APIs. */
fun normalizeMimoEndpoint(value: String): String? = value.trim().toHttpUrlOrNull()
    ?.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.fragment == null }
    ?.toString()
