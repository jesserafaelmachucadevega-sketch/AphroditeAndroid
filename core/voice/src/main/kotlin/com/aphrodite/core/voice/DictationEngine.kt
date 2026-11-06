package com.aphrodite.core.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** What dictation reports back as it listens. */
sealed interface DictationEvent {
    /** Text so far, still subject to revision. */
    data class Partial(val text: String) : DictationEvent

    /** The recogniser's final answer. */
    data class Final(val text: String) : DictationEvent

    /** An error the user can act on, already phrased for them. */
    data class Failed(val message: String) : DictationEvent
}

/**
 * Dictation through the platform speech recogniser.
 *
 * Like the text-to-speech side, this is the framework rather than an engine: accuracy comes from
 * whichever recogniser the device has, which on most phones is the best available.
 *
 * Worth knowing: this commonly sends audio to a remote service. That is the platform's behaviour,
 * not a choice made here, and it is the reason someone might prefer an on-device model instead.
 */
@Singleton
class DictationEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val main = Handler(Looper.getMainLooper())

    /** True when a recogniser is installed at all. Some devices and ROMs have none. */
    val available: Boolean
        get() = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

    val hasPermission: Boolean
        get() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Listens until the flow is cancelled.
     *
     * A partial result is emitted on every revision so the text appears as the user speaks. Waiting
     * for the final result instead makes dictation feel broken on anything longer than one sentence.
     *
     * The recogniser is created and driven on the main thread because the platform requires it, and
     * destroyed in [awaitClose] — a recogniser left alive holds the microphone.
     */
    fun listen(locale: Locale = Locale.getDefault()): Flow<DictationEvent> = callbackFlow {
        if (!available) {
            trySend(DictationEvent.Failed("No speech recogniser is installed on this device."))
            close()
            return@callbackFlow
        }
        if (!hasPermission) {
            trySend(DictationEvent.Failed("Microphone permission is needed to dictate."))
            close()
            return@callbackFlow
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        var recognizer: SpeechRecognizer? = null
        main.post {
            runCatching {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) = Unit
                        override fun onBeginningOfSpeech() = Unit
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() = Unit
                        override fun onEvent(eventType: Int, params: Bundle?) = Unit

                        override fun onPartialResults(partialResults: Bundle?) {
                            partialResults.firstResult()?.let { trySend(DictationEvent.Partial(it)) }
                        }

                        override fun onResults(results: Bundle?) {
                            results.firstResult()?.let { trySend(DictationEvent.Final(it)) }
                            close()
                        }

                        override fun onError(error: Int) {
                            trySend(DictationEvent.Failed(describe(error)))
                            close()
                        }
                    })
                    startListening(intent)
                }
            }.onFailure { error ->
                trySend(DictationEvent.Failed(error.message ?: "Dictation could not start."))
                close()
            }
        }

        awaitClose {
            main.post {
                runCatching {
                    recognizer?.stopListening()
                    recognizer?.destroy()
                }
                recognizer = null
            }
        }
    }

    private fun Bundle?.firstResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }

    /** Platform errors are integers; these are the ones a user can actually do something about. */
    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "The microphone could not be read. Another app may be using it."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission was denied."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        -> "The recogniser needs a network connection and could not reach one."
        SpeechRecognizer.ERROR_NO_MATCH -> "Nothing was recognised. Try speaking again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The recogniser is busy. Try again in a moment."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was heard."
        else -> "Dictation failed (code $error)."
    }
}
