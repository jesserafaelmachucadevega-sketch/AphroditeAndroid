package com.aphrodite.feature.speech

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aphrodite.core.datastore.PreferencesRepository
import com.aphrodite.core.voice.DictationEngine
import com.aphrodite.core.voice.DictationEvent
import com.aphrodite.core.voice.TextToSpeechEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/** One selectable voice, flattened for display. */
data class VoiceOption(
    val name: String,
    val label: String,
    val quality: Int,
    val offline: Boolean,
)

data class SpeechUiState(
    val ready: Boolean = false,
    val speakReplies: Boolean = false,
    val voices: List<VoiceOption> = emptyList(),
    val selectedVoice: String? = null,
    val rate: Float = 1f,
    val pitch: Float = 1f,
    val recogniserAvailable: Boolean = false,
    val hasMicPermission: Boolean = false,
    val dictationInProgress: Boolean = false,
    val dictationText: String = "",
    val error: String? = null,
) {
    /** True when the engine offers more than one voice worth choosing between. */
    val hasVoiceChoice: Boolean get() = voices.size > 1
}

/**
 * Speech settings.
 *
 * Everything here is the platform's own speech stack. What the screen adds is the ability to pick a
 * better voice than the device defaults to, which is the usual reason a phone sounds robotic.
 */
@HiltViewModel
class SpeechViewModel @Inject constructor(
    private val preferences: PreferencesRepository,
    private val tts: TextToSpeechEngine,
    private val dictation: DictationEngine,
) : ViewModel() {

    private val _state = MutableStateFlow(SpeechUiState())
    val state: StateFlow<SpeechUiState> = _state.asStateFlow()

    private var dictationJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            val saved = preferences.preferences.first()
            apply(saved.speakReplies, saved.speechVoice, saved.speechRate, saved.speechPitch)
            tts.selectVoice(saved.speechVoice)
            tts.configure(saved.speechRate, saved.speechPitch)
        }
        // The engine initialises asynchronously, so readiness arrives after the screen is built.
        viewModelScope.launch {
            tts.ready.collect { ready ->
                _state.update { it.copy(ready = ready) }
                if (ready) refreshVoices()
            }
        }
        _state.update {
            it.copy(
                recogniserAvailable = dictation.available,
                hasMicPermission = dictation.hasPermission,
            )
        }
    }

    private fun apply(speakReplies: Boolean, voice: String?, rate: Float, pitch: Float) {
        _state.update {
            it.copy(
                speakReplies = speakReplies,
                selectedVoice = voice,
                rate = rate,
                pitch = pitch,
            )
        }
    }

    private fun refreshVoices() {
        val language = Locale.getDefault().language
        _state.update { current ->
            current.copy(
                voices = tts.voices(language).map { voice ->
                    VoiceOption(
                        name = voice.name,
                        label = prettify(voice.name),
                        quality = voice.quality,
                        // A network voice needs a connection and may bill; worth marking.
                        offline = !voice.isNetworkConnectionRequired,
                    )
                },
                // With nothing saved, report what the engine actually chose so the list is not
                // showing no selection at all.
                selectedVoice = current.selectedVoice ?: tts.currentVoice()?.name,
            )
        }
    }

    /** Engine voice names are identifiers like `en-us-x-sfg#female_1-local`. */
    private fun prettify(name: String): String = name
        .substringAfterLast('#', name)
        .removeSuffix("-local")
        .removeSuffix("-network")
        .replace('_', ' ')
        .trim()

    fun onSpeakRepliesChange(enabled: Boolean) {
        _state.update { it.copy(speakReplies = enabled) }
        viewModelScope.launch { preferences.setSpeakReplies(enabled) }
    }

    fun onVoiceSelected(name: String) {
        _state.update { it.copy(selectedVoice = name) }
        tts.selectVoice(name)
        viewModelScope.launch { preferences.setSpeechVoice(name) }
    }

    fun onRateChange(rate: Float) {
        _state.update { it.copy(rate = rate) }
        tts.configure(rate, _state.value.pitch)
        viewModelScope.launch { preferences.setSpeechRate(rate) }
    }

    fun onPitchChange(pitch: Float) {
        _state.update { it.copy(pitch = pitch) }
        tts.configure(_state.value.rate, pitch)
        viewModelScope.launch { preferences.setSpeechPitch(pitch) }
    }

    fun preview() {
        tts.speak(PREVIEW_TEXT)
    }

    fun stopSpeaking() = tts.stop()

    fun toggleDictation() {
        if (_state.value.dictationInProgress) {
            dictationJob?.cancel()
            _state.update { it.copy(dictationInProgress = false) }
            return
        }
        _state.update { it.copy(dictationInProgress = true, dictationText = "", error = null) }

        dictationJob = viewModelScope.launch {
            try {
                dictation.listen(Locale.getDefault()).collect { event ->
                    when (event) {
                        is DictationEvent.Partial -> _state.update { it.copy(dictationText = event.text) }
                        is DictationEvent.Final -> _state.update {
                            it.copy(dictationText = event.text, dictationInProgress = false)
                        }
                        is DictationEvent.Failed -> _state.update {
                            it.copy(error = event.message, dictationInProgress = false)
                        }
                    }
                }
            } finally {
                _state.update { it.copy(dictationInProgress = false) }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private companion object {
        const val PREVIEW_TEXT =
            "This is how Aphrodite will read answers aloud. Adjust the rate and pitch until it sounds right."
    }
}
