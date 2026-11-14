package com.example.proteleprompter.hardware

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.Normalizer
import java.util.Locale

class VoiceScrollController(
    private val context: Context,
    private val onTargetProgressChanged: (Float) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningRequested = false
    private var isRestartScheduled = false
    private var indexedWords: List<IndexedWord> = emptyList()
    private var currentWordCursor = 0

    private val _lastHeardPhrase = MutableStateFlow("")
    val lastHeardPhrase: StateFlow<String> = _lastHeardPhrase.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val recognitionIntent: Intent by lazy {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.forLanguageTag("pt-BR").toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra("android.speech.extra.DICTATION_MODE", true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putExtra(
                    RecognizerIntent.EXTRA_ENABLE_FORMATTING,
                    RecognizerIntent.FORMATTING_OPTIMIZE_LATENCY
                )
            }
        }
    }

    data class IndexedWord(
        val normalized: String,
        val progressFraction: Float
    )

    fun prepareScript(rawContent: String) {
        val cleanWords = Regex("""[\p{L}\p{N}]{2,}""").findAll(rawContent).toList()
        val totalLen = rawContent.length.coerceAtLeast(1)
        indexedWords = cleanWords.map { match ->
            IndexedWord(
                normalized = normalizeWord(match.value),
                progressFraction = (match.range.first.toFloat() / totalLen.toFloat()).coerceIn(0f, 1f)
            )
        }
        currentWordCursor = 0
    }

    fun updateCursorFromScroll(scrollFraction: Float) {
        if (indexedWords.isEmpty()) return
        val idx = indexedWords.indexOfFirst { it.progressFraction >= scrollFraction }
        if (idx >= 0) {
            currentWordCursor = idx
        }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _lastHeardPhrase.value = "Reconhecimento de voz indisponível neste dispositivo"
            return
        }
        isListeningRequested = true
        isRestartScheduled = false
        mainHandler.post {
            ensureRecognizerCreated()
            startListeningImmediate()
        }
    }

    fun stopListening() {
        isListeningRequested = false
        isRestartScheduled = false
        _isListening.value = false
        mainHandler.removeCallbacksAndMessages(null)
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
    }

    private fun ensureRecognizerCreated() {
        if (speechRecognizer != null) return
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isRestartScheduled = false
                _isListening.value = true
            }

            override fun onBeginningOfSpeech() {
                _isListening.value = true
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                if (!isListeningRequested) return
                if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    recreateRecognizerAndListen(60L)
                } else {
                    scheduleImmediateListen(15L)
                }
            }

            override fun onResults(results: Bundle?) {
                processBundle(results)
                if (isListeningRequested) {
                    scheduleImmediateListen(10L)
                }
            }

            override fun onSegmentResults(segmentResults: Bundle) {
                processBundle(segmentResults)
            }

            override fun onEndOfSegmentedSession() {
                if (isListeningRequested) {
                    scheduleImmediateListen(10L)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                processBundle(partialResults)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer = recognizer
    }

    private fun startListeningImmediate() {
        if (!isListeningRequested) return
        try {
            ensureRecognizerCreated()
            speechRecognizer?.startListening(recognitionIntent)
        } catch (_: Exception) {
            recreateRecognizerAndListen(80L)
        }
    }

    private fun scheduleImmediateListen(delayMs: Long) {
        if (!isListeningRequested || isRestartScheduled) return
        isRestartScheduled = true
        mainHandler.postDelayed({
            isRestartScheduled = false
            if (isListeningRequested) {
                startListeningImmediate()
            }
        }, delayMs)
    }

    private fun recreateRecognizerAndListen(delayMs: Long) {
        if (!isListeningRequested) return
        mainHandler.removeCallbacksAndMessages(null)
        isRestartScheduled = true
        mainHandler.postDelayed({
            isRestartScheduled = false
            if (!isListeningRequested) return@postDelayed
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            }
            speechRecognizer = null
            ensureRecognizerCreated()
            startListeningImmediate()
        }, delayMs)
    }

    private fun processBundle(bundle: Bundle?) {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val phrase = matches?.firstOrNull()?.trim().orEmpty()
        if (phrase.isEmpty()) return

        _lastHeardPhrase.value = phrase
        val spokenTokens = phrase
            .split(Regex("""\s+"""))
            .map { normalizeWord(it) }
            .filter { it.length >= 2 }

        if (spokenTokens.isEmpty() || indexedWords.isEmpty()) return

        val lastTokens = spokenTokens.takeLast(3)
        val searchStart = (currentWordCursor - 3).coerceAtLeast(0)
        val searchEnd = (currentWordCursor + 36).coerceAtMost(indexedWords.lastIndex)

        var bestMatchIndex = -1
        for (i in searchStart..searchEnd) {
            val candidate = indexedWords[i].normalized
            if (lastTokens.any { token -> candidate == token || (token.length >= 4 && candidate.startsWith(token)) }) {
                bestMatchIndex = i
            }
        }

        if (bestMatchIndex > currentWordCursor) {
            currentWordCursor = bestMatchIndex
            onTargetProgressChanged(indexedWords[bestMatchIndex].progressFraction)
        }
    }

    private fun normalizeWord(input: String): String {
        val decomposed = Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")
            .replace(Regex("""[^\p{L}\p{N}]"""), "")
    }
}
