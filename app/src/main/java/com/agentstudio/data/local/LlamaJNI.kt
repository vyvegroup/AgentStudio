package com.agentstudio.data.local

import android.util.Log

/**
 * JNI interface for llama.cpp native library
 * Provides low-level access to LLM inference
 */
object LlamaJNI {
    private const val TAG = "LlamaJNI"

    // Track native library load status
    private var nativeLoaded = false
    private var loadError: String? = null

    init {
        try {
            System.loadLibrary("llama-android")
            nativeLoaded = true
            Log.i(TAG, "✅ Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            nativeLoaded = false
            loadError = e.message ?: "Unknown error"
            Log.e(TAG, "❌ Failed to load native library: $loadError")
        } catch (e: Exception) {
            nativeLoaded = false
            loadError = e.message ?: "Unknown error"
            Log.e(TAG, "❌ Exception loading native library: $loadError")
        }
    }

    /**
     * Check if native library is loaded
     */
    fun isNativeLoaded(): Boolean = nativeLoaded

    /**
     * Get load error if any
     */
    fun getLoadError(): String? = loadError

    // ========== Native Methods ==========

    /**
     * Load a GGUF model from file path
     * @param modelPath Absolute path to the .gguf model file
     * @param nCtx Context window size (default: 2048)
     * @param nThreads Number of threads for inference (default: 4)
     * @return true if model loaded successfully
     */
    @JvmStatic
    external fun loadModel(modelPath: String, nCtx: Int = 2048, nThreads: Int = 4): Boolean

    /**
     * Check if a model is currently loaded
     * @return true if model is loaded
     */
    @JvmStatic
    external fun isModelLoaded(): Boolean

    /**
     * Get model info string
     * @return Model description or error message
     */
    @JvmStatic
    external fun getModelInfo(): String

    /**
     * Generate text completion
     * @param prompt Input prompt text
     * @param maxTokens Maximum tokens to generate
     * @param temperature Sampling temperature (0.0 - 2.0)
     * @param topP Top-p sampling parameter
     * @param topK Top-k sampling parameter
     * @return Generated text or error message
     */
    @JvmStatic
    external fun generate(
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        topK: Int = 40
    ): String

    /**
     * Generate text with streaming callback
     * @param prompt Input prompt text
     * @param maxTokens Maximum tokens to generate
     * @param temperature Sampling temperature
     * @param callback Callback for each token (returns false to stop)
     * @return Final generated text
     */
    @JvmStatic
    external fun generateStream(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        callback: (token: String) -> Boolean
    ): String

    /**
     * Stop current generation
     */
    @JvmStatic
    external fun stopGeneration()

    /**
     * Get available VRAM/memory in MB
     */
    @JvmStatic
    external fun getAvailableMemory(): Long

    /**
     * Free the current model and release resources
     */
    @JvmStatic
    external fun freeModel()

    /**
     * Benchmark the model
     * @param nTokens Number of tokens for benchmark
     * @return Tokens per second
     */
    @JvmStatic
    external fun benchmark(nTokens: Int): Float
}
