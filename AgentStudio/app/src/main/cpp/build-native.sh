#!/bin/bash
# Build llama.cpp for Android

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$(dirname "$(dirname "$SCRIPT_DIR")")")"
LLAMA_DIR="$SCRIPT_DIR/llama.cpp"
NDK_BUILD_DIR="$SCRIPT_DIR/build"

# Check for NDK
if [ -z "$ANDROID_NDK_HOME" ]; then
    if [ -d "$HOME/Android/Sdk/ndk" ]; then
        ANDROID_NDK_HOME=$(ls -d $HOME/Android/Sdk/ndk/*/ 2>/dev/null | head -1)
    fi
    if [ -z "$ANDROID_NDK_HOME" ]; then
        echo "ERROR: ANDROID_NDK_HOME not set and NDK not found"
        echo "Please set ANDROID_NDK_HOME or install NDK via Android Studio"
        exit 1
    fi
fi

echo "=== Building llama.cpp for Android ==="
echo "NDK: $ANDROID_NDK_HOME"
echo "llama.cpp: $LLAMA_DIR"

# Check if llama.cpp exists
if [ ! -d "$LLAMA_DIR" ]; then
    echo "ERROR: llama.cpp not found. Run ./download-llama.sh first"
    exit 1
fi

# ABI list
ABIS=("arm64-v8a" "armeabi-v7a")
if [ "$1" != "" ]; then
    ABIS=("$1")
fi

for ABI in "${ABIS[@]}"; do
    echo ""
    echo "=== Building for $ABI ==="
    
    BUILD_DIR="$NDK_BUILD_DIR/$ABI"
    mkdir -p "$BUILD_DIR"
    
    # Configure CMake
    cmake \
        -S "$SCRIPT_DIR" \
        -B "$BUILD_DIR" \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM=android-26 \
        -DANDROID_STL=c++_static \
        -DCMAKE_BUILD_TYPE=Release \
        -DLLAMA_NATIVE=OFF \
        -DLLAMA_CUBLAS=OFF \
        -DLLAMA_OPENBLAS=OFF \
        -DLLAMA_METAL=OFF
    
    # Build
    cmake --build "$BUILD_DIR" --config Release -j$(nproc)
    
    # Copy to jniLibs
    JNI_LIBS_DIR="$PROJECT_DIR/src/main/jniLibs/$ABI"
    mkdir -p "$JNI_LIBS_DIR"
    
    cp "$BUILD_DIR/libllama-android.so" "$JNI_LIBS_DIR/"
    
    echo "Copied to: $JNI_LIBS_DIR/libllama-android.so"
done

echo ""
echo "=== Build complete! ==="
echo "Native libraries are in: $PROJECT_DIR/src/main/jniLibs/"
