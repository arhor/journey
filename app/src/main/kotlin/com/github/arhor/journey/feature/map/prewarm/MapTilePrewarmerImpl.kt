package com.github.arhor.journey.feature.map.prewarm

import android.content.Context
import android.util.Log
import com.github.arhor.journey.di.AppCoroutineScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import org.maplibre.android.MapLibre
import org.maplibre.android.offline.OfflineManager

@Singleton
class MapTilePrewarmerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val styleResolver: MapTileStyleResolver,
    private val coverageCalculator: MapTileCoverageCalculator,
    private val resourceFetcher: MapTileResourceFetcher,
    private val cacheWriter: MapTileCacheWriter,
) : MapTilePrewarmer {
    private val activeJobs = ConcurrentHashMap<String, Job>()

    override fun prewarm(request: MapTilePrewarmRequest): Job {
        val previousJob = activeJobs.remove(request.requestKey)
        previousJob?.cancel()

        val job = appScope.launch {
            runCatching {
                prewarmInternal(request)
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                Log.w(TAG, "Map tile prewarm failed for ${request.requestKey}", throwable)
            }
        }

        activeJobs[request.requestKey] = job
        job.invokeOnCompletion { activeJobs.remove(request.requestKey, job) }
        return job
    }

    private suspend fun prewarmInternal(
        request: MapTilePrewarmRequest,
    ) {
        val pixelRatio = context.resources.displayMetrics.density
        val resolvedResources = styleResolver.resolve(
            style = request.style,
            fetcher = resourceFetcher,
            pixelRatio = pixelRatio,
        )

        for (resource in resolvedResources.metadataResources) {
            cacheWriter.cache(resource)
        }

        val tileUrls = coverageCalculator.calculate(
            request = request,
            sources = resolvedResources.tileSources,
            pixelRatio = pixelRatio,
        )

        val urlsToFetch = buildList {
            addAll(resolvedResources.staticResourceUrls)
            addAll(tileUrls)
        }.distinct()

        if (urlsToFetch.isEmpty()) {
            return
        }

        val semaphore = Semaphore(MAX_CONCURRENT_FETCHES)
        supervisorScope {
            urlsToFetch
                .map { url ->
                    async {
                        semaphore.withPermit {
                            val resource = resourceFetcher.fetch(url)
                            if (resource != null) {
                                cacheWriter.cache(resource)
                            }
                        }
                    }
                }
                .awaitAll()
        }
    }

    private companion object {
        private const val MAX_CONCURRENT_FETCHES = 4
        private const val TAG = "MapTilePrewarmer"
    }
}

interface MapTileResourceFetcher {
    suspend fun fetch(url: String): FetchedMapResource?
}

interface MapTileCacheWriter {
    suspend fun cache(resource: FetchedMapResource)
}

data class FetchedMapResource(
    val url: String,
    val body: ByteArray,
    val modifiedEpochSeconds: Long = 0,
    val expiresEpochSeconds: Long = 0,
    val etag: String? = null,
    val mustRevalidate: Boolean = false,
)

@Singleton
class HttpMapTileResourceFetcher @Inject constructor() : MapTileResourceFetcher {
    override suspend fun fetch(url: String): FetchedMapResource? {
        if (!isRemoteHttpUrl(url)) {
            return null
        }

        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            val connection = (URL(url).openConnection() as? HttpURLConnection)
                ?: return@withContext null
            runCatching {
                connection.instanceFollowRedirects = true
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.connect()

                if (connection.responseCode !in 200..299) {
                    return@withContext null
                }

                val cacheControl = connection.getHeaderField("Cache-Control").orEmpty()
                if (cacheControl.contains("no-store", ignoreCase = true)) {
                    return@withContext null
                }

                val body = BufferedInputStream(connection.inputStream).use { input ->
                    input.readBytes()
                }

                FetchedMapResource(
                    url = connection.url.toString(),
                    body = body,
                    modifiedEpochSeconds = connection.lastModified.toEpochSeconds(),
                    expiresEpochSeconds = connection.expiration.toEpochSeconds(),
                    etag = connection.getHeaderField("ETag"),
                    mustRevalidate = cacheControl.contains("must-revalidate", ignoreCase = true) ||
                        cacheControl.contains("no-cache", ignoreCase = true) ||
                        cacheControl.contains("max-age=0", ignoreCase = true),
                )
            }.getOrNull().also {
                connection.disconnect()
            }
        }
    }

    private fun Long.toEpochSeconds(): Long =
        if (this > 0L) this / 1_000L else 0L

    private companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 5_000
        private const val READ_TIMEOUT_MILLIS = 10_000
        private const val USER_AGENT = "Journey-MapTilePrewarmer/1.0"
    }
}

@Singleton
class AndroidMapTileCacheWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) : MapTileCacheWriter {
    override suspend fun cache(resource: FetchedMapResource) {
        kotlinx.coroutines.withContext(Dispatchers.Main.immediate) {
            MapLibre.getInstance(context)
            OfflineManager.getInstance(context).putResourceWithUrl(
                resource.url,
                resource.body,
                resource.modifiedEpochSeconds,
                resource.expiresEpochSeconds,
                resource.etag,
                resource.mustRevalidate,
            )
        }
    }
}

internal fun isRemoteHttpUrl(url: String): Boolean {
    val scheme = runCatching { URL(url).protocol }.getOrNull()
    return scheme == "http" || scheme == "https"
}

internal fun encodeUrlPathSegment(value: String): String =
    java.net.URLEncoder.encode(value, Charsets.UTF_8)
        .replace("+", "%20")
