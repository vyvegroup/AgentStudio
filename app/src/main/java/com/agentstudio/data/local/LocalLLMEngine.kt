package com.agentstudio.data.local

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Local LLM Engine using llama.cpp
 * Handles model management and text generation
 */
class LocalLLMEngine(private val context: Context) {

    companion object {
        private const val TAG = "LocalLLMEngine"

        // Model storage directory
        private const val MODELS_DIR = "models"

        // Recommended free models for mobile
        val RECOMMENDED_MODELS = listOf(
            ModelInfo(
                id = "qwen2.5-1.5b-instruct-q4_k_m",
                name = "Qwen 2.5 1.5B Instruct (Q4_K_M)",
                url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                size = "1.1 GB",
                description = "Fast and efficient, great for quick responses"
            ),
            ModelInfo(
                id = "qwen2.5-3b-instruct-q4_k_m",
                name = "Qwen 2.5 3B Instruct (Q4_K_M)",
                url = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
                size = "2.0 GB",
                description = "Better quality, recommended for most use cases"
            ),
            ModelInfo(
                id = "phi-3-mini-4k-instruct-q4_k_m",
                name = "Phi-3 Mini 4K Instruct (Q4_K_M)",
                url = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
                size = "2.2 GB",
                description = "Microsoft's compact model with good reasoning"
            ),
            ModelInfo(
                id = "gemma-2-2b-it-q4_k_m",
                name = "Gemma 2 2B IT (Q4_K_M)",
                url = "https://huggingface.co/google/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-q4_k_m.gguf",
                size = "1.6 GB",
                description = "Google's efficient instruction model"
            ),
            ModelInfo(
                id = "llama-3.2-1b-instruct-q4_k_m",
                name = "Llama 3.2 1B Instruct (Q4_K_M)",
                url = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                size = "0.8 GB",
                description = "Meta's smallest Llama 3.2 model"
            ),
            ModelInfo(
                id = "llama-3.2-3b-instruct-q4_k_m",
                name = "Llama 3.2 3B Instruct (Q4_K_M)",
                url = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
                size = "2.0 GB",
                description = "Meta's Llama 3.2 with better quality"
            )
        )
    }

    // Current model state
    private var currentModelPath: String? = null
    private var isLoaded = false

    /**
     * Check if native library is available
     */
    fun isNativeAvailable(): Boolean = LlamaJNI.isNativeLoaded()

    /**
     * Get native library load error if any
     */
    fun getNativeError(): String? = LlamaJNI.getLoadError()

    /**
     * Check if a model is currently loaded
     */
    fun isModelLoaded(): Boolean = isLoaded && LlamaJNI.isModelLoaded()

    /**
     * Get models directory
     */
    fun getModelsDirectory(): File {
        val dir = File(context.filesDir, MODELS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * List downloaded models
     */
    fun listDownloadedModels(): List<File> {
        val dir = getModelsDirectory()
        return dir.listFiles()
            ?.filter { it.extension == "gguf" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Load a model from file
     * @param modelPath Path to the GGUF model file
     * @param nCtx Context window size
     * @param nThreads Number of threads
     * @return LoadResult indicating success or failure
     */
    suspend fun loadModel(
        modelPath: String,
        nCtx: Int = 2048,
        nThreads: Int = 4
    ): LoadResult = withContext(Dispatchers.IO) {
        if (!LlamaJNI.isNativeLoaded()) {
            return@withContext LoadResult.Error(
                "Native library not loaded: ${LlamaJNI.getLoadError()}"
            )
        }

        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            return@withContext LoadResult.Error("Model file not found: $modelPath")
        }

        Log.i(TAG, "Loading model: $modelPath (ctx=$nCtx, threads=$nThreads)")

        try {
            // Free existing model if any
            if (LlamaJNI.isModelLoaded()) {
                LlamaJNI.freeModel()
                isLoaded = false
            }

            val success = LlamaJNI.loadModel(modelPath, nCtx, nThreads)

            if (success) {
                currentModelPath = modelPath
                isLoaded = true
                val info = LlamaJNI.getModelInfo()
                Log.i(TAG, "Model loaded successfully: $info")
                LoadResult.Success(info)
            } else {
                LoadResult.Error("Failed to load model - check logcat for details")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
            LoadResult.Error("Exception: ${e.message}")
        }
    }

    /**
     * Generate text completion
     */
    suspend fun generate(
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        topK: Int = 40
    ): GenerateResult = withContext(Dispatchers.IO) {
        if (!isModelLoaded()) {
            return@withContext GenerateResult.Error("Model not loaded")
        }

        try {
            val result = LlamaJNI.generate(prompt, maxTokens, temperature, topP, topK)
            GenerateResult.Success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Generation error", e)
            GenerateResult.Error("Generation failed: ${e.message}")
        }
    }

    /**
     * Generate text with streaming
     */
    fun generateStream(
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        topK: Int = 40
    ): Flow<String> = flow {
        if (!isModelLoaded()) {
            emit("❌ Model not loaded")
            return@flow
        }

        try {
            val result = LlamaJNI.generateStream(
                prompt,
                maxTokens,
                temperature
            ) { token ->
                // Emit each token
                emit(token)
                true // Continue generation
            }
            // Emit final result
            emit(result)
        } catch (e: Exception) {
            emit("❌ Generation error: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Stop current generation
     */
    fun stopGeneration() {
        if (LlamaJNI.isNativeLoaded()) {
            LlamaJNI.stopGeneration()
        }
    }

    /**
     * Free model resources
     */
    fun freeModel() {
        if (LlamaJNI.isNativeLoaded() && LlamaJNI.isModelLoaded()) {
            LlamaJNI.freeModel()
            isLoaded = false
            currentModelPath = null
            Log.i(TAG, "Model freed")
        }
    }

    /**
     * Get model info
     */
    fun getModelInfo(): String? {
        return if (isModelLoaded()) {
            LlamaJNI.getModelInfo()
        } else {
            null
        }
    }

    /**
     * Benchmark current model
     */
    fun benchmark(nTokens: Int = 64): Float? {
        return if (isModelLoaded()) {
            LlamaJNI.benchmark(nTokens)
        } else {
            null
        }
    }

    /**
     * Get available memory in MB
     */
    fun getAvailableMemory(): Long {
        return if (LlamaJNI.isNativeLoaded()) {
            LlamaJNI.getAvailableMemory()
        } else {
            Runtime.getRuntime().freeMemory() / (1024 * 1024)
        }
    }
}

/**
 * Model information
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val url: String,
    val size: String,
    val description: String
)

/**
 * Load result
 */
sealed class LoadResult {
    data class Success(val info: String) : LoadResult()
    data class Error(val message: String) : LoadResult()
}

/**
 * Generate result
 */
sealed class GenerateResult {
    data class Success(val text: String) : GenerateResult()
    data class Error(val message: String) : GenerateResult()
}
