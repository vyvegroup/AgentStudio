package com.agentstudio.data.local

import android.util.Log
import java.io.File

/**
 * JNI Interface for llama.cpp
 *
 * This class provides the bridge between Kotlin and llama.cpp native library.
 * The native library must be compiled with NDK and placed in jniLibs.
 *
 * If native library is not available, the app will still work with Cloud AI.
 */
object LlamaJNI {

    private const val TAG = "LlamaJNI"
    private const val LIBRARY_NAME = "llama-android"

    // Load native library
    private var _isLoaded = false

    init {
        try {
            System.loadLibrary(LIBRARY_NAME)
            _isLoaded = true
            Log.i(TAG, "llama.cpp native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "llama.cpp native library not found. Local AI will use cloud fallback.")
            Log.d(TAG, "Error: ${e.message}")
            _isLoaded = false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error loading native library", e)
            _isLoaded = false
        }
    }

    val isLoaded: Boolean get() = _isLoaded

    // Native methods - only called if library is loaded
    @JvmStatic
    private external fun nativeInit(): Boolean

    @JvmStatic
    private external fun nativeLoadModel(modelPath: String): Long

    @JvmStatic
    private external fun nativeFreeModel(contextPtr: Long)

    @JvmStatic
    private external fun nativeGetModelInfo(contextPtr: Long): String

    @JvmStatic
    private external fun nativeGenerate(
        contextPtr: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        seed: Long
    ): String

    @JvmStatic
    private external fun nativeGetVocabSize(contextPtr: Long): Int

    @JvmStatic
    private external fun nativeGetContextSize(contextPtr: Long): Int

    /**
     * Model context wrapper
     */
    class ModelContext private constructor(private val ptr: Long) {
        var isValid: Boolean = ptr != 0L
            private set

        val pointer: Long get() = ptr

        companion object {
            fun create(ptr: Long): ModelContext? {
                return if (ptr != 0L) ModelContext(ptr) else null
            }
        }

        fun free() {
            if (isValid && ptr != 0L && isLoaded) {
                try {
                    nativeFreeModel(ptr)
                } catch (e: Exception) {
                    Log.e(TAG, "Error freeing model", e)
                }
                isValid = false
            }
        }

        fun getInfo(): String {
            return if (isValid && isLoaded) {
                try {
                    nativeGetModelInfo(ptr)
                } catch (e: Exception) {
                    "Error getting model info: ${e.message}"
                }
            } else {
                "Invalid context or native library not loaded"
            }
        }

        fun generate(
            prompt: String,
            maxTokens: Int = 512,
            temperature: Float = 0.7f,
            topP: Float = 0.9f,
            topK: Int = 40,
            seed: Long = -1
        ): String {
            if (!isValid) throw IllegalStateException("Model context is not valid")
            if (!isLoaded) throw IllegalStateException("Native library not loaded")

            return nativeGenerate(ptr, prompt, maxTokens, temperature, topP, topK, seed)
        }

        override fun finalize() {
            free()
        }
    }

    /**
     * Initialize llama.cpp backend
     */
    fun initialize(): Boolean {
        if (!_isLoaded) return false
        return try {
            nativeInit()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize llama.cpp", e)
            false
        }
    }

    /**
     * Load a GGUF model file
     */
    fun loadModel(modelPath: String): Result<ModelContext> {
        if (!_isLoaded) {
            return Result.failure(Exception("Native library not loaded. Local AI requires native compilation."))
        }

        val file = File(modelPath)
        if (!file.exists()) {
            return Result.failure(Exception("Model file not found: $modelPath"))
        }

        return try {
            val ptr = nativeLoadModel(modelPath)
            if (ptr == 0L) {
                Result.failure(Exception("Failed to load model - native returned null pointer"))
            } else {
                Log.i(TAG, "Model loaded successfully: $modelPath")
                Result.success(ModelContext.create(ptr)!!)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            Result.failure(e)
        }
    }
}
