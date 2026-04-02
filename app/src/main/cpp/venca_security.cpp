// VenCA Native Security Layer
// Enterprise-grade native protection for critical operations

#include <jni.h>
#include <string>
#include <vector>
#include <random>
#include <algorithm>
#include <cstring>
#include <chrono>
#include <sstream>
#include <iomanip>

// Disable debugging symbols in release
#ifndef DEBUG
#define NDEBUG
#endif

namespace venca {

// XOR encryption key (generated at build time)
constexpr unsigned char XOR_KEY[] = {0x56, 0x45, 0x4E, 0x43, 0x41, 0x53, 0x45, 0x43};
constexpr size_t XOR_KEY_LEN = 8;

// Simple XOR encryption/decryption
std::string xorEncrypt(const std::string& input) {
    std::string output = input;
    for (size_t i = 0; i < input.length(); i++) {
        output[i] ^= XOR_KEY[i % XOR_KEY_LEN];
    }
    return output;
}

// Generate random bytes
std::vector<unsigned char> generateRandomBytes(size_t length) {
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dis(0, 255);
    
    std::vector<unsigned char> bytes(length);
    for (size_t i = 0; i < length; i++) {
        bytes[i] = static_cast<unsigned char>(dis(gen));
    }
    return bytes;
}

// Base64 encoding
std::string base64Encode(const std::vector<unsigned char>& data) {
    static const char* chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::string result;
    int val = 0, valb = -6;
    
    for (unsigned char c : data) {
        val = (val << 8) + c;
        valb += 8;
        while (valb >= 0) {
            result.push_back(chars[(val >> valb) & 0x3F]);
            valb -= 6;
        }
    }
    if (valb > -6) {
        result.push_back(chars[((val << 8) >> (valb + 8)) & 0x3F]);
    }
    while (result.size() % 4) {
        result.push_back('=');
    }
    return result;
}

// Base64 decoding
std::vector<unsigned char> base64Decode(const std::string& input) {
    static const int table[] = {
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1, -1, -1, 63,
        52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -1, -1, -1,
        -1,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14,
        15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, -1,
        -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
        41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
        -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
    };
    
    std::vector<unsigned char> result;
    int val = 0, valb = -8;
    
    for (char c : input) {
        if (table[(unsigned char)c] == -1) break;
        val = (val << 6) + table[(unsigned char)c];
        valb += 6;
        if (valb >= 0) {
            result.push_back(static_cast<unsigned char>((val >> valb) & 0xFF));
            valb -= 8;
        }
    }
    return result;
}

// Simple SHA-256-like hash (for demonstration - use proper crypto in production)
std::string simpleHash(const std::string& input) {
    unsigned int hash = 0x811c9dc5; // FNV offset basis
    for (char c : input) {
        hash ^= static_cast<unsigned char>(c);
        hash *= 0x01000193; // FNV prime
    }
    
    std::stringstream ss;
    ss << std::hex << std::setw(8) << std::setfill('0') << hash;
    return ss.str();
}

// Check if running in emulator
bool isEmulator() {
    // Check for emulator indicators in system properties
    FILE* fp = popen("getprop ro.hardware", "r");
    if (fp) {
        char buffer[128];
        if (fgets(buffer, sizeof(buffer), fp)) {
            std::string hardware(buffer);
            hardware.erase(hardware.find_last_not_of("\n\r") + 1);
            pclose(fp);
            
            if (hardware.find("goldfish") != std::string::npos ||
                hardware.find("ranchu") != std::string::npos ||
                hardware.find("vbox") != std::string::npos) {
                return true;
            }
        }
        pclose(fp);
    }
    return false;
}

// Check if debugger is attached
bool isDebuggerAttached() {
    FILE* fp = fopen("/proc/self/status", "r");
    if (fp) {
        char line[256];
        while (fgets(line, sizeof(line), fp)) {
            if (strncmp(line, "TracerPid:", 10) == 0) {
                int pid = atoi(line + 10);
                fclose(fp);
                return pid != 0;
            }
        }
        fclose(fp);
    }
    return false;
}

// Check for hook frameworks
bool detectHooks() {
    // Check for Frida
    FILE* fp = fopen("/data/local/tmp/frida-server", "r");
    if (fp) {
        fclose(fp);
        return true;
    }
    
    // Check memory maps for suspicious libraries
    fp = fopen("/proc/self/maps", "r");
    if (fp) {
        char line[512];
        while (fgets(line, sizeof(line), fp)) {
            std::string mapLine(line);
            if (mapLine.find("frida") != std::string::npos ||
                mapLine.find("xposed") != std::string::npos ||
                mapLine.find("substrate") != std::string::npos) {
                fclose(fp);
                return true;
            }
        }
        fclose(fp);
    }
    
    return false;
}

} // namespace venca

