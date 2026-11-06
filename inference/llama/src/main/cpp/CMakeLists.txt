cmake_minimum_required(VERSION 3.22.1)
project(aphrodite_llama C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Everything llama.cpp ships for its own use is switched off. Only the inference library is wanted;
# examples, the test suite, the HTTP server and the CLI tools would each add build time and
# binaries to the APK for something the app never calls.
set(LLAMA_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON    OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL            OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP           OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE        OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS     OFF CACHE BOOL "" FORCE)

add_subdirectory(llama.cpp)

add_library(aphrodite_llama SHARED aphrodite_llama.cpp)

target_include_directories(aphrodite_llama PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include)

# Static linkage against llama.cpp: one .so rather than a handful, which keeps the APK's native
# payload to a single file per ABI.
target_link_libraries(aphrodite_llama PRIVATE llama android log)
