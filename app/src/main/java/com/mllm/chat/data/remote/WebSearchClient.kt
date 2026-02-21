package com.mllm.chat.data.remote

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// Brave Search response models
private data class BraveSearchResponse(
    val web: BraveWebResults?
)

private data class BraveWebResults(
    val results: List<BraveResult>?
)

private data class BraveResult(
    val title: String?,
    val url: String?,
    val description: String?,
    @SerializedName("page_age")
    val pageAge: String?
)

// Tavily response models
private data class TavilySearchResponse(
    val results: List<TavilyResult>?
)

private data class TavilyResult(
    val title: String?,
    val url: String?,
    val content: String?,
    @SerializedName("published_date")
    val publishedDate: String?
)

// Synthetic Search response models
private data class SyntheticSearchResponse(
    val results: List<SyntheticResult>?
)

private data class SyntheticResult(
    val url: String?,
    val title: String?,
    val text: String?,
    val published: String?
)

@Singleton
class WebSearchClient @Inject constructor() {
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Throws IOException on API errors; caller is responsible for catching.
    // Query is truncated to 400 characters to match the tool description limit.
    suspend fun search(
        query: String,
        apiKey: String,
        provider: String
    ): String = withContext(Dispatchers.IO) {
        val trimmedQuery = query.take(400)
        when (provider) {
            "tavily" -> searchTavily(trimmedQuery, apiKey)
            "synthetic" -> searchSynthetic(trimmedQuery, apiKey)
            else -> searchBrave(trimmedQuery, apiKey)
        }
    }

    private fun httpErrorMessage(code: Int): String = when (code) {
        401 -> "Invalid API key (HTTP 401)"
        403 -> "Access forbidden (HTTP 403)"
        429 -> "Rate limit exceeded, try again later (HTTP 429)"
        else -> "HTTP error $code"
    }

    private fun searchBrave(query: String, apiKey: String): String {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("https://api.search.brave.com/res/v1/web/search?q=$encodedQuery&count=5")
            .addHeader("X-Subscription-Token", apiKey)
            .addHeader("Accept", "application/json")
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                throw IOException("Brave Search: ${httpErrorMessage(response.code)}")
            }
            if (body.isNullOrEmpty()) return "No results found"

            val parsed = try {
                gson.fromJson(body, BraveSearchResponse::class.java)
            } catch (e: Exception) {
                throw IOException("Failed to parse Brave Search results: ${e.message}")
            }

            val results = parsed.web?.results ?: return "No results found"
            if (results.isEmpty()) return "No results found"

            buildString {
                results.forEachIndexed { i, result ->
                    appendLine("${i + 1}. ${result.title ?: "No title"}")
                    appendLine("   URL: ${result.url ?: "No URL"}")
                    if (!result.description.isNullOrBlank()) {
                        appendLine("   ${result.description}")
                    }
                    if (!result.pageAge.isNullOrBlank()) {
                        appendLine("   Published: ${result.pageAge}")
                    }
                    appendLine()
                }
            }.trim()
        }
    }

    private fun searchTavily(query: String, apiKey: String): String {
        val requestBody = gson.toJson(
            mapOf(
                "api_key" to apiKey,
                "query" to query,
                "search_depth" to "basic",
                "max_results" to 5
            )
        ).toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.tavily.com/search")
            .post(requestBody)
            .build()

        return client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                throw IOException("Tavily: ${httpErrorMessage(response.code)}")
            }
            if (body.isNullOrEmpty()) return "No results found"

            val parsed = try {
                gson.fromJson(body, TavilySearchResponse::class.java)
            } catch (e: Exception) {
                throw IOException("Failed to parse Tavily results: ${e.message}")
            }

            val results = parsed.results ?: return "No results found"
            if (results.isEmpty()) return "No results found"

            buildString {
                results.forEachIndexed { i, result ->
                    appendLine("${i + 1}. ${result.title ?: "No title"}")
                    appendLine("   URL: ${result.url ?: "No URL"}")
                    if (!result.content.isNullOrBlank()) {
                        appendLine("   ${result.content}")
                    }
                    if (!result.publishedDate.isNullOrBlank()) {
                        appendLine("   Published: ${result.publishedDate}")
                    }
                    appendLine()
                }
            }.trim()
        }
    }

    private fun searchSynthetic(query: String, apiKey: String): String {
        val requestBody = gson.toJson(mapOf("query" to query))
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.synthetic.new/v2/search")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        return client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                throw IOException("Synthetic Search: ${httpErrorMessage(response.code)}")
            }
            if (body.isNullOrEmpty()) return "No results found"

            val parsed = try {
                gson.fromJson(body, SyntheticSearchResponse::class.java)
            } catch (e: Exception) {
                throw IOException("Failed to parse Synthetic Search results: ${e.message}")
            }

            val results = parsed.results ?: return "No results found"
            if (results.isEmpty()) return "No results found"

            buildString {
                results.forEachIndexed { i, result ->
                    appendLine("${i + 1}. ${result.title ?: "No title"}")
                    appendLine("   URL: ${result.url ?: "No URL"}")
                    if (!result.text.isNullOrBlank()) {
                        appendLine("   ${result.text}")
                    }
                    if (!result.published.isNullOrBlank()) {
                        appendLine("   Published: ${result.published}")
                    }
                    appendLine()
                }
            }.trim()
        }
    }
}

