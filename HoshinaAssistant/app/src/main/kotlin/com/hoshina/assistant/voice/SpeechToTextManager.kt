package com.hoshina.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.hoshina.assistant.R

class SpeechToTextManager(
    private val context: Context,
    private val onPartialResult: (String) -> Unit = {},
    private val onFinalResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit = {},
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            isListening = false
            onListeningChanged(false)
        }

        override fun onError(error: Int) {
            isListening = false
            onListeningChanged(false)

            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> context.getString(R.string.stt_error_audio)
                SpeechRecognizer.ERROR_CLIENT -> context.getString(R.string.stt_error_client)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    context.getString(R.string.stt_error_permissions)
                SpeechRecognizer.ERROR_NETWORK -> context.getString(R.string.stt_error_network)
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    context.getString(R.string.stt_error_network_timeout)
                SpeechRecognizer.ERROR_NO_MATCH -> context.getString(R.string.stt_error_no_match)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> context.getString(R.string.stt_error_busy)
                SpeechRecognizer.ERROR_SERVER -> context.getString(R.string.stt_error_server)
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    context.getString(R.string.stt_error_speech_timeout)
                else -> context.getString(R.string.stt_error_unknown)
            }

            if (error != SpeechRecognizer.ERROR_NO_MATCH &&
                error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            ) {
                onError(message)
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            onListeningChanged(false)

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim().orEmpty()

            if (text.isNotEmpty()) {
                onFinalResult(text)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim().orEmpty()

            if (text.isNotEmpty()) {
                onPartialResult(text)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    init {
        val appContext = context.applicationContext
        speechRecognizer = if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
            try {
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    null
                } else {
                    SpeechRecognizer.createSpeechRecognizer(appContext).also { recognizer ->
                        recognizer.setRecognitionListener(recognitionListener)
                    }
                }
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalStateException) {
                null
            } catch (_: RuntimeException) {
                null
            }
        } else {
            null
        }
    }

    fun startListening() {
        if (!isAvailable || isListening) return
        speechRecognizer ?: return   // ✅ 修正：避免 null crash

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1_200L)
        }

        isListening = true
        onListeningChanged(true)

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        if (!isListening) return
        speechRecognizer?.stopListening()

        // ✅ 修正：避免 state 卡住
        isListening = false
        onListeningChanged(false)
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
    }
}
