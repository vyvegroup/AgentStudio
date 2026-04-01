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
#include <random>

// llama.cpp headers - only use core API
#include "llama.h"

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
    const llama_vocab* vocab = nullptr;
    std::string modelPath;
    int nCtx = DEFAULT_CONTEXT_SIZE;
    int nVocab = 0;
    int nThreads = 2;
};

static ModelContext* g_context = nullptr;

// Simple tokenization using llama API
static std::vector<llama_token> tokenize_simple(const llama_vocab* vocab, const std::string& text, bool add_bos) {
    std::vector<llama_token> tokens;
    tokens.resize(text.size() + 1); // Upper bound
    
    int n_tokens = llama_tokenize(
        vocab,
        text.c_str(),
        text.size(),
        tokens.data(),
        tokens.size(),
        add_bos,
        false
    );
    
    if (n_tokens > 0) {
        tokens.resize(n_tokens);
    } else {
        tokens.clear();
    }
    
    return tokens;
}

// Simple token to text conversion
static std::string token_to_text(const llama_vocab* vocab, llama_token token) {
    std::string result;
    result.resize(16); // Most tokens are small
    
    int n = llama_token_to_piece(
        vocab,
        token,
        result.data(),
        result.size(),
        0,
        false
    );
    
    if (n > 0) {
        result.resize(n);
    } else {
        result.clear();
    }
    
    return result;
}

// Simple sampling with temperature
static llama_token sample_token(llama_context* ctx, const llama_vocab* vocab, float temp, uint32_t seed) {
    // Get logits
    float* logits = llama_get_logits(ctx);
    int n_vocab = llama_vocab_n_tokens(vocab);
    
    // Simple temperature sampling
    std::vector<float> probs(n_vocab);
    float max_logit = -1e10f;
    
    // Find max for numerical stability
    for (int i = 0; i < n_vocab; i++) {
        if (logits[i] > max_logit) {
            max_logit = logits[i];
        }
    }
    
    // Apply temperature and compute softmax
    float sum = 0.0f;
    for (int i = 0; i < n_vocab; i++) {
        probs[i] = expf((logits[i] - max_logit) / temp);
        sum += probs[i];
    }
    
    // Normalize
    for (int i = 0; i < n_vocab; i++) {
        probs[i] /= sum;
    }
    
    // Sample using cumulative distribution
    static std::mt19937 rng(seed);
    std::uniform_real_distribution<float> dist(0.0f, 1.0f);
    float r = dist(rng);
    
    float cumsum = 0.0f;
    for (int i = 0; i < n_vocab; i++) {
        cumsum += probs[i];
        if (r < cumsum) {
            return i;
        }
    }
    
    return n_vocab - 1;
}

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
    llama_model* model = llama_model_load_from_file(pathStr.c_str(), model_params);
    if (!model) {
        LOGE("Failed to load model from %s", pathStr.c_str());
        return 0;
    }

    // Get vocab from model
    const llama_vocab* vocab = llama_model_get_vocab(model);
    if (!vocab) {
        LOGE("Failed to get vocab from model");
        llama_model_free(model);
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
    llama_context* ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create context");
        llama_model_free(model);
        return 0;
    }

    // Create context wrapper
    ModelContext* mc = new ModelContext();
    mc->model = model;
    mc->ctx = ctx;
    mc->vocab = vocab;
    mc->modelPath = pathStr;
    mc->nCtx = ctx_params.n_ctx;
    mc->nVocab = llama_vocab_n_tokens(vocab);
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
        if (mc->ctx) {
            llama_free(mc->ctx);
        }
        if (mc->model) {
            llama_model_free(mc->model);
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

    // Tokenize prompt using vocab
    std::vector<llama_token> tokens = tokenize_simple(mc->vocab, promptStr, true);

    LOGD("Tokenized to %zu tokens", tokens.size());

    // Clear KV cache
    llama_memory_clear(llama_get_memory(mc->ctx), false);

    // Decode prompt in batches
    for (size_t i = 0; i < tokens.size(); i += BATCH_SIZE) {
        size_t batch_size = std::min(tokens.size() - i, (size_t)BATCH_SIZE);
        
        llama_batch batch = llama_batch_get_one(tokens.data() + i, batch_size);

        if (llama_decode(mc->ctx, batch) != 0) {
            LOGE("Failed to decode prompt batch at index %zu", i);
            return env->NewStringUTF("Error: Failed to process prompt");
        }
    }

    // Use provided seed or generate one
    uint32_t useed = (seed < 0) ? (uint32_t)time(nullptr) : (uint32_t)seed;
    float temp = (temperature > 0.0f) ? temperature : DEFAULT_TEMP;

    // Generation
    std::string result;
    int current_pos = tokens.size();

    for (int i = 0; i < maxTokens; i++) {
        // Sample next token
        llama_token nextToken = sample_token(mc->ctx, mc->vocab, temp, useed + i);

        // Check for EOS
        if (llama_vocab_is_eog(mc->vocab, nextToken)) {
            LOGD("Reached EOS token at position %d", i);
            break;
        }

        // Convert to text
        std::string tokenStr = token_to_text(mc->vocab, nextToken);
        result += tokenStr;

        // Decode for next iteration
        llama_batch batch = llama_batch_get_one(&nextToken, 1);
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
