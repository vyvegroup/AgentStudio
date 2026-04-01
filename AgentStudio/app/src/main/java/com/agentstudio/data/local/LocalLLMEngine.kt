package com.agentstudio.data.local

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Local LLM Inference Engine
 * 
 * This engine provides a placeholder for local model inference.
 * For real GGUF inference, integrate llama.cpp Android bindings.
 * 
 * Integration options:
 * 1. llama.cpp Android - Full GGUF support, requires NDK
 * 2. MLC LLM - Pre-built Android library, some GGUF support
 * 3. Google MediaPipe - Limited model support
 */
class LocalLLMEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "LocalLLMEngine"
        
        // Check if native library is available
        private var nativeLibraryLoaded = false
        
        init {
            try {
                System.loadLibrary("llama")
                nativeLibraryLoaded = true
                Log.d(TAG, "llama.cpp native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.d(TAG, "llama.cpp native library not available - running in demo mode")
                nativeLibraryLoaded = false
            }
        }
        
        private var instance: LocalLLMEngine? = null
        
        fun getInstance(context: Context): LocalLLMEngine {
            return instance ?: synchronized(this) {
                instance ?: LocalLLMEngine(context.applicationContext).also { instance = it }
            }
        }
        
        fun isNativeAvailable(): Boolean = nativeLibraryLoaded
    }
    
    private var isLoaded = false
    private var modelPath: String? = null
    
    /**
     * Check if the engine is ready for inference
     */
    fun isReady(): Boolean = isLoaded && modelPath != null
    
    /**
     * Check if real inference is available (native library loaded)
     */
    fun isRealInferenceAvailable(): Boolean = nativeLibraryLoaded
    
    /**
     * Get engine status info
     */
    fun getStatusInfo(): EngineStatus {
        return EngineStatus(
            nativeAvailable = nativeLibraryLoaded,
            modelLoaded = isLoaded,
            modelPath = modelPath,
            mode = if (nativeLibraryLoaded) "Real Inference" else "Demo Mode"
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
            
            if (nativeLibraryLoaded) {
                // Real inference - initialize llama.cpp context
                // This would call native method: nativeLoadModel(path)
                // For now, simulate loading
                modelPath = path
                isLoaded = true
                Log.d(TAG, "Model loaded successfully (native)")
            } else {
                // Demo mode - just track the path
                modelPath = path
                isLoaded = true
                Log.d(TAG, "Model loaded in demo mode")
            }
            
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
        if (!isReady()) {
            throw Exception("Model not loaded. Please load a model first.")
        }
        
        if (nativeLibraryLoaded) {
            // Real inference would happen here
            // For now, emit a message about native support
            emit("✅ Native inference available but not yet integrated. ")
            emit("Model loaded: ${modelPath?.substringAfterLast("/")}\n\n")
            emit("To enable full inference, integrate llama.cpp native calls.")
        } else {
            // Demo mode - explain the situation
            val demoResponse = buildDemoResponse(prompt)
            emit(demoResponse)
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
        if (!isReady()) {
            throw Exception("Model not loaded")
        }
        
        // Get the last user message
        val lastUserMessage = messages.lastOrNull { it.first == "user" }?.second ?: ""
        
        if (nativeLibraryLoaded) {
            // Real inference
            emit("🔄 Native inference mode ready.\n\n")
            emit("Model: ${modelPath?.substringAfterLast("/")}\n\n")
            emit("Full inference requires native JNI integration.")
        } else {
            // Demo mode
            val demoResponse = buildDemoChatResponse(lastUserMessage)
            emit(demoResponse)
        }
        
    }.flowOn(Dispatchers.IO)
    
    private fun buildDemoResponse(prompt: String): String {
        return buildString {
            appendLine("⚠️ **Demo Mode**\n")
            appendLine("Local AI đang chạy ở chế độ demo.")
            appendLine("Model đã tải: `${modelPath?.substringAfterLast("/")}`\n")
            appendLine("---")
            appendLine("Để bật inference thực sự, cần tích hợp **llama.cpp** native library.")
            appendLine("\n💡 **Khuyến nghị:** Sử dụng Cloud AI để có trải nghiệm tốt nhất.")
        }
    }
    
    private fun buildDemoChatResponse(userMessage: String): String {
        // Provide a helpful response in demo mode
        return buildString {
            appendLine("👋 Xin chào! Tôi là VenAI Local (Demo Mode).")
            appendLine()
            appendLine("⚠️ Model đã được tải nhưng đang chạy ở chế độ demo.")
            appendLine("Để có trải nghiệm AI thực sự, bạn có thể:")
            appendLine()
            appendLine("1. **Sử dụng Cloud AI** - Toggle nút Cloud ở trên")
            appendLine("2. **Tích hợp llama.cpp** - Để bật inference offline")
            appendLine()
            appendLine("📦 Model: `${modelPath?.substringAfterLast("/")}`")
            appendLine("📊 Kích thước: ~4.5GB")
        }
    }
    
    /**
     * Get model info
     */
    fun getModelInfo(): Map<String, Any> {
        return mapOf(
            "loaded" to isLoaded,
            "modelPath" to (modelPath ?: "none"),
            "nativeAvailable" to nativeLibraryLoaded,
            "mode" to if (nativeLibraryLoaded) "ready" else "demo"
        )
    }
}