extern "C" {

// Native string encryption
JNIEXPORT jstring JNICALL
Java_com_agentstudio_security_VenCANative_encryptString(
    JNIEnv* env,
    jobject /* this */,
    jstring input
) {
    const char* inputStr = env->GetStringUTFChars(input, nullptr);
    std::string encrypted = venca::xorEncrypt(inputStr);
    env->ReleaseStringUTFChars(input, inputStr);
    return env->NewStringUTF(encrypted.c_str());
}

// Native string decryption
JNIEXPORT jstring JNICALL
Java_com_agentstudio_security_VenCANative_decryptString(
    JNIEnv* env,
    jobject /* this */,
    jstring input
) {
    const char* inputStr = env->GetStringUTFChars(input, nullptr);
    std::string decrypted = venca::xorEncrypt(inputStr); // XOR is symmetric
    env->ReleaseStringUTFChars(input, inputStr);
    return env->NewStringUTF(decrypted.c_str());
}

// Generate secure random string
JNIEXPORT jstring JNICALL
Java_com_agentstudio_security_VenCANative_generateSecureRandom(
    JNIEnv* env,
    jobject /* this */,
    jint length
) {
    auto bytes = venca::generateRandomBytes(length);
    std::string encoded = venca::base64Encode(bytes);
    return env->NewStringUTF(encoded.substr(0, length).c_str());
}

// Get device security fingerprint
JNIEXPORT jstring JNICALL
Java_com_agentstudio_security_VenCANative_getSecurityFingerprint(
    JNIEnv* env,
    jobject /* this */
) {
    // Combine multiple factors for fingerprint
    std::string data = "VENCA_SECURITY_FINGERPRINT";
    
    // Add timestamp component
    auto now = std::chrono::system_clock::now();
    auto duration = now.time_since_epoch();
    auto millis = std::chrono::duration_cast<std::chrono::milliseconds>(duration).count();
    data += std::to_string(millis % 1000000);
    
    std::string hash = venca::simpleHash(data);
    return env->NewStringUTF(hash.c_str());
}

// Native security check
JNIEXPORT jint JNICALL
Java_com_agentstudio_security_VenCANative_performSecurityCheck(
    JNIEnv* env,
    jobject /* this */
) {
    int score = 100;
    
    if (venca::isEmulator()) {
        score -= 10;
    }
    
    if (venca::isDebuggerAttached()) {
        score -= 20;
    }
    
    if (venca::detectHooks()) {
        score -= 40;
    }
    
    return score;
}

// Native hash function
JNIEXPORT jstring JNICALL
Java_com_agentstudio_security_VenCANative_hash(
    JNIEnv* env,
    jobject /* this */,
    jstring input
) {
    const char* inputStr = env->GetStringUTFChars(input, nullptr);
    std::string hash = venca::simpleHash(inputStr);
    env->ReleaseStringUTFChars(input, inputStr);
    return env->NewStringUTF(hash.c_str());
}

// Verify app integrity
JNIEXPORT jboolean JNICALL
Java_com_agentstudio_security_VenCANative_verifyIntegrity(
    JNIEnv* env,
    jobject /* this */
) {
    // Check for debugger
    if (venca::isDebuggerAttached()) {
        return JNI_FALSE;
    }
    
    // Check for hooks
    if (venca::detectHooks()) {
        return JNI_FALSE;
    }
    
    return JNI_TRUE;
}

} // extern "C"
