package com.agentstudio.data.local

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Local LLM Inference Engine using llama.cpp
 *
 * This engine provides real GGUF model inference using llama.cpp native library.
 * If the native library is not available, it will provide a clear message to the user.
 */
class LocalLLMEngine(private val context: Context) {

    companion object {
        private const val TAG = "LocalLLMEngine"

        private var instance: LocalLLMEngine? = null

        fun getInstance(context: Context): LocalLLMEngine {
            return instance ?: synchronized(this) {
                instance ?: LocalLLMEngine(context.applicationContext).also { instance = it }
            }
        }

        fun isNativeAvailable(): Boolean = LlamaJNI.isLoaded
    }

    private var modelContext: LlamaJNI.ModelContext? = null
    private var isLoaded = false
    private var modelPath: String? = null

    /**
     * Check if the engine is ready for inference
     */
    fun isReady(): Boolean = isLoaded && modelContext?.isValid == true

    /**
     * Check if real inference is available (native library loaded)
     */
    fun isRealInferenceAvailable(): Boolean = LlamaJNI.isLoaded

    /**
     * Get engine status info
     */
    fun getStatusInfo(): EngineStatus {
        return EngineStatus(
            nativeAvailable = LlamaJNI.isLoaded,
            modelLoaded = isLoaded,
            modelPath = modelPath,
            mode = if (LlamaJNI.isLoaded) "Real Inference (llama.cpp)" else "Native Required"
        )
    }

    data class EngineStatus(
        val nativeAvailable: Boolean,
        val modelLoaded: Boolean,
        val modelPath: String?,
        val mode: String
    )

