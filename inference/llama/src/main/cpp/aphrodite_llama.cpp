#include <jni.h>
#include <string>
#include <android/log.h>
#include "llama.h"

#define LOG_TAG "AphroditeLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {
    bool g_initialised = false;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_aphrodite_inference_llama_LlamaNative_initialize(JNIEnv *, jclass) {
    if (g_initialised) return JNI_TRUE;
    // Registers the available CPU backends. Must happen before any model is loaded, and doing it
    // twice aborts the process.
    llama_backend_init();
    g_initialised = true;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_aphrodite_inference_llama_LlamaNative_systemInfo(JNIEnv *env, jclass) {
    const char *info = llama_print_system_info();
    return env->NewStringUTF(info ? info : "");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_aphrodite_inference_llama_LlamaNative_version(JNIEnv *env, jclass) {
    return env->NewStringUTF("llama.cpp 67107ac");
}

extern "C" JNIEXPORT void JNICALL
Java_com_aphrodite_inference_llama_LlamaNative_shutdown(JNIEnv *, jclass) {
    if (!g_initialised) return;
    llama_backend_free();
    g_initialised = false;
}
