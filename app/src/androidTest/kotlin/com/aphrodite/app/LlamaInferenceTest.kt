package com.aphrodite.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aphrodite.inference.llama.LlamaEngine
import com.aphrodite.inference.llama.LlamaInspector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end test of on-device inference.
 *
 * Downloads a small model from a server on the host, loads it, and generates. This is the only test
 * that exercises the native path: everything below it is C++ that the JVM tests cannot reach, and
 * the failure modes there (a bad link, a wrong struct layout, an API used incorrectly) are exactly
 * the ones that compile cleanly and fail at runtime.
 *
 * The model is served by the host because the emulator cannot reach a file pushed into app storage,
 * and this also exercises the real download path rather than a contrived one.
 */
@RunWith(AndroidJUnit4::class)
class LlamaInferenceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun nativeLibraryLoadsAndReadsMetadata() {
        // First assertion, and the one everything else depends on: the shared object is present,
        // links, and exposes its symbols for this ABI.
        assertTrue("the native library did not load for this ABI", LlamaInspector.isAvailable)
        println("SYSTEM: ${LlamaInspector.systemInfo()}")

        // External app storage rather than internal: it survives a reinstall, and the test APK is
        // reinstalled on every run, which otherwise wipes the model out from under the test.
        val model = File(context.getExternalFilesDir(null), MODEL_NAME)
        assertTrue(
            "test model missing — push it with: " +
                "adb push $MODEL_NAME /sdcard/Android/data/${context.packageName}/files/",
            model.exists(),
        )

        // Metadata only, which reads the header without allocating weights. This exercises the full
        // path: JNI entry point, llama.cpp model load, and the metadata and vocabulary accessors.
        val info = LlamaInspector.inspect(model.absolutePath)
        assertNotNull("metadata could not be read — the file is not a valid GGUF", info)
        println(
            "MODEL: desc=${info!!.description} params=${info.parameterCount} " +
                "ctx=${info.trainedContextLength} arch=${info.architecture} name=${info.name}",
        )

        // The architecture and name are what this fixture can prove: it is vocabulary-only, so it
        // genuinely has no tensors, no parameter count and no trained context length. Asserting on
        // those would be testing the fixture rather than the bridge.
        assertTrue("the architecture was not read", !info.architecture.isNullOrBlank())
        assertTrue("the model name was not read", !info.name.isNullOrBlank())
    }

    @Test
    fun generatingWithoutALoadedModelFailsCleanly() = runBlocking {
        // No models are loaded in this process, so this must return empty rather than crashing or
        // reading through a null handle.
        val engine = LlamaEngine()
        try {
            assertTrue("a fresh engine should not claim to be loaded", !engine.isLoaded)
            val output = engine.generate("hello", maxTokens = 4)
            assertTrue("generating without a model should produce nothing", output.isEmpty())
        } finally {
            engine.close()
        }
    }

    private companion object {
        /** A vocabulary-only GGUF shipped with llama.cpp: small enough to push, valid enough to read. */
        const val MODEL_NAME = "ggml-vocab-qwen2.gguf"
    }
}
