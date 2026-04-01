#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <thread>
#include <mutex>
#include <atomic>
#include <sstream>
#include <iomanip>
#include <cmath>
#include <unistd.h>

// Only compile llama.cpp code if available
#ifdef LLAMA_CPP_AVAILABLE
#include "llama.h"
#include "common.h"
#include "sampling.h"

#define LOG_TAG "llama-android"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// Global state
static std::mutex g_mutex;
static std::atomic<bool> g_initialized(false);

// Configuration
constexpr int N_THREADS_MIN = 2;
constexpr int N_THREADS_MAX = 4;
constexpr int N_THREADS_HEADROOM = 2;
constexpr int DEFAULT_CONTEXT_SIZE = 4096;
constexpr int BATCH_SIZE = 512;
constexpr float DEFAULT_TEMP = 0.7f;

// Model context
struct ModelContext {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    common_sampler* sampler = nullptr;
    std::string modelPath;
    int nCtx = DEFAULT_CONTEXT_SIZE;
    int nVocab = 0;
    int nThreads = 2;
    llama_pos current_position = 0;
    std::vector<common_chat_msg> chat_msgs;
};

static ModelContext* g_context = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_agentstudio_data_local_LlamaJNI_nativeInit(JNIEnv* env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_initialized) {
        return JNI_TRUE;
    }

    // Initialize llama.cpp backend
    llama_backend_init();
    g_initialized = true;
    LOGI("llama.cpp backend initialized");
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_com_agentstudio_data_local_LlamaJNI_nativeLoadModel(JNIEnv* env, jobject thiz, jstring modelPath) {
    if (!g_initialized) {
        LOGE("Backend not initialized");
        return 0;
    }

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    std::string pathStr(path);
    env->ReleaseStringUTFChars(modelPath, path);

    LOGI("Loading model from: %s", pathStr.c_str());

    // Model parameters
    llama_model_params model_params = llama_model_default_params();

    // Load model
    llama_model* model = llama_load_model_from_file(pathStr.c_str(), model_params);
    if (!model) {
        LOGE("Failed to load model from %s", pathStr.c_str());
        return 0;
    }

    // Calculate threads
    int nThreads = std::max(N_THREADS_MIN,
        std::min(N_THREADS_MAX, (int)sysconf(_SC_NPROCESSORS_ONLN) - N_THREADS_HEADROOM));

    // Context parameters
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = DEFAULT_CONTEXT_SIZE;
    ctx_params.n_batch = BATCH_SIZE;
    ctx_params.n_ubatch = BATCH_SIZE;
    ctx_params.n_threads = nThreads;
    ctx_params.n_threads_batch = nThreads;

    // Create context
    llama_context* ctx = llama_new_context_with_model(model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create context");
        llama_free_model(model);
        return 0;
    }

    // Create sampler
    common_params_sampling sparams;
    sparams.temp = DEFAULT_TEMP;
    common_sampler* sampler = common_sampler_init(model, sparams);

    // Create context wrapper
    ModelContext* mc = new ModelContext();
    mc->model = model;
    mc->ctx = ctx;
    mc->sampler = sampler;
    mc->modelPath = pathStr;
    mc->nCtx = ctx_params.n_ctx;
    mc->nVocab = llama_vocab_n_tokens(llama_model_get_vocab(model));
    mc->nThreads = nThreads;

    g_context = mc;

    LOGI("Model loaded successfully. Vocab size: %d, Context: %d, Threads: %d",
         mc->nVocab, mc->nCtx, mc->nThreads);

    return reinterpret_cast<jlong>(mc);
}

JNIEXPORT void JNICALL
Java_com_agentstudio_data_local_LlamaJNI_nativeFreeModel(JNIEnv* env, jobject thiz, jlong contextPtr) {
    if (contextPtr == 0) return;

    ModelContext* mc = reinterpret_cast<ModelContext*>(contextPtr);
    if (mc) {
        if (mc->sampler) {
            common_sampler_free(mc->sampler);
        }
        if (mc->ctx) {
            llama_free(mc->ctx);
        }
        if (mc->model) {
            llama_free_model(mc->model);
        }
        delete mc;
        if (g_context == mc) {
            g_context = nullptr;
        }
        LOGI("Model freed");
    }
}

JNIEXPORT jstring JNICALL
Java_com_agentstudio_data_local_LlamaJNI_nativeGetModelInfo(JNIEnv* env, jobject thiz, jlong contextPtr) {
    if (contextPtr == 0) {
        return env->NewStringUTF("Invalid context");
    }

    ModelContext* mc = reinterpret_cast<ModelContext*>(contextPtr);

    char model_desc[256];
    llama_model_desc(mc->model, model_desc, sizeof(model_desc));

    std::stringstream info;
    info << "Model: " << mc->modelPath.substr(mc->modelPath.find_last_of("/\\") + 1) << "\n";
    info << "Description: " << model_desc << "\n";
    info << "Context size: " << mc->nCtx << "\n";
    info << "Vocab size: " << mc->nVocab << "\n";
    info << "Threads: " << mc->nThreads << "\n";
    info << "Size: " << std::fixed << std::setprecision(2)
         << (double)llama_model_size(mc->model) / (1024.0 * 1024.0 * 1024.0) << " GB\n";
    info << "Parameters: " << std::fixed << std::setprecision(2)
         << (double)llama_model_n_params(mc->model) / 1e9 << "B";

    return env->NewStringUTF(info.str().c_str());
}

