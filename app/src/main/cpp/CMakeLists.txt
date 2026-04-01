cmake_minimum_required(VERSION 3.22.1)
project(llama-android)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp source directory (will be downloaded by GitHub Actions)
set(LLAMA_CPP_DIR ${CMAKE_SOURCE_DIR}/llama.cpp)

# Optimization flags
set(CMAKE_C_FLAGS_RELEASE "-O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG")

# Platform-specific flags for Android
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8.4-a+dotprod+fp16v")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.4-a+dotprod+fp16v")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv7-a -mfpu=neon -mfloat-abi=softfp")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv7-a -mfpu=neon -mfloat-abi=softfp")
endif()

# Define llama.cpp sources based on latest structure
set(LLAMA_SOURCES
    # Core llama sources
    ${LLAMA_CPP_DIR}/src/llama.cpp
    ${LLAMA_CPP_DIR}/src/unicode.cpp
    ${LLAMA_CPP_DIR}/src/unicode-data.cpp
)

# GGML sources (new structure)
set(GGML_SOURCES
    ${LLAMA_CPP_DIR}/ggml/src/ggml.c
    ${LLAMA_CPP_DIR}/ggml/src/ggml-alloc.c
    ${LLAMA_CPP_DIR}/ggml/src/ggml-backend.cpp
    ${LLAMA_CPP_DIR}/ggml/src/ggml-quants.c
)

# CPU backend sources
set(CPU_SOURCES
    ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/ggml-cpu.cpp
    ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/ggml-cpu.c
)

# Platform-specific optimizations
if(ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "armeabi-v7a")
    list(APPEND CPU_SOURCES
        ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu/arch/arm/neon.c
    )
endif()

# Combine all sources
set(ALL_SOURCES ${LLAMA_SOURCES} ${GGML_SOURCES} ${CPU_SOURCES})

# Create static library for llama.cpp
add_library(llama STATIC ${ALL_SOURCES})

target_include_directories(llama PUBLIC
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/src
    ${LLAMA_CPP_DIR}/ggml/include
    ${LLAMA_CPP_DIR}/ggml/src
    ${LLAMA_CPP_DIR}/ggml/src/ggml-cpu
)

target_compile_definitions(llama PUBLIC
    GGML_USE_CPU
    GGML_USE_NEON
    __ARM_NEON
    __ARM_FEATURE_FMA
    __ARM_FEATURE_DOTPROD
)

# Link libraries for llama
target_link_libraries(llama
    log
    android
)

# Create our JNI library
add_library(llama-android SHARED
    llama-android.cpp
)

target_include_directories(llama-android PRIVATE
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/src
    ${LLAMA_CPP_DIR}/ggml/include
    ${LLAMA_CPP_DIR}/ggml/src
)

# Link with llama static library
target_link_libraries(llama-android
    llama
    log
    android
)

# Export library
set_target_properties(llama-android PROPERTIES
    OUTPUT_NAME "llama-android"
)
