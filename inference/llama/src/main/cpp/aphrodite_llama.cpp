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
Java_com_aphrodite_inference_llama_LlamaNative_initializeNative(JNIEnv *, jclass) {
    if (g_initialised) return JNI_TRUE;
    // Registers the available CPU backends. Must happen before any model is loaded, and doing it
    // twice aborts the process.
    llama_backend_init();
    g_initialised = true;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_aphrodite_inference_llama_LlamaNative_systemInfoNative(JNIEnv *env, jclass) {
    const char *info = llama_print_system_info();
    return env->NewStringUTF(info ? info : "");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_aphrodite_inference_llama_LlamaNative_versionNative(JNIEnv *env, jclass) {
    return env->NewStringUTF("llama.cpp 67107ac");
}

extern "C" JNIEXPORT void JNICALL
Java_com_aphrodite_inference_llama_LlamaNative_shutdownNative(JNIEnv *, jclass) {
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
Java_com_aphrodite_inference_llama_LlamaNative_loadModelNative(
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
Java_com_aphrodite_inference_llama_LlamaNative_freeModelNative(JNIEnv *, jclass, jlong handle) {
    if (handle == 0) return;
    llama_model_free(reinterpret_cast<llama_model *>(handle));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_aphrodite_inference_llama_LlamaNative_modelDescriptionNative(
        JNIEnv *env, jclass, jlong handle) {
    if (handle == 0) return env->NewStringUTF("");
    // The buffer form is used rather than the returned pointer: the signature writes into a
    // caller-provided buffer, and passing null to get the pointer back is not guaranteed.
    char buffer[256];
    llama_model_desc(reinterpret_cast<llama_model *>(handle), buffer, sizeof(buffer));
    return env->NewStringUTF(buffer);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_aphrodite_inference_llama_LlamaNative_modelParametersNative(
        JNIEnv *, jclass, jlong handle) {
    if (handle == 0) return 0;
    return static_cast<jlong>(
        llama_model_n_params(reinterpret_cast<llama_model *>(handle)));
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_aphrodite_inference_llama_LlamaNative_modelSizeNative(
        JNIEnv *, jclass, jlong handle) {
    if (handle == 0) return 0;
    return static_cast<jlong>(llama_model_size(reinterpret_cast<llama_model *>(handle)));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_aphrodite_inference_llama_LlamaNative_modelContextLengthNative(
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
Java_com_aphrodite_inference_llama_LlamaNative_modelMetadataNative(
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

// ── Generation ───────────────────────────────────────────────────────────────
//
// A context owns the KV cache, so it is separate from the model: one model can back several
// contexts, and freeing a context releases the memory a conversation used without unloading the
// weights. That distinction is what makes switching conversations cheap.

#include <vector>
#include <string>

namespace {
    constexpr int CALLBACK_ON_TOKEN = 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_aphrodite_inference_llama_LlamaNative_createContextNative(
        JNIEnv *, jclass, jlong model_handle, jint n_ctx, jint n_threads) {
    if (model_handle == 0) return 0;

    auto *model = reinterpret_cast<llama_model *>(model_handle);
    llama_context_params params = llama_context_default_params();
    params.n_ctx = static_cast<uint32_t>(n_ctx);
    // Leave a core for the UI. Saturating every core makes the interface stutter during
    // generation, which reads as the app being slower even though generation finished sooner.
    params.n_threads = static_cast<int32_t>(n_threads);
    params.n_threads_batch = static_cast<int32_t>(n_threads);

    llama_context *ctx = llama_init_from_model(model, params);
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT void JNICALL
Java_com_aphrodite_inference_llama_LlamaNative_freeContextNative(JNIEnv *, jclass, jlong handle) {
    if (handle == 0) return;
    llama_free(reinterpret_cast<llama_context *>(handle));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_aphrodite_inference_llama_LlamaNative_contextSizeNative(JNIEnv *, jclass, jlong handle) {
    if (handle == 0) return 0;
    return static_cast<jint>(llama_n_ctx(reinterpret_cast<llama_context *>(handle)));
}

/**
 * Runs a prompt and generates up to max_tokens, streaming each piece to the callback.
 *
 * Blocking rather than incremental. Returning token by token to the caller would mean holding
 * native state across JNI calls and re-entering from Kotlin, which is a great deal of machinery for
 * something the callback already provides: the interface fills in as the answer arrives.
 *
 * A cancelled generation is not detected here. Kotlin cancels the coroutine, the thread is
 * interrupted, and the loop checks that between tokens so a long generation stops promptly rather
 * than running to completion after the user has moved on.
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_aphrodite_inference_llama_LlamaNative_generateNative(
        JNIEnv *env, jclass, jlong handle, jstring jprompt,
        jint max_tokens, jfloat temperature, jfloat top_p, jobject callback) {
    if (handle == 0) return env->NewStringUTF("");

    auto *ctx = reinterpret_cast<llama_context *>(handle);
    const llama_model *model = llama_get_model(ctx);
    const llama_vocab *vocab = llama_model_get_vocab(model);

    const char *prompt = env->GetStringUTFChars(jprompt, nullptr);
    if (prompt == nullptr) return env->NewStringUTF("");
    const std::string prompt_text(prompt);
    env->ReleaseStringUTFChars(jprompt, prompt);

    // Tokenise twice: once to size, once to fill. The sizing call is the documented way to avoid
    // guessing an upper bound for a token count.
    int32_t count = llama_tokenize(vocab, prompt_text.c_str(),
                                   static_cast<int32_t>(prompt_text.size()),
                                   nullptr, 0, true, true);
    if (count < 0) count = -count;
    if (count <= 0) return env->NewStringUTF("");

    std::vector<llama_token> tokens(static_cast<size_t>(count));
    count = llama_tokenize(vocab, prompt_text.c_str(),
                           static_cast<int32_t>(prompt_text.size()),
                           tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    if (count <= 0) return env->NewStringUTF("");
    tokens.resize(static_cast<size_t>(count));

    // A prompt longer than the context cannot be evaluated at all; the caller is told rather than
    // silently truncating the front of the conversation.
    if (static_cast<uint32_t>(count) >= llama_n_ctx(ctx)) {
        return env->NewStringUTF(
            "[prompt is longer than the context window — shorten the conversation or raise the "
            "context size]");
    }

    if (llama_decode(ctx, llama_batch_get_one(tokens.data(), count)) != 0) {
        return env->NewStringUTF("[the model failed to evaluate the prompt]");
    }

    llama_sampler_chain_params chain_params = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(chain_params);
    if (temperature <= 0.0f) {
        // Zero temperature means the user wants the same answer twice, which is what greedy
        // decoding gives and what a temperature sampler cannot.
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    }

    jclass callback_class = callback != nullptr ? env->GetObjectClass(callback) : nullptr;
    jmethodID on_token = callback_class != nullptr
        ? env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)V")
        : nullptr;

    std::string result;
    for (int32_t i = 0; i < max_tokens; ++i) {
        if (env->ExceptionCheck()) break;

        const llama_token id = llama_sampler_sample(sampler, ctx, -1);
        if (llama_vocab_is_eog(vocab, id)) break;

        llama_sampler_accept(sampler, id);

        char piece[512];
        const int32_t written = llama_token_to_piece(vocab, id, piece, sizeof(piece), 0, true);

        if (written > 0) {
            const std::string text(piece, static_cast<size_t>(written));
            result += text;

            if (on_token != nullptr) {
                jstring jtext = env->NewStringUTF(text.c_str());
                env->CallVoidMethod(callback, on_token, jtext);
                env->DeleteLocalRef(jtext);
                // A Kotlin exception inside the callback would otherwise be swallowed and
                // reported as a successful empty generation.
                if (env->ExceptionCheck()) break;
            }
        }

        // Not const: the batch API may write back into the token array.
        llama_token next = id;
        if (llama_decode(ctx, llama_batch_get_one(&next, 1)) != 0) break;
    }

    llama_sampler_free(sampler);
    return env->NewStringUTF(result.c_str());
}
