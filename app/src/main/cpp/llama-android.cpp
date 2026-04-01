/**
 * llama.cpp Android JNI Interface
 * Provides native LLM inference for Android
 *
 * Uses llama.cpp API (latest version)
 */

#include <jni.h>
#include <android/log.h>
#include <string>
#include <cstring>
#include <thread>
#include <atomic>
#include <mutex>
#include <chrono>
#include <vector>

// llama.cpp headers
#include "llama.h"

#define TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

// Global state
static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static const llama_vocab* g_vocab = nullptr;
static std::string g_model_info;
static std::atomic<bool> g_stop_generation(false);
static std::mutex g_mutex;

// Default parameters
static const int DEFAULT_N_CTX = 2048;
static const int DEFAULT_N_THREADS = 4;

extern "C" {

/**
 * Load a GGUF model
 */
JNIEXPORT jboolean JNICALL
Java_com_agentstudio_data_local_LlamaJNI_loadModel(
    JNIEnv* env,
    jobject /* this */,
    jstring modelPath,
    jint nCtx,
    jint nThreads
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    // Free existing model
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) {
        LOGE("Failed to get model path string");
        return JNI_FALSE;
    }

    LOGI("Loading model from: %s", path);
    LOGI("Parameters: ctx=%d, threads=%d", nCtx, nThreads);

    // Initialize llama.cpp backend
    llama_backend_init();

    // Get model parameters
    llama_model_params model_params = llama_model_default_params();

    // Load model using new API
    g_model = llama_model_load_from_file(path, model_params);
    if (!g_model) {
        LOGE("Failed to load model from: %s", path);
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    // Get context parameters
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = nCtx > 0 ? nCtx : DEFAULT_N_CTX;
    ctx_params.n_threads = nThreads > 0 ? nThreads : DEFAULT_N_THREADS;
    ctx_params.n_threads_batch = ctx_params.n_threads;

    // Create context
    g_ctx = llama_new_context_with_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("Failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    // Get vocab
    g_vocab = llama_model_get_vocab(g_model);

    // Get model info
    int n_vocab = llama_vocab_n_tokens(g_vocab);
    int n_ctx_train = llama_model_n_ctx_train(g_model);
    int n_embd = llama_model_n_embd(g_model);

    char info[256];
    snprintf(info, sizeof(info), "Vocab: %d, Ctx: %d, Embd: %d",
             n_vocab, n_ctx_train, n_embd);
    g_model_info = std::string(info);

    LOGI("Model loaded successfully: %s", info);
    env->ReleaseStringUTFChars(modelPath, path);

    return JNI_TRUE;
}

/**
 * Check if model is loaded
 */
JNIEXPORT jboolean JNICALL
Java_com_agentstudio_data_local_LlamaJNI_isModelLoaded(
    JNIEnv* /* env */,
    jobject /* this */
) {
    return (g_model != nullptr && g_ctx != nullptr) ? JNI_TRUE : JNI_FALSE;
}

/**
 * Get model info string
 */
JNIEXPORT jstring JNICALL
Java_com_agentstudio_data_local_LlamaJNI_getModelInfo(
    JNIEnv* env,
    jobject /* this */
) {
    if (!g_model) {
        return env->NewStringUTF("No model loaded");
    }
    return env->NewStringUTF(g_model_info.c_str());
}

/**
 * Generate text completion
 */
JNIEXPORT jstring JNICALL
Java_com_agentstudio_data_local_LlamaJNI_generate(
    JNIEnv* env,
    jobject /* this */,
    jstring prompt,
    jint maxTokens,
    jfloat temperature,
    jfloat topP,
    jint topK
) {
    if (!g_ctx || !g_model || !g_vocab) {
        return env->NewStringUTF("Error: Model not loaded");
    }

    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);
    if (!prompt_str) {
        return env->NewStringUTF("Error: Invalid prompt");
    }

    LOGI("Generating: max_tokens=%d, temp=%.2f, top_p=%.2f, top_k=%d",
         maxTokens, temperature, topP, topK);

    std::string input(prompt_str);
    env->ReleaseStringUTFChars(prompt, prompt_str);

    // Tokenize input
    const bool add_bos = true;
    std::vector<llama_token> tokens;
    tokens.resize(input.size() + 1);

    int n_tokens = llama_vocab_tokenize(
        g_vocab,
        input.c_str(),
        input.size(),
        tokens.data(),
        tokens.size(),
        add_bos,
        false
    );

    if (n_tokens < 0) {
        return env->NewStringUTF("Error: Tokenization failed");
    }
    tokens.resize(n_tokens);

    LOGD("Input tokens: %d", n_tokens);

    // Prepare for generation
    std::string result;
    g_stop_generation = false;

    // Create batch using new API
    llama_batch batch = llama_batch_init(std::max(512, maxTokens), 0, 1);

    // Add input tokens to batch
    for (int i = 0; i < n_tokens; i++) {
        llama_batch_add(batch, tokens[i], i, {0}, false);
    }

    // Decode input
    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        return env->NewStringUTF("Error: Decode failed");
    }

    // Create sampler chain
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* sampler = llama_sampler_chain_init(sparams);

    if (temperature > 0.0f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(topK > 0 ? topK : 40));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP, 1));
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    } else {
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    }

    // Generate tokens
    int generated = 0;

    while (generated < maxTokens && !g_stop_generation) {
        // Sample next token
        llama_token new_token_id = llama_sampler_sample(sampler, g_ctx, batch.n_tokens - 1);

        // Check for EOS
        if (llama_vocab_is_eog(g_vocab, new_token_id)) {
            LOGD("EOS token reached");
            break;
        }

        // Convert token to text
        char token_buf[32];
        int len = llama_token_to_piece(g_vocab, new_token_id, token_buf, sizeof(token_buf), 0, false);
        if (len > 0) {
            token_buf[len] = '\0';
            result += token_buf;
        }

        // Add new token to batch
        llama_batch_clear(batch);
        llama_batch_add(batch, new_token_id, n_tokens + generated, {0}, true);

        // Decode
        if (llama_decode(g_ctx, batch) != 0) {
            LOGE("Decode failed at token %d", generated);
            break;
        }

        generated++;
    }

    llama_sampler_free(sampler);
    llama_batch_free(batch);

    LOGI("Generated %d tokens", generated);
    return env->NewStringUTF(result.c_str());
}

