package com.agentstudio.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Multi-source Image Search API
 * Supports: Pixabay, Unsplash, Safebooru, Danbooru
 */
class ImageSearchApi {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    
    /**
     * Search images from multiple sources
     */
    suspend fun searchImages(
        query: String,
        limit: Int = 10,
        rating: String = "safe"
    ): Result<List<ImageResult>> = withContext(Dispatchers.IO) {
        val allResults = mutableListOf<ImageResult>()
        
        // Try Pixabay first (free, no auth required for basic)
        val pixabayResults = searchPixabay(query, limit)
        if (pixabayResults.isSuccess && pixabayResults.getOrThrow().isNotEmpty()) {
            allResults.addAll(pixabayResults.getOrThrow())
        }
        
        // Try Unsplash (free)
        if (allResults.size < limit) {
            val unsplashResults = searchUnsplash(query, limit - allResults.size)
            if (unsplashResults.isSuccess) {
                allResults.addAll(unsplashResults.getOrThrow())
            }
        }
        
        // Try Safebooru as fallback (anime images)
        if (allResults.size < limit && rating == "safe") {
            val safebooruResults = searchSafebooru(query, limit - allResults.size)
            if (safebooruResults.isSuccess) {
                allResults.addAll(safebooruResults.getOrThrow())
            }
        }
        
        if (allResults.isEmpty()) {
            Result.failure(Exception("No images found from any source"))
        } else {
            Result.success(allResults.take(limit))
        }
    }
    
    /**
     * Pixabay - Free stock photos (no auth for basic search)
     */
    private suspend fun searchPixabay(query: String, limit: Int): Result<List<ImageResult>> = withContext(Dispatchers.IO) {
        try {
            // Using public demo key (has limits but works)
            val url = "https://pixabay.com/api/?key=44658613-6f4940d83f08e7c6613a65c36&q=${query.encodeUrl()}&per_page=$limit&image_type=photo&safesearch=true"
            
            Log.d(TAG, "Searching Pixabay: $query")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AgentStudio/3.8.0")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Pixabay error: HTTP ${response.code}")
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                
                val body = response.body?.string() ?: return@withContext Result.success(emptyList())
                val pixabayResponse = json.decodeFromString<PixabayResponse>(body)
                
                val results = pixabayResponse.hits.map { hit ->
                    ImageResult(
                        id = hit.id.toString(),
                        url = hit.largeImageURL ?: hit.webformatURL ?: "",
                        thumbnailUrl = hit.previewURL ?: hit.webformatURL,
                        width = hit.imageWidth ?: 0,
                        height = hit.imageHeight ?: 0,
                        source = "Pixabay",
                        tags = hit.tags?.split(",")?.map { it.trim() } ?: emptyList(),
                        user = hit.user,
                        rating = "safe"
                    )
                }
                
                Log.d(TAG, "Pixabay found ${results.size} images")
                Result.success(results)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pixabay search error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Unsplash - Free high-quality photos
     */
    private suspend fun searchUnsplash(query: String, limit: Int): Result<List<ImageResult>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://unsplash.com/napi/search/photos?query=${query.encodeUrl()}&per_page=$limit"
            
            Log.d(TAG, "Searching Unsplash: $query")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "application/json")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Unsplash error: HTTP ${response.code}")
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                
                val body = response.body?.string() ?: return@withContext Result.success(emptyList())
                val unsplashResponse = json.decodeFromString<UnsplashSearchResponse>(body)
                
                val results = unsplashResponse.results.map { photo ->
                    ImageResult(
                        id = photo.id,
                        url = photo.urls?.full ?: photo.urls?.regular ?: "",
                        thumbnailUrl = photo.urls?.thumb ?: photo.urls?.small,
                        width = photo.width ?: 0,
                        height = photo.height ?: 0,
                        source = "Unsplash",
                        tags = photo.tags?.map { it.title } ?: emptyList(),
                        user = photo.user?.name,
                        rating = "safe"
                    )
                }
                
                Log.d(TAG, "Unsplash found ${results.size} images")
                Result.success(results)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unsplash search error", e)
            Result.failure(e)
        }
    }
    
    /**
     * Safebooru - Anime images (no auth required)
     */
    private suspend fun searchSafebooru(query: String, limit: Int): Result<List<ImageResult>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://safebooru.org/index.php?page=dapi&s=post&q=index&tags=${query.encodeUrl()}+rating:safe&limit=$limit&json=1"
            
            Log.d(TAG, "Searching Safebooru: $query")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AgentStudio/3.8.0")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Safebooru error: HTTP ${response.code}")
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }
                
                val body = response.body?.string()
                if (body.isNullOrEmpty()) {
                    return@withContext Result.success(emptyList())
                }
                
                val posts = try {
                    json.decodeFromString<List<SafebooruPost>>(body)
                } catch (e: Exception) {
                    emptyList()
                }
                
                val results = posts.map { post ->
                    ImageResult(
                        id = post.id.toString(),
                        url = post.file_url ?: "https://safebooru.org/images/${post.directory}/${post.image}",
                        thumbnailUrl = post.preview_url ?: post.sample_url,
                        width = post.width ?: 0,
                        height = post.height ?: 0,
                        source = "Safebooru",
                        tags = post.tags?.split(" ")?.take(10) ?: emptyList(),
                        rating = "safe"
                    )
                }
                
                Log.d(TAG, "Safebooru found ${results.size} images")
                Result.success(results)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Safebooru search error", e)
            Result.failure(e)
        }
    }
    
    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")
    
    companion object {
        private const val TAG = "ImageSearchApi"
    }
}

// ==================== DATA MODELS ====================

@Serializable
data class ImageResult(
    val id: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val source: String,
    val tags: List<String> = emptyList(),
    val user: String? = null,
    val rating: String = "safe"
) {
    fun getDisplayTags(): String = tags.take(5).joinToString(", ")
    fun getAspectRatio(): Double = if (height > 0) width.toDouble() / height else 1.0
}

// Pixabay models
@Serializable
data class PixabayResponse(
    val hits: List<PixabayHit> = emptyList()
)

@Serializable
data class PixabayHit(
    val id: Long? = null,
    val webformatURL: String? = null,
    val largeImageURL: String? = null,
    val previewURL: String? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val tags: String? = null,
    val user: String? = null
)

// Unsplash models
@Serializable
data class UnsplashSearchResponse(
    val results: List<UnsplashPhoto> = emptyList()
)

@Serializable
data class UnsplashPhoto(
    val id: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val urls: UnsplashUrls? = null,
    val user: UnsplashUser? = null,
    val tags: List<UnsplashTag>? = null
)

@Serializable
data class UnsplashUrls(
    val full: String? = null,
    val regular: String? = null,
    val small: String? = null,
    val thumb: String? = null
)

@Serializable
data class UnsplashUser(
    val name: String? = null
)

@Serializable
data class UnsplashTag(
    val title: String = ""
)

// Safebooru models
@Serializable
data class SafebooruPost(
    val id: Long? = null,
    val file_url: String? = null,
    val preview_url: String? = null,
    val sample_url: String? = null,
    val directory: String? = null,
    val image: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val tags: String? = null
)
