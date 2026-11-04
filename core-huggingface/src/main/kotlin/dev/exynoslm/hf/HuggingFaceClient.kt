package dev.exynoslm.hf

import dev.exynoslm.model.ModelCard
import dev.exynoslm.model.ModelFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Filters map 1:1 onto query params of the public Hub endpoint GET /api/models (https://huggingface.co/docs/hub/api). */
data class SearchQuery(
    val search: String? = null,
    val pipelineTag: String? = null,     // e.g. "text-generation"
    val library: String? = null,         // e.g. "gguf", "tflite", "onnx", "transformers"
    val author: String? = null,
    val tags: List<String> = emptyList(),// e.g. "license:apache-2.0"
    val sort: String = "downloads",      // downloads | likes | lastModified | trendingScore
    val limit: Int = 30,
)

data class SearchPage(val models: List<ModelCard>, val nextUrl: String?)

sealed class HubException(msg: String) : IOException(msg) {
    class RateLimited(val retryAfterSec: Long?) : HubException("rate limited by Hugging Face")
    class NotFound(what: String) : HubException("not found: $what")
    class GatedOrUnauthorized(repo: String) : HubException("'$repo' is gated or requires a token")
    class Http(code: Int) : HubException("HTTP $code")
}

/** Curated quick filters for on-device-friendly model families. */
object Presets {
    val chat = listOf("gemma", "qwen", "phi", "llama", "smollm").map { SearchQuery(search = it, pipelineTag = "text-generation") }
    val litertCommunity = SearchQuery(author = "litert-community")
    val embedding = SearchQuery(pipelineTag = "feature-extraction")
    val asr = SearchQuery(pipelineTag = "automatic-speech-recognition")
    val imageClassification = SearchQuery(pipelineTag = "image-classification")
    val objectDetection = SearchQuery(pipelineTag = "object-detection")
    val ocr = SearchQuery(pipelineTag = "image-to-text")
    val imageGeneration = SearchQuery(pipelineTag = "text-to-image")
}

class HuggingFaceClient(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://huggingface.co",
    private val token: () -> String? = { null },
    private val maxRetries: Int = 3,
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun searchUrl(q: SearchQuery): HttpUrl = "$baseUrl/api/models".toHttpUrl().newBuilder().apply {
        q.search?.let { addQueryParameter("search", it) }
        q.pipelineTag?.let { addQueryParameter("pipeline_tag", it) }
        q.library?.let { addQueryParameter("library", it) }
        q.author?.let { addQueryParameter("author", it) }
        q.tags.forEach { addQueryParameter("filter", it) }
        addQueryParameter("sort", q.sort)
        addQueryParameter("direction", "-1")
        addQueryParameter("limit", q.limit.toString())
        addQueryParameter("full", "true")
        addQueryParameter("config", "false")
    }.build()

    suspend fun search(q: SearchQuery): SearchPage = fetchPage(searchUrl(q).toString())

    /** Pagination uses the RFC 5988 `Link: <...>; rel="next"` header returned by the Hub. */
    suspend fun fetchPage(url: String): SearchPage {
        val (body, link) = get(url)
        val models = json.decodeFromString<List<ApiModel>>(body).map { it.toCard() }
        return SearchPage(models, parseNextLink(link))
    }

    /** Model detail including per-file size and LFS sha256 (`blobs=true`). */
    suspend fun model(repoId: String, revision: String = "main"): ModelCard {
        val url = "$baseUrl/api/models/$repoId/revision/$revision".toHttpUrl().newBuilder().addQueryParameter("blobs", "true").build()
        return json.decodeFromString<ApiModel>(get(url.toString(), repoId).first).toCard()
    }

    fun resolveUrl(repoId: String, path: String, revision: String = "main") = "$baseUrl/$repoId/resolve/$revision/$path"

    private suspend fun get(url: String, what: String = url): Pair<String, String?> = withContext(Dispatchers.IO) {
        var attempt = 0
        while (true) {
            val req = Request.Builder().url(url).header("Accept", "application/json")
                .apply { token()?.let { header("Authorization", "Bearer $it") } }.build()
            http.newCall(req).execute().use { r ->
                when {
                    r.isSuccessful -> return@withContext r.body!!.string() to r.header("Link")
                    r.code == 429 -> {
                        val after = r.header("Retry-After")?.toLongOrNull()
                        if (attempt >= maxRetries) throw HubException.RateLimited(after)
                        delay(((after ?: (1L shl attempt)) * 1000).coerceAtMost(30_000))
                    }
                    r.code == 404 -> throw HubException.NotFound(what)
                    r.code == 401 || r.code == 403 -> throw HubException.GatedOrUnauthorized(what)
                    r.code >= 500 && attempt < maxRetries -> delay(500L shl attempt)
                    else -> throw HubException.Http(r.code)
                }
            }
            attempt++
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    companion object {
        private val NEXT = Regex("<([^>]+)>;\\s*rel=\"next\"")
        fun parseNextLink(link: String?): String? = link?.let { NEXT.find(it)?.groupValues?.get(1) }
    }
}

@Serializable
internal data class ApiSibling(val rfilename: String, val size: Long? = null, val lfs: ApiLfs? = null)
@Serializable
internal data class ApiLfs(val sha256: String? = null, val size: Long? = null)
@Serializable
internal data class ApiCardData(val license: String? = null)
@Serializable
internal data class ApiModel(
    val id: String? = null,
    val modelId: String? = null,
    val author: String? = null,
    @SerialName("pipeline_tag") val pipelineTag: String? = null,
    @SerialName("library_name") val libraryName: String? = null,
    val tags: List<String> = emptyList(),
    val downloads: Long = 0,
    val likes: Long = 0,
    val lastModified: String? = null,
    val gated: kotlinx.serialization.json.JsonElement? = null,
    val cardData: ApiCardData? = null,
    val siblings: List<ApiSibling> = emptyList(),
) {
    fun toCard(): ModelCard {
        val repo = id ?: modelId ?: ""
        return ModelCard(
            repoId = repo,
            author = author ?: repo.substringBefore('/', "").ifEmpty { null },
            pipelineTag = pipelineTag,
            libraryName = libraryName,
            tags = tags,
            license = cardData?.license ?: tags.firstOrNull { it.startsWith("license:") }?.removePrefix("license:"),
            downloads = downloads,
            likes = likes,
            lastModified = lastModified,
            gated = gated?.toString()?.let { it != "false" } ?: false,
            files = siblings.map { ModelFile(it.rfilename, it.lfs?.size ?: it.size, it.lfs?.sha256) },
        )
    }
}
