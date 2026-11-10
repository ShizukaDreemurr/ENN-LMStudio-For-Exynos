cmake_minimum_required(VERSION 3.22)
project(llama_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# llama.cpp release tag this bridge was written against. include/llama.h of THIS tag is the API reference for llama_jni.cpp.
set(LLAMA_CPP_TAG "b12257" CACHE STRING "llama.cpp release tag")

# Gradle debug builds would otherwise compile ggml at -O0 (unusably slow); always optimise the fetched sources.
foreach(lang C CXX)
    set(CMAKE_${lang}_FLAGS_DEBUG "-O3 -DNDEBUG")
    set(CMAKE_${lang}_FLAGS_RELWITHDEBINFO "-O3 -DNDEBUG")
endforeach()

# ---- llama.cpp build configuration: static, CPU backend only ----
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_UI       OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL        OFF CACHE BOOL "" FORCE)

set(GGML_VULKAN  OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL  OFF CACHE BOOL "" FORCE)
set(GGML_METAL   OFF CACHE BOOL "" FORCE)
set(GGML_CUDA    OFF CACHE BOOL "" FORCE)
set(GGML_BLAS    OFF CACHE BOOL "" FORCE)
set(GGML_RPC     OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE  OFF CACHE BOOL "" FORCE)
# OpenMP would add a libomp.so runtime dependency; ggml's own thread pool is used instead.
set(GGML_OPENMP  OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)

# Cross-compiling: no -mcpu=native. Fixed target = ARMv8.2-A + dot-product + int8 matmul (+fp16), which the Exynos 2600
# (Cortex-A725/X925 class cores, ARMv9.2) supports. This binary will SIGILL on CPUs without i8mm (e.g. Snapdragon 865, Exynos 990).
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+i8mm+fp16" CACHE STRING "" FORCE)

include(FetchContent)
FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG        ${LLAMA_CPP_TAG}
    GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(llama_cpp)

add_library(llama_jni SHARED llama_jni.cpp)
target_link_libraries(llama_jni PRIVATE llama log)
# Android 15+ 16 KB page-size devices: align ELF segments (NDK r27 defaults to 4 KB).
target_link_options(llama_jni PRIVATE -Wl,-z,max-page-size=16384)
