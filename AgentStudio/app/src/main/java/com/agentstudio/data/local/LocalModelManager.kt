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
        url = "https://huggingface.co/Andycurrent/Gemma-3-4B-VL-it-Gemini-Pro-Heretic-Uncensored-Thinking_GGUF/resolve/main/Gemma-3-4B-VL-it-Gemini-Pro-Heretic-Uncensored-Thinking_mmproj_f16.gguf",
        filename = "gemma-3-4b-vl.gguf",
        sizeBytes = 4_500_000_000L // ~4.5GB
    )
    
    val ALL = listOf(GEMMA_4B)
}

class LocalModelManager(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
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
        val file = File(modelsDir, model.filename)
        return if (file.exists() && file.length() > 100_000_000L) {
            file.absolutePath
        } else null
    }
    
    fun isModelDownloaded(model: LocalModel): Boolean {
        return getLocalModelPath(model) != null
    }
    
    fun getDownloadedModels(): List<LocalModel> {
        return LocalModels.ALL.filter { isModelDownloaded(it) }
    }
    
    suspend fun downloadModel(model: LocalModel): Flow<DownloadProgress> = flow {
        val targetFile = File(modelsDir, model.filename)
        
        if (targetFile.exists() && targetFile.length() > 100_000_000L) {
            emit(DownloadProgress.Completed(targetFile.absolutePath))
            return@flow
        }
        
        _downloadState.update { it + (model.id to DownloadState(downloading = true)) }
        
        try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(model.url)
                    .header("User-Agent", "VenAI/3.0")
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ${response.message}")
                }
                
                val body = response.body ?: throw Exception("Empty response body")
                val contentLength = body.contentLength()
                
                var totalRead = 0L
                val buffer = ByteArray(8192)
                
                FileOutputStream(targetFile).use { output ->
                    body.byteStream().use { input ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            totalRead += read
                            
                            if (contentLength > 0) {
                                val progress = totalRead.toFloat() / contentLength
                                emit(DownloadProgress.Progress(progress, totalRead, contentLength))
                                _downloadState.update { 
                                    it + (model.id to DownloadState(progress = progress, downloading = true))
                                }
                            }
                        }
                    }
                }
            }
            
            _downloadState.update { it + (model.id to DownloadState(completed = true)) }
            emit(DownloadProgress.Completed(targetFile.absolutePath))
            
        } catch (e: Exception) {
            targetFile.delete()
            _downloadState.update { it + (model.id to DownloadState(error = e.message, downloading = false)) }
            emit(DownloadProgress.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)
    
    fun deleteModel(model: LocalModel): Boolean {
        val file = File(modelsDir, model.filename)
        return if (file.exists()) {
            file.delete()
        } else false
    }
    
    fun getDownloadedSize(): Long {
        return modelsDir.listFiles()?.sumOf { it.length() } ?: 0L
    }
    
    sealed class DownloadProgress {
        data class Progress(val progress: Float, val downloaded: Long, val total: Long) : DownloadProgress()
        data class Completed(val path: String) : DownloadProgress()
        data class Error(val message: String) : DownloadProgress()
    }
}
