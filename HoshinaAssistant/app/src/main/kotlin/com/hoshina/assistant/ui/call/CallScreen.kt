package com.hoshina.assistant.ui.call

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoshina.assistant.R
import com.hoshina.assistant.ui.chat.ChatViewModel
import com.hoshina.assistant.voice.SpeechToTextManager
import com.hoshina.assistant.voice.TextToSpeechManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.random.Random

@Composable
fun CallScreen(
    viewModel: ChatViewModel,
    aiName: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val micPermissionMessage = stringResource(R.string.chat_mic_permission_required)
    var partialText by remember { mutableStateOf("") }
    var pendingVoiceStart by remember { mutableStateOf(false) }
    var callActive by remember { mutableStateOf(true) }
    var isSpeaking by remember { mutableStateOf(false) }
    var thinkingFillerPlayed by remember { mutableStateOf(false) }

    val ttsManager = remember { TextToSpeechManager(context) }
    DisposableEffect(Unit) {
        onDispose { ttsManager.shutdown() }
    }
    LaunchedEffect(viewModel) {
        viewModel.speakEvents.collectLatest { text ->
            isSpeaking = true
            ttsManager.speak(text) {
                isSpeaking = false
            }
        }
    }

    val speechManager = remember {
        SpeechToTextManager(
            context = context,
            onPartialResult = { partialText = it },
            onFinalResult = { text ->
                partialText = ""
                thinkingFillerPlayed = false
                viewModel.sendVoiceMessage(text)
            },
            onError = viewModel::showError,
            onListeningChanged = viewModel::setListening,
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            callActive = false
            speechManager.destroy()
            ttsManager.stop()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingVoiceStart) {
            speechManager.startListening()
        } else if (!granted) {
            viewModel.showError(micPermissionMessage)
        }
        pendingVoiceStart = false
    }

    fun startCallInput() {
        viewModel.clearError()
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            callActive = true
            speechManager.startListening()
        } else {
            pendingVoiceStart = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        startCallInput()
    }

    LaunchedEffect(callActive, uiState.isLoading, uiState.isListening, isSpeaking) {
        if (callActive && !uiState.isLoading && !uiState.isListening && !isSpeaking) {
            delay(450L)
            startCallInput()
        }
    }

    LaunchedEffect(uiState.isLoading, callActive) {
        if (uiState.isLoading && callActive && !thinkingFillerPlayed) {
            delay(1_500L)
            if (uiState.isLoading && callActive && !isSpeaking) {
                thinkingFillerPlayed = true
                isSpeaking = true
                ttsManager.speak(CallThinkingFillers.random()) {
                    isSpeaking = false
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF07111F), Color(0xFF130A22), Color.Black),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.call_mode_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = aiName.ifBlank { stringResource(R.string.chat_title) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier.size(168.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                ) {
                    Image(
                        painter = painterResource(R.drawable.app_icon),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.clip(CircleShape),
                    )
                }
                Text(
                    text = when {
                        uiState.isLoading -> stringResource(R.string.call_status_thinking)
                        uiState.isListening -> stringResource(R.string.call_status_listening)
                        else -> stringResource(R.string.call_status_ready)
                    },
                    modifier = Modifier.padding(top = 24.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                val caption = partialText.ifBlank { uiState.error.orEmpty() }
                if (caption.isNotBlank()) {
                    Text(
                        text = caption,
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    color = Color(0xFFE94B5F),
                ) {
                    IconButton(
                        onClick = {
                            callActive = false
                            speechManager.stopListening()
                            ttsManager.stop()
                            onNavigateBack()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = stringResource(R.string.call_end),
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                Surface(
                    modifier = Modifier.size(88.dp),
                    shape = CircleShape,
                    color = if (callActive) Color(0xFF7C4DFF) else Color(0xFF475569),
                ) {
                    IconButton(
                        onClick = {
                            callActive = !callActive
                            if (callActive) {
                                startCallInput()
                            } else {
                                speechManager.stopListening()
                            }
                        },
                        enabled = !uiState.isLoading,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = stringResource(R.string.call_talk),
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }
        }
    }
}

private object CallThinkingFillers {
    private val values = listOf(
        "嗯，我想一下。",
        "等我一下喔。",
        "我懂，我整理一下。",
        "好，我聽到了。",
    )

    fun random(): String = values[Random.nextInt(values.size)]
}