/**
 * Generate with streaming callback
 */
JNIEXPORT jstring JNICALL
Java_com_agentstudio_data_local_LlamaJNI_generateStream(
    JNIEnv* env,
    jobject thiz,
    jstring prompt,
    jint maxTokens,
    jfloat temperature,
    jobject callback
) {
    if (!g_ctx || !g_model || !g_vocab) {
        return env->NewStringUTF("Error: Model not loaded");
    }

    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);
    if (!prompt_str) {
        return env->NewStringUTF("Error: Invalid prompt");
    }

    // Get callback class and method
    jclass callbackClass = env->GetObjectClass(callback);
    if (!callbackClass) {
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("Error: Invalid callback");
    }

    jmethodID callbackMethod = env->GetMethodID(callbackClass, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;");
    if (!callbackMethod) {
        // Try with String parameter
        callbackMethod = env->GetMethodID(callbackClass, "invoke", "(Ljava/lang/String;)Z");
    }
    if (!callbackMethod) {
        env->ReleaseStringUTFChars(prompt, prompt_str);
        return env->NewStringUTF("Error: Callback method not found");
    }

    std::string input(prompt_str);
    env->ReleaseStringUTFChars(prompt, prompt_str);

    // Tokenize input
    const bool add_bos = true;
    std::vector<llama_token> tokens;
    tokens.resize(input.size() + 1);

    int n_tokens = llama_vocab_tokenize(
        g_vocab,
        input.c_str(),
        input.size(),
        tokens.data(),
        tokens.size(),
        add_bos,
        false
    );

    if (n_tokens < 0) {
        return env->NewStringUTF("Error: Tokenization failed");
    }
    tokens.resize(n_tokens);

    std::string result;
    g_stop_generation = false;

    // Create batch
    llama_batch batch = llama_batch_init(std::max(512, maxTokens), 0, 1);

    for (int i = 0; i < n_tokens; i++) {
        llama_batch_add(batch, tokens[i], i, {0}, false);
    }

    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        return env->NewStringUTF("Error: Decode failed");
    }

    // Create sampler
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    int generated = 0;

    while (generated < maxTokens && !g_stop_generation) {
        llama_token new_token_id = llama_sampler_sample(sampler, g_ctx, batch.n_tokens - 1);

        if (llama_vocab_is_eog(g_vocab, new_token_id)) {
            break;
        }

        char token_buf[32];
        int len = llama_token_to_piece(g_vocab, new_token_id, token_buf, sizeof(token_buf), 0, false);
        if (len > 0) {
            token_buf[len] = '\0';
            result += token_buf;

            // Call callback
            jstring jToken = env->NewStringUTF(token_buf);
            env->CallVoidMethod(callback, callbackMethod, jToken);
            env->DeleteLocalRef(jToken);
        }

        llama_batch_clear(batch);
        llama_batch_add(batch, new_token_id, n_tokens + generated, {0}, true);

        if (llama_decode(g_ctx, batch) != 0) {
            break;
        }

        generated++;
    }

    llama_sampler_free(sampler);
    llama_batch_free(batch);

    return env->NewStringUTF(result.c_str());
}

