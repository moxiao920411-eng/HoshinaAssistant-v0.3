package com.hoshina.assistant.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

object BackendUrlResolver {
    private const val BACKEND_PORT = 8000
    private const val HEALTH_PATH = "health"
    private const val PROBE_TIMEOUT_MS = 450L
    private const val SCAN_BATCH_SIZE = 32

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(PROBE_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS)
        .build()

    suspend fun resolve(currentBaseUrl: String): String? = withContext(Dispatchers.IO) {
        // Public URLs are explicit configuration. Never replace one with a LAN address.
        if (!isLocalBackend(currentBaseUrl)) {
            return@withContext currentBaseUrl.takeIf { canReachBackend(it, remote = true) }
        }
        val directCandidates = buildDirectCandidates(currentBaseUrl)
        directCandidates.firstOrNull { canReachBackend(it) }?.let { return@withContext it }

        discoverSubnetCandidates()
            .chunked(SCAN_BATCH_SIZE)
            .forEach { batch ->
                val reachable = coroutineScope {
                    batch.map { candidate ->
                        async { candidate.takeIf { canReachBackend(it) } }
                    }.awaitAll().firstOrNull { it != null }
                }
                if (reachable != null) {
                    return@withContext reachable
                }
            }

        null
    }

    private fun buildDirectCandidates(currentBaseUrl: String): List<String> {
        return buildList {
            addNormalized(currentBaseUrl)
            add("http://192.168.43.1:$BACKEND_PORT/")
            add("http://192.168.137.1:$BACKEND_PORT/")
            add("http://172.20.10.1:$BACKEND_PORT/")
            add("http://10.0.0.1:$BACKEND_PORT/")
            add("http://10.128.237.121:$BACKEND_PORT/")
        }.distinct()
    }

    private fun discoverSubnetCandidates(): List<String> {
        val prefixes = linkedSetOf<String>()

        runCatching {
            NetworkInterface.getNetworkInterfaces()
                .toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { networkInterface -> networkInterface.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .map { address -> address.hostAddress.orEmpty() }
                .filterNot { it.startsWith("127.") || it.startsWith("169.254.") }
                .forEach { ip ->
                    val parts = ip.split(".")
                    if (parts.size == 4) {
                        prefixes += parts.take(3).joinToString(".")
                    }
                }
        }

        prefixes += listOf(
            "192.168.43",
            "192.168.137",
            "172.20.10",
            "10.128.237",
        )

        return prefixes
            .flatMap { prefix ->
                (1..254).map { host -> "http://$prefix.$host:$BACKEND_PORT/" }
            }
            .distinct()
    }

    fun isLocalBackend(baseUrl: String): Boolean {
        val host = runCatching { RetrofitProvider.normalizeBaseUrl(baseUrl).toHttpUrl().host }
            .getOrNull() ?: return false
        if (host == "localhost" || host == "::1") return true
        val parts = host.split(".").map { it.toIntOrNull() ?: return false }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        return parts[0] == 10 || parts[0] == 127 ||
            (parts[0] == 192 && parts[1] == 168) ||
            (parts[0] == 172 && parts[1] in 16..31)
    }

    private fun canReachBackend(baseUrl: String, remote: Boolean = false): Boolean {
        val normalized = runCatching { RetrofitProvider.normalizeBaseUrl(baseUrl) }.getOrNull()
            ?: return false
        val request = Request.Builder()
            .url("$normalized$HEALTH_PATH")
            .get()
            .build()

        return runCatching {
            val client = if (remote) probeClient.newBuilder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .callTimeout(10, TimeUnit.SECONDS).build() else probeClient
            client.newCall(request).execute().use { response ->
                response.isSuccessful &&
                    JSONObject(response.body?.string().orEmpty()).optString("service") == "hoshina"
            }
        }.getOrDefault(false)
    }

    private fun MutableList<String>.addNormalized(url: String) {
        runCatching { RetrofitProvider.normalizeBaseUrl(url) }
            .getOrNull()
            ?.let(::add)
    }
}
