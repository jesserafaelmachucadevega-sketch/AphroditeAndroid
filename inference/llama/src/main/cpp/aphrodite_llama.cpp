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

// ── Model inspection ─────────────────────────────────────────────────────────
//
// Loading with vocab_only reads the header and vocabulary without allocating the weights. A
// multi-gigabyte model can therefore be described in a fraction of a second, which is what makes
// reading real metadata practical instead of guessing from the file name.

extern "C" JNIEXPORT jlong JNICALL
Java_com_aphrodite_inference_llama_LlamaNative_loadModel(
        JNIEnv *env, jclass, jstring jpath, jboolean vocab_only) {
    if (!g_initialised) return 0;

    const char *path = env->GetStringUTFChars(jpath, nullptr);
    if (path == nullptr) return 0;

    llama_model_params params = llama_model_default_params();
    // Phones have no discrete GPU to offload to, and requesting layers there is meaningless.
    params.n_gpu_layers = 0;
    params.vocab_only = vocab_only == JNI_TRUE;

    llama_model *model = llama_model_load_from_file(path, params);

    env->ReleaseStringUTFChars(jpath, path);
    return reinterpret_cast<jlong>(model);
}

extern "C" JNIEXPORT void JNICALL
Java_com_aphrodite_inference_llama_LlamaNative_freeModel(JNIEnv *, jclass, jlong handle) {
    if (handle == 0) return;
    llama_model_free(reinterpret_cast<llama_model *>(handle));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_aphrodite_inference_llama_LlamaNative_modelDescription(
        JNIEnv *env, jclass, jlong handle) {
    if (handle == 0) return env->NewStringUTF("");
    // The buffer form is used rather than the returned pointer: the signature writes into a
    // caller-provided buffer, and passing null to get the pointer back is not guaranteed.
    char buffer[256];
    llama_model_desc(reinterpret_cast<llama_model *>(handle), buffer, sizeof(buffer));
    return env->NewStringUTF(buffer);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_aphrodite_inference_llama_LlamaNative_modelParameters(
        JNIEnv *, jclass, jlong handle) {
    if (handle == 0) return 0;
    return static_cast<jlong>(
        llama_model_n_params(reinterpret_cast<llama_model *>(handle)));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_aphrodite_inference_llama_LlamaNative_modelSize(
        JNIEnv *, jclass, jlong handle) {
    if (handle == 0) return 0;
    return static_cast<jlong>(llama_model_size(reinterpret_cast<llama_model *>(handle)));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_aphrodite_inference_llama_LlamaNative_modelContextLength(
        JNIEnv *, jclass, jlong handle) {
    if (handle == 0) return 0;
    return static_cast<jint>(
        llama_model_n_ctx_train(reinterpret_cast<llama_model *>(handle)));
}

/**
 * Reads one metadata value, returning null when the key is absent.
 *
 * Absent is the normal case rather than an error: which keys a GGUF file carries depends on the
 * converter and the architecture, so callers have to cope with missing values regardless.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_aphrodite_inference_llama_LlamaNative_modelMetadata(
        JNIEnv *env, jclass, jlong handle, jstring jkey) {
    if (handle == 0) return nullptr;

    const char *key = env->GetStringUTFChars(jkey, nullptr);
    if (key == nullptr) return nullptr;

    char buffer[1024];
    const int32_t written = llama_model_meta_val_str(
        reinterpret_cast<llama_model *>(handle), key, buffer, sizeof(buffer));

    env->ReleaseStringUTFChars(jkey, key);

    if (written < 0) return nullptr;
    return env->NewStringUTF(buffer);
}
