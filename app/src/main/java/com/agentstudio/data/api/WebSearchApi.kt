package com.agentstudio.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class WebSearchApi {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    /**
     * Search the web using DuckDuckGo Instant Answer API
     * For more comprehensive results, consider using a dedicated search API
     */
    suspend fun search(query: String, maxResults: Int = 10): Result<List<WebSearchResult>> = withContext(Dispatchers.IO) {
        try {
            // Using DuckDuckGo Instant Answer API (free, no API key needed)
            val url = "https://api.duckduckgo.com/?q=${query.encodeUrl()}&format=json&no_html=1"
            
            Log.d(TAG, "Searching: $query")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AgentStudio/1.0")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                
                val body = response.body?.string()
                if (body.isNullOrEmpty()) {
                    return@withContext Result.success(emptyList())
                }
                
                val ddgResponse = json.decodeFromString<DuckDuckGoResponse>(body)
                
                val results = mutableListOf<WebSearchResult>()
                
                // Add abstract if available
                if (!ddgResponse.Abstract.isNullOrBlank() && !ddgResponse.AbstractURL.isNullOrBlank()) {
                    results.add(WebSearchResult(
                        title = ddgResponse.Heading ?: query,
                        url = ddgResponse.AbstractURL,
                        snippet = ddgResponse.Abstract,
                        source = ddgResponse.AbstractSource ?: "DuckDuckGo"
                    ))
                }
                
                // Add related topics
                ddgResponse.RelatedTopics?.forEach { topic ->
                    if (results.size >= maxResults) return@forEach
                    
                    if (!topic.Text.isNullOrBlank() && !topic.FirstURL.isNullOrBlank()) {
                        results.add(WebSearchResult(
                            title = topic.Text.take(100),
                            url = topic.FirstURL,
                            snippet = topic.Text,
                            source = "DuckDuckGo"
                        ))
                    }
                }
                
                // Add results from results array
                ddgResponse.Results?.forEach { result ->
                    if (results.size >= maxResults) return@forEach
                    
                    if (!result.Text.isNullOrBlank() && !result.FirstURL.isNullOrBlank()) {
                        results.add(WebSearchResult(
                            title = result.Text.take(100),
                            url = result.FirstURL,
                            snippet = result.Text,
                            source = "DuckDuckGo"
                        ))
                    }
                }
                
                Log.d(TAG, "Found ${results.size} results")
                Result.success(results.take(maxResults))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching web", e)
            Result.failure(e)
        }
    }
    
    /**
     * Search using Wikipedia API for more detailed information
     */
    suspend fun searchWikipedia(query: String, maxResults: Int = 5): Result<List<WebSearchResult>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://en.wikipedia.org/w/api.php?action=opensearch&search=${query.encodeUrl()}&limit=$maxResults&format=json"
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AgentStudio/1.0")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                
                val body = response.body?.string()
                if (body.isNullOrEmpty()) {
                    return@withContext Result.success(emptyList())
                }
                
                // Wikipedia opensearch returns: [query, [titles], [descriptions], [urls]]
                val array = json.parseToJsonElement(body).jsonArray
                
                val titles = array.getOrNull(1)?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                val descriptions = array.getOrNull(2)?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                val urls = array.getOrNull(3)?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                
                val results = titles.mapIndexed { index, title ->
                    WebSearchResult(
                        title = title,
                        url = urls.getOrNull(index) ?: "",
                        snippet = descriptions.getOrNull(index) ?: "",
                        source = "Wikipedia"
                    )
                }.filter { it.url.isNotEmpty() }
                
                Result.success(results)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching Wikipedia", e)
            Result.failure(e)
        }
    }
    
    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
    
    @Serializable
    data class DuckDuckGoResponse(
        val Abstract: String? = null,
        val AbstractText: String? = null,
        val AbstractSource: String? = null,
        val AbstractURL: String? = null,
        val Heading: String? = null,
        val RelatedTopics: List<RelatedTopic>? = null,
        val Results: List<RelatedTopic>? = null
    )
    
    @Serializable
    data class RelatedTopic(
        val Text: String? = null,
        val FirstURL: String? = null,
        val Icon: Icon? = null
    )
    
    @Serializable
    data class Icon(
        val URL: String? = null,
        val Width: Int? = null,
        val Height: Int? = null
    )
    
    companion object {
        private const val TAG = "WebSearchApi"
    }
}

@Serializable
data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val source: String = ""
)
