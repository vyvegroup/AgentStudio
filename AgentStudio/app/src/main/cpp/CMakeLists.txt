cmake_minimum_required(VERSION 3.22.1)
project("llama-android")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp source directory
set(LLAMA_DIR ${CMAKE_SOURCE_DIR}/llama.cpp)

# Check if llama.cpp exists
if(NOT EXISTS ${LLAMA_DIR}/CMakeLists.txt)
    message(WARNING "llama.cpp not found at ${LLAMA_DIR}.")
    message(WARNING "Native inference will be disabled.")
    message(WARNING "To enable native inference:")
    message(WARNING "  cd app/src/main/cpp")
    message(WARNING "  git clone --depth 1 https://github.com/ggerganov/llama.cpp.git")
    # Create an empty library so the build doesn't fail
    add_library(llama-android SHARED empty.cpp)
    target_link_libraries(llama-android log)
    return()
endif()

# Compile flags for Android
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -fPIC -fvisibility=hidden")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -fPIC -fvisibility=hidden")

# llama.cpp options - build as static library
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CUBLAS OFF CACHE BOOL "" FORCE)
set(GGML_OPENBLAS OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)  # Build static libs
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_STATIC ON CACHE BOOL "" FORCE)

# Add llama.cpp subdirectory
add_subdirectory(${LLAMA_DIR} llama_build EXCLUDE_FROM_ALL)

# Our JNI library
add_library(llama-android SHARED llama-android.cpp)

# Include directories
target_include_directories(llama-android PRIVATE
    ${LLAMA_DIR}
    ${LLAMA_DIR}/common
    ${LLAMA_DIR}/src
    ${CMAKE_SOURCE_DIR}
)

# Link with static llama library and log/android
target_link_libraries(llama-android 
    llama  # This will link statically
    log 
    android
)

# Define for Android
target_compile_definitions(llama-android PRIVATE GGML_USE_CPU ANDROID LLAMA_CPP_AVAILABLE)
