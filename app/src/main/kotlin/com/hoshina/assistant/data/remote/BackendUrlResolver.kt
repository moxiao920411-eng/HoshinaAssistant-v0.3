package com.hoshina.assistant.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

object BackendUrlResolver {
    private const val BACKEND_PORT = 8000
    private const val HEALTH_PATH = "agents"
    private const val PROBE_TIMEOUT_MS = 450L
    private const val SCAN_BATCH_SIZE = 32

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(PROBE_TIMEOUT_MS * 2, TimeUnit.MILLISECONDS)
        .build()

    suspend fun resolve(currentBaseUrl: String): String? = withContext(Dispatchers.IO) {
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

    private fun canReachBackend(baseUrl: String): Boolean {
        val normalized = runCatching { RetrofitProvider.normalizeBaseUrl(baseUrl) }.getOrNull()
            ?: return false
        val request = Request.Builder()
            .url("$normalized$HEALTH_PATH")
            .get()
            .build()

        return runCatching {
            probeClient.newCall(request).execute().use { response -> response.isSuccessful }
        }.getOrDefault(false)
    }

    private fun MutableList<String>.addNormalized(url: String) {
        runCatching { RetrofitProvider.normalizeBaseUrl(url) }
            .getOrNull()
            ?.let(::add)
    }
}