    /**
     * Load a GGUF model for inference
     */
    suspend fun loadModel(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("Model file not found: $path"))
            }

            val fileSize = file.length()
            if (fileSize < 100_000_000L) {
                return@withContext Result.failure(Exception("Model file too small or incomplete"))
            }

            Log.d(TAG, "Loading model from: $path (${fileSize / 1_000_000}MB)")

            // Check if native library is available
            if (!LlamaJNI.isLoaded) {
                Log.e(TAG, "Native library not loaded - cannot perform real inference")
                return@withContext Result.failure(
                    Exception(
                        "llama.cpp native library not available.\n\n" +
                        "To enable local AI inference:\n" +
                        "1. Build the app with Android NDK\n" +
                        "2. Ensure llama.cpp is compiled for your device architecture\n\n" +
                        "For now, please use Cloud AI mode."
                    )
                )
            }

            // Initialize llama.cpp backend
            if (!LlamaJNI.initialize()) {
                return@withContext Result.failure(Exception("Failed to initialize llama.cpp backend"))
            }

            // Free existing model if any
            modelContext?.free()
            modelContext = null
            isLoaded = false

            // Load new model
            val result = LlamaJNI.loadModel(path)

            result.fold(
                onSuccess = { ctx ->
                    modelContext = ctx
                    modelPath = path
                    isLoaded = true
                    Log.i(TAG, "Model loaded successfully with llama.cpp")
                    Log.i(TAG, "Model info: ${ctx.getInfo()}")
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to load model: ${error.message}")
                    return@withContext Result.failure(error)
                }
            )

            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            Result.failure(e)
        }
    }

    /**
     * Unload the current model
     */
    fun unloadModel() {
        modelContext?.free()
        modelContext = null
        isLoaded = false
        modelPath = null
        Log.d(TAG, "Model unloaded")
    }

    /**
     * Generate text completion
     */
    fun generate(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Float = 0.7f
    ): Flow<String> = flow {
        if (!LlamaJNI.isLoaded) {
            emit(buildNativeRequiredMessage())
            return@flow
        }

        if (!isReady()) {
            throw Exception("Model not loaded. Please load a model first.")
        }

        val ctx = modelContext ?: throw Exception("Model context is null")

        Log.d(TAG, "Starting generation with prompt: ${prompt.take(50)}...")

        try {
            // Build full prompt with chat template
            val fullPrompt = buildPrompt(prompt)

            // Generate response
            val response = ctx.generate(
                prompt = fullPrompt,
                maxTokens = maxTokens,
                temperature = temperature,
                topP = 0.9f,
                topK = 40,
                seed = System.currentTimeMillis()
            )

            Log.d(TAG, "Generation complete: ${response.length} chars")
            emit(response)

        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            emit("Error during generation: ${e.message}")
        }

    }.flowOn(Dispatchers.IO)

    /**
     * Generate with conversation history
     */
    fun chat(
        messages: List<Pair<String, String>>,
        maxTokens: Int = 512,
        temperature: Float = 0.7f
    ): Flow<String> = flow {
        if (!LlamaJNI.isLoaded) {
            emit(buildNativeRequiredMessage())
            return@flow
        }

        if (!isReady()) {
            throw Exception("Model not loaded")
        }

        val ctx = modelContext ?: throw Exception("Model context is null")

        // Get the last user message
        val lastUserMessage = messages.lastOrNull { it.first == "user" }?.second ?: ""

        Log.d(TAG, "Starting chat generation for: ${lastUserMessage.take(50)}...")

        try {
            // Build chat prompt
            val chatPrompt = buildChatPrompt(messages)

            // Generate response
            val response = ctx.generate(
                prompt = chatPrompt,
                maxTokens = maxTokens,
                temperature = temperature,
                topP = 0.9f,
                topK = 40,
                seed = System.currentTimeMillis()
            )

            Log.d(TAG, "Chat generation complete: ${response.length} chars")
            emit(response)

        } catch (e: Exception) {
            Log.e(TAG, "Chat generation failed", e)
            emit("Error during generation: ${e.message}")
        }

    }.flowOn(Dispatchers.IO)

    /**
     * Build prompt for completion
     */
    private fun buildPrompt(userPrompt: String): String {
        return buildString {
            append("<start_of_turn>user\n")
            append(userPrompt)
            append("<end_of_turn>\n")
            append("<start_of_turn>model\n")
        }
    }

    /**
     * Build prompt for chat with history
     */
    private fun buildChatPrompt(messages: List<Pair<String, String>>): String {
        return buildString {
            // System message first
            val systemMessage = messages.firstOrNull { it.first == "system" }?.second
            if (systemMessage != null) {
                append("<start_of_turn>system\n")
                append(systemMessage)
                append("<end_of_turn>\n")
            }

            // Conversation history
            for ((role, content) in messages) {
                if (role == "system") continue
                append("<start_of_turn>")
                append(role)
                append("\n")
                append(content)
                append("<end_of_turn>\n")
            }

            // Start model response
            append("<start_of_turn>model\n")
        }
    }

    /**
     * Build message when native library is required
     */
    private fun buildNativeRequiredMessage(): String {
        return buildString {
            appendLine("⚠️ **Native Library Required**")
            appendLine()
            appendLine("Local AI inference requires the llama.cpp native library.")
            appendLine()
            appendLine("**To enable Local AI:**")
            appendLine("1. Build the app with Android NDK installed")
            appendLine("2. Run: `./gradlew assembleRelease` with NDK configured")
            appendLine("3. Or use GitHub Actions to build native libraries")
            appendLine()
            appendLine("**Current Status:** Native library not loaded")
            appendLine()
            appendLine("💡 **Alternative:** Use Cloud AI mode for full AI capabilities.")
        }
    }

    /**
     * Get model info
     */
    fun getModelInfo(): Map<String, Any> {
        return mapOf(
            "loaded" to isLoaded,
            "modelPath" to (modelPath ?: "none"),
            "nativeAvailable" to LlamaJNI.isLoaded,
            "mode" to if (LlamaJNI.isLoaded) "real" else "unavailable",
            "modelInfo" to (modelContext?.getInfo() ?: "N/A")
        )
    }
}
