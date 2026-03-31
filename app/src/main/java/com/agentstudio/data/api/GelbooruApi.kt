package com.agentstudio.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class GelbooruApi {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    suspend fun searchImages(
        tags: String,
        limit: Int = 20,
        page: Int = 0,
        apiKey: String? = null,
        userId: String? = null
    ): Result<List<GelbooruPost>> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = StringBuilder(BASE_URL)
                .append("?page=dapi")
                .append("&s=post")
                .append("&q=index")
                .append("&tags=${tags.encodeUrl()}")
                .append("&limit=$limit")
                .append("&pid=$page")
                .append("&json=1")
            
            // Add API credentials if available (for higher rate limits)
            if (!apiKey.isNullOrEmpty() && !userId.isNullOrEmpty()) {
                urlBuilder.append("&api_key=$apiKey")
                urlBuilder.append("&user_id=$userId")
            }
            
            val url = urlBuilder.toString()
            Log.d(TAG, "Searching Gelbooru: $url")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AgentStudio/1.0")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Gelbooru API error: HTTP ${response.code}")
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                
                val body = response.body?.string()
                if (body.isNullOrEmpty()) {
                    return@withContext Result.success(emptyList())
                }
                
                // Parse response - can be array or object with "post" array
                val posts = try {
                    // Try parsing as direct array first
                    json.decodeFromString<List<GelbooruPost>>(body)
                } catch (e: Exception) {
                    try {
                        // Try parsing as object with post array
                        val wrapper = json.decodeFromString<GelbooruResponse>(body)
                        wrapper.post ?: emptyList()
                    } catch (e2: Exception) {
                        Log.e(TAG, "Failed to parse response", e2)
                        emptyList()
                    }
                }
                
                Log.d(TAG, "Found ${posts.size} posts")
                Result.success(posts)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching Gelbooru", e)
            Result.failure(e)
        }
    }
    
    suspend fun getPostById(id: Int): Result<GelbooruPost?> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL?page=dapi&s=post&q=index&id=$id&json=1"
            
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
                    return@withContext Result.success(null)
                }
                
                val posts = try {
                    json.decodeFromString<List<GelbooruPost>>(body)
                } catch (e: Exception) {
                    emptyList()
                }
                
                Result.success(posts.firstOrNull())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
    
    @Serializable
    data class GelbooruResponse(
        val post: List<GelbooruPost>? = null
    )
    
    companion object {
        private const val TAG = "GelbooruApi"
        const val BASE_URL = "https://gelbooru.com/index.php"
        const val BASE_IMAGE_URL = "https://img3.gelbooru.com/images"
    }
}

@Serializable
data class GelbooruPost(
    val id: Int? = null,
    val owner: String? = null,
    val created_at: String? = null,
    val score: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val source: String? = null,
    val rating: String? = null,  // s=safe, q=questionable, e=explicit
    val tags: String? = null,
    val file_url: String? = null,
    val sample_url: String? = null,
    val preview_url: String? = null,
    val change: Long? = null,
    val directory: String? = null,
    val hash: String? = null,
    val sample_width: Int? = null,
    val sample_height: Int? = null,
    val preview_width: Int? = null,
    val preview_height: Int? = null,
    val parent_id: Int? = null,
    val has_children: Boolean? = null,
    val has_comments: Boolean? = null,
    val has_notes: Boolean? = null,
    val status: String? = null,
    val title: String? = null
) {
    fun getBestImageUrl(): String? {
        return when {
            !file_url.isNullOrEmpty() -> file_url
            !sample_url.isNullOrEmpty() -> sample_url
            !preview_url.isNullOrEmpty() -> preview_url
            else -> null
        }
    }
    
    fun getTagList(): List<String> {
        return tags?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
    }
    
    fun getRatingText(): String = when (rating) {
        "s" -> "Safe"
        "q" -> "Questionable"
        "e" -> "Explicit"
        else -> "Unknown"
    }
}
