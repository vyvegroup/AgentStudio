package com.agentstudio.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class LocalModel(
    val id: String,
    val name: String,
    val url: String,
    val filename: String,
    val sizeBytes: Long,
    val downloaded: Boolean = false,
    val downloadProgress: Float = 0f,
    val localPath: String? = null
)

object LocalModels {
    val GEMMA_4B = LocalModel(
        id = "gemma-3-4b-vl",
        name = "Gemma 3 4B VL (Thinking)",
        url = "https://huggingface.co/Andycurrent/Gemma-3-4B-VL-it-Gemini-Pro-Heretic-Uncensored-Thinking_GGUF/resolve/main/Gemma-3-4B-VL-it-Gemini-Pro-Heretic-Uncensored-Thinking_mmproj_f16.gguf?download=true",
        filename = "gemma-3-4b-vl.gguf",
        sizeBytes = 4_500_000_000L // ~4.5GB
    )
    
    val ALL = listOf(GEMMA_4B)
}

class LocalModelManager(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    
    private val modelsDir: File
        get() = File(context.filesDir, "local_models").apply { mkdirs() }
    
    private val _downloadState = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadState: StateFlow<Map<String, DownloadState>> = _downloadState.asStateFlow()
    
    data class DownloadState(
        val progress: Float = 0f,
        val downloading: Boolean = false,
        val error: String? = null,
        val completed: Boolean = false
    )
    
    fun getLocalModelPath(model: LocalModel): String? {
        return try {
            val file = File(modelsDir, model.filename)
            if (file.exists() && file.length() > 100_000_000L) {
                file.absolutePath
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    fun isModelDownloaded(model: LocalModel): Boolean {
        return try {
            getLocalModelPath(model) != null
        } catch (e: Exception) {
            false
        }
    }
    
    fun getDownloadedModels(): List<LocalModel> {
        return LocalModels.ALL.filter { isModelDownloaded(it) }
    }
    
    fun deleteModel(model: LocalModel): Boolean {
        return try {
            val file = File(modelsDir, model.filename)
            if (file.exists()) {
                file.delete()
            } else false
        } catch (e: Exception) {
            false
        }
    }
    
    fun getDownloadedSize(): Long {
        return try {
            modelsDir.listFiles()?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
    suspend fun downloadModel(model: LocalModel): Flow<DownloadProgress> = channelFlow {
        val targetFile = File(modelsDir, model.filename)
        
        // Check if already downloaded
        if (targetFile.exists() && targetFile.length() > 100_000_000L) {
            trySend(DownloadProgress.Completed(targetFile.absolutePath))
            return@channelFlow
        }
        
        // Update state
        _downloadState.update { it + (model.id to DownloadState(downloading = true)) }
        
        try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(model.url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) VenAI/3.2")
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "identity")
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ${response.message}")
                }
                
                val body = response.body ?: throw Exception("Empty response body")
                val contentLength = body.contentLength()
                
                // Create temp file first
                val tempFile = File(modelsDir, "${model.filename}.tmp")
                
                var totalRead = 0L
                val buffer = ByteArray(8192)
                var lastEmitTime = 0L
                
                FileOutputStream(tempFile).use { output ->
                    body.byteStream().use { input ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            totalRead += read
                            
                            // Throttle progress updates to every 500ms
                            val currentTime = System.currentTimeMillis()
                            if (contentLength > 0 && currentTime - lastEmitTime > 500) {
                                val progress = totalRead.toFloat() / contentLength
                                trySend(DownloadProgress.Progress(progress, totalRead, contentLength))
                                _downloadState.update { 
                                    it + (model.id to DownloadState(progress = progress, downloading = true))
                                }
                                lastEmitTime = currentTime
                            }
                        }
                    }
                }
                
                // Rename temp file to final file
                if (tempFile.exists() && tempFile.length() > 0) {
                    tempFile.renameTo(targetFile)
                }
            }
            
            _downloadState.update { it + (model.id to DownloadState(completed = true)) }
            trySend(DownloadProgress.Completed(targetFile.absolutePath))
            
        } catch (e: Exception) {
            // Clean up on error
            try {
                File(modelsDir, "${model.filename}.tmp").delete()
            } catch (_: Exception) {}
            
            val errorMessage = e.message ?: "Unknown error occurred"
            _downloadState.update { it + (model.id to DownloadState(error = errorMessage, downloading = false)) }
            trySend(DownloadProgress.Error(errorMessage))
        }
    }
    
    sealed class DownloadProgress {
        data class Progress(val progress: Float, val downloaded: Long, val total: Long) : DownloadProgress()
        data class Completed(val path: String) : DownloadProgress()
        data class Error(val message: String) : DownloadProgress()
    }
}