/**
 * Stop current generation
 */
JNIEXPORT void JNICALL
Java_com_agentstudio_data_local_LlamaJNI_stopGeneration(
    JNIEnv* /* env */,
    jobject /* this */
) {
    g_stop_generation = true;
    LOGI("Generation stop requested");
}

/**
 * Get available memory
 */
JNIEXPORT jlong JNICALL
Java_com_agentstudio_data_local_LlamaJNI_getAvailableMemory(
    JNIEnv* /* env */,
    jobject /* this */
) {
    // Return free memory in MB
    long free_memory = 0;
    FILE* meminfo = fopen("/proc/meminfo", "r");
    if (meminfo) {
        char line[256];
        while (fgets(line, sizeof(line), meminfo)) {
            if (sscanf(line, "MemAvailable: %ld kB", &free_memory) == 1) {
                free_memory /= 1024; // Convert to MB
                break;
            }
        }
        fclose(meminfo);
    }
    return (jlong)free_memory;
}

/**
 * Free model resources
 */
JNIEXPORT void JNICALL
Java_com_agentstudio_data_local_LlamaJNI_freeModel(
    JNIEnv* /* env */,
    jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    g_vocab = nullptr;
    g_model_info.clear();
    LOGI("Model freed");
}

/**
 * Benchmark model
 */
JNIEXPORT jfloat JNICALL
Java_com_agentstudio_data_local_LlamaJNI_benchmark(
    JNIEnv* env,
    jobject /* this */,
    jint nTokens
) {
    if (!g_ctx || !g_model || !g_vocab) {
        return -1.0f;
    }

    LOGI("Running benchmark with %d tokens", nTokens);

    auto start = std::chrono::high_resolution_clock::now();

    // Simple benchmark: generate nTokens
    llama_batch batch = llama_batch_init(nTokens + 64, 0, 1);

    // Start with BOS token
    llama_token bos = llama_vocab_bos(g_vocab);
    llama_batch_add(batch, bos, 0, {0}, false);

    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        return -1.0f;
    }

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    for (int i = 0; i < nTokens; i++) {
        llama_token new_token = llama_sampler_sample(sampler, g_ctx, batch.n_tokens - 1);

        if (llama_vocab_is_eog(g_vocab, new_token)) {
            break;
        }

        llama_batch_clear(batch);
        llama_batch_add(batch, new_token, i + 1, {0}, true);

        if (llama_decode(g_ctx, batch) != 0) {
            break;
        }
    }

    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end - start);

    llama_sampler_free(sampler);
    llama_batch_free(batch);

    float tokens_per_sec = (float)nTokens / (duration.count() / 1000.0f);
    LOGI("Benchmark: %.2f tokens/sec", tokens_per_sec);

    return tokens_per_sec;
}

} // extern "C"
