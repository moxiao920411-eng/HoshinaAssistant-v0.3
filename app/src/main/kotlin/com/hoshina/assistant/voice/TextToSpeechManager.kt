package com.hoshina.assistant.voice

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.hoshina.assistant.HoshinaApplication
import com.hoshina.assistant.data.remote.BackendUrlResolver
import com.hoshina.assistant.data.remote.RetrofitProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

class TextToSpeechManager(context: Context) {

    private val appContext = context.applicationContext
    private val settingsRepository = (appContext as? HoshinaApplication)?.settingsRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private var cloudJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var pendingText: String? = null
    private var pendingOnDone: (() -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        tts = TextToSpeech(context) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (isReady) {
                tts?.language = Locale.getDefault()
                val text = pendingText
                val callback = pendingOnDone
                pendingText = null
                pendingOnDone = null
                if (!text.isNullOrBlank()) {
                    mainHandler.post { speak(text, callback) }
                }
            }
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }
        cloudJob?.cancel()
        cloudJob = scope.launch {
            val useCloudTts = settingsRepository?.getIsCloudTtsEnabled() ?: true
            if (useCloudTts) {
                val playedByCloud = runCatching {
                    speakWithCloudTts(text, onDone)
                }.getOrDefault(false)
                if (!playedByCloud) {
                    onDone?.invoke()
                }
            } else {
                speakWithSystemTts(text, onDone)
            }
        }
    }

    private suspend fun speakWithCloudTts(text: String, onDone: (() -> Unit)?): Boolean {
        val repository = settingsRepository ?: return false
        val configuredBaseUrl = repository.getApiBaseUrl()
        val backendBaseUrl = BackendUrlResolver.resolve(configuredBaseUrl) ?: configuredBaseUrl
        val ttsUrl = RetrofitProvider.normalizeBaseUrl(backendBaseUrl) + "tts"
        val audioFile = withContext(Dispatchers.IO) {
            val requestBody = JSONObject()
                .put("text", text)
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(ttsUrl)
                .post(requestBody)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bytes = response.body?.bytes() ?: return@withContext null
                val file = File(appContext.cacheDir, CLOUD_TTS_FILE_NAME)
                file.writeBytes(bytes)
                file
            }
        } ?: return false

        stopMediaPlayer()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(audioFile.absolutePath)
            setOnCompletionListener {
                stopMediaPlayer()
                onDone?.invoke()
            }
            setOnErrorListener { _, _, _ ->
                stopMediaPlayer()
                onDone?.invoke()
                true
            }
            prepare()
            start()
        }
        return true
    }

    private fun speakWithSystemTts(text: String, onDone: (() -> Unit)? = null) {
        if (!isReady) {
            pendingText = text
            pendingOnDone = onDone
            return
        }
        tts?.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    onDone?.let { callback -> mainHandler.post(callback) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onDone?.let { callback -> mainHandler.post(callback) }
                }
            },
        )
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun stop() {
        cloudJob?.cancel()
        cloudJob = null
        pendingText = null
        pendingOnDone = null
        stopMediaPlayer()
        tts?.stop()
    }

    fun shutdown() {
        scope.cancel()
        cloudJob = null
        pendingText = null
        pendingOnDone = null
        stopMediaPlayer()
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    private fun stopMediaPlayer() {
        mediaPlayer?.runCatching {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
    }

    companion object {
        private const val UTTERANCE_ID = "hoshina-assistant-tts"
        private const val CLOUD_TTS_FILE_NAME = "hoshina-cloud-tts.wav"
    }
}
