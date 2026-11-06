package com.aphrodite.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads text aloud through the platform's text-to-speech engine.
 *
 * The engine here is a framework, not a voice: the audio comes from whichever engine app is
 * installed. That is deliberate — it means the user picks a voice they like in system settings
 * rather than us shipping one, and the voices on a modern device are already good.
 *
 * Initialisation is asynchronous, so the first [speak] can arrive before the engine is usable.
 * Rather than dropping it, the text is held and spoken once the engine reports ready. The very
 * first thing a user asks for is the most likely to be lost, which makes it the most important
 * one to keep.
 */
@Singleton
class TextToSpeechEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var engine: TextToSpeech? = null
    private var queued: String? = null

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    init {
        engine = TextToSpeech(context) { status ->
            _ready.value = status == TextToSpeech.SUCCESS
            if (_ready.value) {
                engine?.setOnUtteranceProgressListener(progress)
                queued?.let { text ->
                    queued = null
                    speakNow(text)
                }
            } else {
                queued = null
            }
        }
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            _speaking.value = true
        }

        override fun onDone(utteranceId: String?) {
            _speaking.value = false
        }

        @Deprecated("Required by the platform interface; not called on modern versions.")
        override fun onError(utteranceId: String?) {
            _speaking.value = false
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            _speaking.value = false
        }
    }

    /**
     * Voices for [language], best first.
     *
     * Quality descending rather than alphabetical: a device typically offers a dozen variants of
     * one language and the good ones are buried in the middle of that list.
     */
    fun voices(language: String = Locale.getDefault().language): List<Voice> = runCatching {
        engine?.voices
            ?.filter { it.locale.language.equals(language, ignoreCase = true) }
            ?.sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.name })
            .orEmpty()
    }.getOrDefault(emptyList())

    /** Every language the installed engine can speak, by display name. */
    fun languages(): List<Locale> = runCatching {
        engine?.availableLanguages?.sortedBy { it.displayName }.orEmpty()
    }.getOrDefault(emptyList())

    fun currentVoice(): Voice? = runCatching { engine?.voice }.getOrNull()

    fun selectVoice(voice: Voice) {
        engine?.voice = voice
    }

    /** Resolves a stored voice name, falling back to letting the engine choose. */
    fun selectVoice(name: String?) {
        if (name.isNullOrBlank()) return
        runCatching {
            engine?.voices?.firstOrNull { it.name == name }?.let { engine?.voice = it }
        }
    }

    fun configure(rate: Float, pitch: Float) {
        engine?.setSpeechRate(rate.coerceIn(0.25f, 3f))
        engine?.setPitch(pitch.coerceIn(0.5f, 2f))
    }

    fun speak(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (!_ready.value || engine == null) {
            queued = trimmed
            return
        }
        speakNow(trimmed)
    }

    /**
     * [TextToSpeech.QUEUE_FLUSH] rather than adding: reading a long answer and then tapping a
     * second one should replace it, not queue behind three minutes of audio.
     */
    private fun speakNow(text: String) {
        val tts = engine ?: run {
            queued = text
            return
        }
        // Long answers are split at sentence boundaries. Some engines truncate past a few thousand
        // characters, and losing the end of an answer is worse than a short pause between parts.
        val parts = text.chunked(MAX_UTTERANCE).flatMap { part ->
            if (part.length < MAX_UTTERANCE) {
                listOf(part)
            } else {
                splitAtSentence(part)
            }
        }
        parts.forEachIndexed { index, part ->
            tts.speak(part, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "aphrodite-$index")
        }
    }

    private fun splitAtSentence(text: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        text.split(Regex("(?<=[.!?])\\s+")).forEach { sentence ->
            if (current.length + sentence.length > MAX_UTTERANCE && current.isNotEmpty()) {
                out += current.toString().trim()
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(sentence)
        }
        if (current.isNotBlank()) out += current.toString().trim()
        return out.ifEmpty { listOf(text) }
    }

    fun stop() {
        queued = null
        _speaking.value = false
        runCatching { engine?.stop() }
    }

    private companion object {
        /** Comfortably under the limits engines impose on a single utterance. */
        const val MAX_UTTERANCE = 3000
    }
}
