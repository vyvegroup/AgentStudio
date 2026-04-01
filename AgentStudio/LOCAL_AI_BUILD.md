# VenAI - Local AI Integration with llama.cpp

## Overview

VenAI now supports local AI inference using llama.cpp. This allows you to run AI models directly on your device without internet connection.

## Features

- **GGUF Model Support**: Load and run GGUF format models
- **Offline Inference**: No internet required for local AI
- **Model Download**: Download models directly from HuggingFace
- **Chat Interface**: Full chat experience with local models

## Building Native Library

### Prerequisites

1. **Android Studio** with NDK installed
2. **CMake** 3.22.1 or higher
3. **Android SDK** with Build Tools 34

### Option 1: Build with Android Studio

1. Open the project in Android Studio
2. Go to **SDK Manager** → **SDK Tools**
3. Install **NDK (Side by side)** version 27.x
4. Install **CMake**
5. Sync Project with Gradle Files
6. Build → Make Project

### Option 2: Build with Command Line

```bash
# Set Android NDK path
export ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/27.0.12077973

# Navigate to cpp directory
cd app/src/main/cpp

# Clone llama.cpp if not already present
if [ ! -d "llama.cpp" ]; then
    git clone --depth 1 https://github.com/ggerganov/llama.cpp.git
fi

# Build for arm64-v8a
mkdir -p build/arm64-v8a && cd build/arm64-v8a
cmake \
    -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-26 \
    -DANDROID_STL=c++_static \
    -DCMAKE_BUILD_TYPE=Release \
    -DLLAMA_NATIVE=OFF \
    -DLLAMA_CUBLAS=OFF \
    -DLLAMA_OPENBLAS=OFF \
    -DLLAMA_METAL=OFF \
    ../..
cmake --build . --config Release -j$(nproc)

# Copy to jniLibs
mkdir -p ../../../jniLibs/arm64-v8a
cp libllama-android.so ../../../jniLibs/arm64-v8a/
```

### Option 3: Use GitHub Actions

Push the code to GitHub and manually trigger the `Build Native Libraries` workflow.

1. Go to **Actions** tab in GitHub
2. Select **Build Native Libraries** workflow
3. Click **Run workflow**
4. Download the `native-libraries` artifact
5. Extract to `app/src/main/jniLibs/`

## Building APK

```bash
# Build release APK
./gradlew assembleRelease

# APK will be at:
# app/build/outputs/apk/release/app-release.apk
```

## Project Structure

```
app/src/main/
├── cpp/
│   ├── llama.cpp/          # llama.cpp source (git submodule)
│   ├── llama-android.cpp   # JNI implementation
│   ├── CMakeLists.txt      # CMake configuration
│   └── empty.cpp           # Fallback for no native build
├── jniLibs/
│   ├── arm64-v8a/
│   │   └── libllama-android.so
│   └── armeabi-v7a/
│       └── libllama-android.so
└── java/com/agentstudio/
    └── data/local/
        ├── LlamaJNI.kt         # JNI interface
        ├── LocalLLMEngine.kt   # Engine wrapper
        └── LocalModelManager.kt # Model download
```

## Supported Models

- **Gemma 3 4B VL** - Recommended for most devices
  - Size: ~4.5 GB
  - RAM Required: ~6 GB
  - URL: [HuggingFace](https://huggingface.co/Andycurrent/Gemma-3-4B-VL-it-Gemini-Pro-Heretic-Uncensored-Thinking_GGUF)

## Troubleshooting

### Native Library Not Loaded

If you see "Native library not loaded" error:

1. Ensure NDK is installed
2. Build the native library following the steps above
3. Check that `libllama-android.so` exists in `jniLibs/<arch>/`

### Model Loading Failed

If model loading fails:

1. Ensure model file is complete (check file size)
2. Ensure sufficient RAM is available
3. Try a smaller model if device has limited resources

### Build Errors

If you encounter build errors:

1. Clean the project: `./gradlew clean`
2. Invalidate caches: Android Studio → File → Invalidate Caches
3. Ensure correct NDK version is installed

## Performance Tips

1. **Use arm64-v8a builds** for better performance on modern devices
2. **Close other apps** to free up RAM
3. **Use quantized models** (Q4_K_M, Q5_K_M) for faster inference
4. **Reduce context size** in settings for lower memory usage

## License

- VenAI: MIT License
- llama.cpp: MIT License