JNIEXPORT jstring JNICALL
Java_com_agentstudio_data_local_LlamaJNI_nativeGenerate(
    JNIEnv* env, jobject thiz,
    jlong contextPtr,
    jstring prompt,
    jint maxTokens,
    jfloat temperature,
    jfloat topP,
    jint topK,
    jlong seed
) {
    if (contextPtr == 0) {
        return env->NewStringUTF("Error: Invalid context");
    }

    ModelContext* mc = reinterpret_cast<ModelContext*>(contextPtr);
    const char* promptCStr = env->GetStringUTFChars(prompt, nullptr);
    std::string promptStr(promptCStr);
    env->ReleaseStringUTFChars(prompt, promptCStr);

    LOGD("Generating with prompt length: %zu, maxTokens: %d", promptStr.length(), maxTokens);

    // Reset sampler with new parameters
    if (mc->sampler) {
        common_sampler_free(mc->sampler);
    }
    common_params_sampling sparams;
    sparams.temp = temperature;
    sparams.top_p = topP;
    sparams.top_k = topK;
    sparams.seed = (seed < 0) ? (uint32_t)time(nullptr) : (uint32_t)seed;
    mc->sampler = common_sampler_init(mc->model, sparams);

    // Tokenize prompt
    bool add_bos = true;
    std::vector<llama_token> tokens = common_tokenize(mc->ctx, promptStr, add_bos, true);

    LOGD("Tokenized to %zu tokens", tokens.size());

    // Clear KV cache
    llama_kv_cache_clear(mc->ctx);

    // Decode prompt in batches
    for (size_t i = 0; i < tokens.size(); i += BATCH_SIZE) {
        size_t batch_size = std::min(tokens.size() - i, (size_t)BATCH_SIZE);

        llama_batch batch = llama_batch_get_one(tokens.data() + i, batch_size, i, 0);

        if (llama_decode(mc->ctx, batch) != 0) {
            LOGE("Failed to decode prompt batch at index %zu", i);
            return env->NewStringUTF("Error: Failed to process prompt");
        }
    }

    // Generation
    std::string result;
    llama_pos current_pos = tokens.size();

    for (int i = 0; i < maxTokens; i++) {
        // Sample next token
        llama_token nextToken = common_sampler_sample(mc->sampler, mc->ctx, -1);
        common_sampler_accept(mc->sampler, nextToken, true);

        // Check for EOS
        if (llama_vocab_is_eog(llama_model_get_vocab(mc->model), nextToken)) {
            LOGD("Reached EOS token at position %d", i);
            break;
        }

        // Convert to text
        std::string tokenStr = common_token_to_piece(mc->ctx, nextToken);
        result += tokenStr;

        // Decode for next iteration
        llama_batch batch = llama_batch_get_one(&nextToken, 1, current_pos, 0);
        if (llama_decode(mc->ctx, batch) != 0) {
            LOGE("Failed to decode generated token");
            break;
        }
        current_pos++;
    }

    LOGD("Generation complete: %zu chars", result.length());
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jint JNICALL
Java_com_agentstudio_data_local_LlamaJNI_nativeGetVocabSize(JNIEnv* env, jobject thiz, jlong contextPtr) {
    if (contextPtr == 0) return 0;
    ModelContext* mc = reinterpret_cast<ModelContext*>(contextPtr);
    return mc->nVocab;
}

JNIEXPORT jint JNICALL
Java_com_agentstudio_data_local_LlamaJNI_nativeGetContextSize(JNIEnv* env, jobject thiz, jlong contextPtr) {
    if (contextPtr == 0) return 0;
    ModelContext* mc = reinterpret_cast<ModelContext*>(contextPtr);
    return mc->nCtx;
}

} // extern "C"

#else
// Fallback when llama.cpp is not available
#define LOG_TAG "llama-android"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_agentstudio_data_local_LlamaJNI_nativeInit(JNIEnv* env, jobject thiz) {
    LOGI("Native library loaded but llama.cpp not compiled in");
    return JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_agentstudio_data_local_LlamaJNI_nativeLoadModel(JNIEnv* env, jobject thiz, jstring modelPath) {
    return 0;
}

JNIEXPORT void JNICALL
Java_com_agentstudio_data_local_LlamaJNI_nativeFreeModel(JNIEnv* env, jobject thiz, jlong contextPtr) {
}

JNIEXPORT jstring JNICALL
Java_com_agentstudio_data_local_LlamaJNI_nativeGetModelInfo(JNIEnv* env, jobject thiz, jlong contextPtr) {
    return env->NewStringUTF("llama.cpp not compiled");
}

JNIEXPORT jstring JNICALL
Java_com_agentstudio_data_local_LlamaJNI_nativeGenerate(
    JNIEnv* env, jobject thiz,
    jlong contextPtr,
    jstring prompt,
    jint maxTokens,
    jfloat temperature,
    jfloat topP,
    jint topK,
    jlong seed
) {
    return env->NewStringUTF("llama.cpp not available");
}

JNIEXPORT jint JNICALL
Java_com_agentstudio_data_local_LlamaJNI_nativeGetVocabSize(JNIEnv* env, jobject thiz, jlong contextPtr) {
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_agentstudio_data_local_LlamaJNI_nativeGetContextSize(JNIEnv* env, jobject thiz, jlong contextPtr) {
    return 0;
}

} // extern "C"

#endif // LLAMA_CPP_AVAILABLE
