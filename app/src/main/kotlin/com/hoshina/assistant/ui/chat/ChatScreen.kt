package com.hoshina.assistant.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hoshina.assistant.R
import com.hoshina.assistant.domain.model.ChatMessage
import com.hoshina.assistant.voice.SpeechToTextManager
import com.hoshina.assistant.voice.TextToSpeechManager
import kotlinx.coroutines.flow.collectLatest
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TIME_SEPARATOR_GAP_MS = 5 * 60 * 1000L

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    aiName: String,
    showDeviceTime: Boolean,
    onOpenCall: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun dismissKeyboard() {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    val micPermissionMessage = stringResource(R.string.chat_mic_permission_required)
    val defaultChatTitle = stringResource(R.string.chat_title)

    val ttsManager = remember { TextToSpeechManager(context) }
    fun speakAssistantText(text: String) {
        viewModel.setAssistantSpeaking(true)
        ttsManager.speak(text) {
            viewModel.setAssistantSpeaking(false)
        }
    }
    DisposableEffect(Unit) {
        onDispose { ttsManager.shutdown() }
    }
    LaunchedEffect(viewModel) {
        viewModel.speakEvents.collectLatest { text ->
            speakAssistantText(text)
        }
    }

    val speechManager = remember {
        SpeechToTextManager(
            context = context,
            onPartialResult = { partial -> inputText = partial },
            onFinalResult = { text ->
                inputText = ""
                viewModel.sendVoiceMessage(text)
            },
            onError = viewModel::showError,
            onListeningChanged = viewModel::setListening,
        )
    }
    DisposableEffect(Unit) {
        onDispose { speechManager.destroy() }
    }

    var pendingVoiceStart by remember { mutableStateOf(false) }
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

    fun startVoiceInput() {
        dismissKeyboard()
        viewModel.clearError()
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            speechManager.startListening()
        } else {
            pendingVoiceStart = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(imeVisible) {
        if (imeVisible && uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        bottomBar = {
            ChatInputBar(
                inputText = inputText,
                onInputTextChange = { inputText = it },
                isLoading = uiState.isLoading,
                isListening = uiState.isListening,
                speechAvailable = speechManager.isAvailable,
                onMicPress = ::startVoiceInput,
                onMicRelease = { speechManager.stopListening() },
                onSend = {
                    sendAndClear(viewModel, inputText, { inputText = "" }, ::dismissKeyboard)
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(4f)
                    .padding(end = 76.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { dismissKeyboard() })
                    },
                state = listState,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(start = 18.dp, top = 328.dp, end = 8.dp, bottom = 18.dp),
            ) {
                item {
                    StatusNotices(
                        deviceTimeText = if (showDeviceTime) uiState.deviceTimeText else "",
                        isLoading = uiState.isLoading,
                        activeAgents = uiState.activeAgents,
                        reminderNotice = uiState.reminderNotice,
                        error = uiState.error,
                    )
                }
                itemsIndexed(
                    items = uiState.messages,
                    key = { _, message -> message.id },
                ) { index, message ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val previousMessage = uiState.messages.getOrNull(index - 1)
                        if (shouldShowTimeSeparator(previousMessage, message)) {
                            TimeSeparator(timestamp = message.timestamp)
                        }
                        ChatMessageItem(
                            message = message,
                            userAvatarUri = uiState.userAvatarUri,
                            onReplay = { speakAssistantText(message.content) },
                        )
                    }
                }
            }
            ImmersiveTopChrome(
                title = aiName.ifBlank { defaultChatTitle },
                onOpenCall = onOpenCall,
                onClearChat = viewModel::clearChat,
                clearEnabled = uiState.messages.isNotEmpty() && !uiState.isLoading,
                onOpenSettings = onOpenSettings,
            )
        }
    }
}

@Composable
private fun HeroineBackdrop() {
    Image(
        painter = painterResource(R.drawable.heroine),
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .alpha(0.92f),
        contentScale = ContentScale.Crop,
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.16f),
                    0.42f to Color.Black.copy(alpha = 0.06f),
                    0.72f to Color.Black.copy(alpha = 0.42f),
                    1f to Color.Black.copy(alpha = 0.82f),
                ),
            ),
    )
}

@Composable
private fun ImmersiveTopChrome(
    title: String,
    onOpenCall: () -> Unit,
    onClearChat: () -> Unit,
    clearEnabled: Boolean,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 24.dp, top = 16.dp, end = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White.copy(alpha = 0.96f),
                fontWeight = FontWeight.Black,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .padding(end = 5.dp)
                        .heightIn(min = 1.dp)
                        .background(Color.White.copy(alpha = 0.72f)),
                )
                Text(
                    text = "Hoshina.AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.80f),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .padding(start = 5.dp)
                        .heightIn(min = 1.dp)
                        .background(Color.White.copy(alpha = 0.72f)),
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.End,
        ) {
            FloatingChromeButton(
                icon = Icons.Default.NotificationsNone,
                contentDescription = stringResource(R.string.chat_call_mode),
                onClick = onOpenCall,
            )
            FloatingChromeButton(
                icon = Icons.Default.Settings,
                contentDescription = stringResource(R.string.chat_settings),
                onClick = onOpenSettings,
            )
            FloatingChromeButton(
                icon = Icons.Default.ExpandMore,
                contentDescription = stringResource(R.string.chat_clear),
                onClick = onClearChat,
                enabled = clearEnabled,
            )
        }
    }
}

@Composable
private fun FloatingChromeButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = if (enabled) 0.56f else 0.28f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        shadowElevation = 10.dp,
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = if (enabled) 0.96f else 0.42f),
            )
        }
    }
}

@Composable
private fun StatusNotices(
    deviceTimeText: String,
    isLoading: Boolean,
    activeAgents: List<String>,
    reminderNotice: String?,
    error: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (deviceTimeText.isNotBlank()) {
            NoticeChip(text = stringResource(R.string.chat_device_time, deviceTimeText))
        }
        AgentStatusChip(
            isLoading = isLoading,
            activeAgents = activeAgents,
        )
        reminderNotice?.let { NoticeChip(text = it, tint = Color(0xFF77F2C3)) }
        error?.let { NoticeChip(text = it, tint = Color(0xFFFF8A9A)) }
    }
}

@Composable
private fun AgentStatusChip(
    isLoading: Boolean,
    activeAgents: List<String>,
) {
    val agentNames = activeAgents.map { agentDisplayName(it) }
    val text = when {
        isLoading -> stringResource(R.string.chat_agent_selecting)
        activeAgents.isNotEmpty() -> stringResource(
            R.string.chat_agent_active,
            agentNames.joinToString(" + "),
        )
        else -> ""
    }
    if (text.isNotBlank()) {
        NoticeChip(text = text, tint = Color(0xFFB7F7FF))
    }
}

@Composable
private fun agentDisplayName(agent: String): String {
    return when (agent) {
        "daily_chat" -> stringResource(R.string.agent_daily_chat)
        "deep_reasoning" -> stringResource(R.string.agent_deep_reasoning)
        "emotion_analysis" -> stringResource(R.string.agent_emotion_analysis)
        "work_handler" -> stringResource(R.string.agent_work_handler)
        "multimodal" -> stringResource(R.string.agent_multimodal)
        else -> agent
    }
}

@Composable
private fun NoticeChip(
    text: String,
    tint: Color = Color.White.copy(alpha = 0.72f),
) {
    Surface(
        color = Color.Black.copy(alpha = 0.34f),
        contentColor = tint,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun ChatInputBar(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    isLoading: Boolean,
    isListening: Boolean,
    speechAvailable: Boolean,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (isListening) {
            NoticeChip(text = stringResource(R.string.chat_listening), tint = Color(0xFF8EEBFF))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 3.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.62f)),
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = Color.Black.copy(alpha = 0.54f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
            shadowElevation = 12.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, top = 5.dp, end = 8.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .pointerInput(isLoading, speechAvailable) {
                            if (isLoading || !speechAvailable) return@pointerInput
                            detectTapGestures(
                                onPress = {
                                    onMicPress()
                                    tryAwaitRelease()
                                    onMicRelease()
                                },
                            )
                        },
                    shape = CircleShape,
                    color = if (isListening) Color(0xFF7C4DFF) else Color.White.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = stringResource(R.string.chat_hold_to_speak),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.chat_input_hint),
                            color = Color.White.copy(alpha = 0.58f),
                        )
                    },
                    enabled = !isLoading,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                )

                IconButton(
                    onClick = onSend,
                    enabled = inputText.isNotBlank() && !isLoading,
                    modifier = Modifier.size(42.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.chat_send),
                            tint = Color.White.copy(alpha = if (inputText.isNotBlank()) 0.95f else 0.38f),
                        )
                    }
                }
            }
        }
    }
}

private fun sendAndClear(
    viewModel: ChatViewModel,
    text: String,
    onSent: () -> Unit,
    onDismissKeyboard: () -> Unit = {},
) {
    if (text.isNotBlank()) {
        viewModel.sendMessage(text, speakReply = true)
        onSent()
        onDismissKeyboard()
    }
}

@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    userAvatarUri: String,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alignment = if (message.isFromUser) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (message.isFromUser) {
        Color(0xFF7C3AED).copy(alpha = 0.40f)
    } else {
        Color.Black.copy(alpha = 0.20f)
    }
    val borderColor = if (message.isFromUser) {
        Color(0xFFE9D5FF).copy(alpha = 0.24f)
    } else {
        Color.White.copy(alpha = 0.14f)
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(if (message.isFromUser) 0.82f else 0.92f),
            horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!message.isFromUser) AssistantAvatar()
            if (!message.isFromUser) {
                ReplayButton(onClick = onReplay)
            }
            Surface(
                modifier = Modifier
                    .widthIn(min = 64.dp)
                    .padding(horizontal = 8.dp)
                    .border(1.dp, borderColor, RoundedCornerShape(18.dp)),
                color = bubbleColor,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 8.dp,
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
            if (message.isFromUser) UserAvatar(userAvatarUri = userAvatarUri)
        }
    }
}

@Composable
private fun TimeSeparator(timestamp: Long) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.28f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        ) {
            Text(
                text = formatTimeSeparator(timestamp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.58f),
            )
        }
    }
}

@Composable
private fun ReplayButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.54f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 5.dp, end = 10.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = "5\"",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.84f),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AssistantAvatar() {
    Surface(
        modifier = Modifier.size(38.dp),
        shape = CircleShape,
        color = Color(0xFF0F766E).copy(alpha = 0.72f),
        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.34f)),
    ) {
        Image(
            painter = painterResource(R.drawable.anji_logo),
            contentDescription = null,
            modifier = Modifier.clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun UserAvatar(userAvatarUri: String) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.size(38.dp),
        shape = CircleShape,
        color = Color(0xFF38BDF8).copy(alpha = 0.84f),
        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.34f)),
    ) {
        val avatarBitmap = remember(userAvatarUri) {
            runCatching {
                if (userAvatarUri.isBlank()) {
                    null
                } else {
                    context.contentResolver
                        .openInputStream(Uri.parse(userAvatarUri))
                        ?.use(BitmapFactory::decodeStream)
                }
            }.getOrNull()
        }
        if (avatarBitmap != null) {
            Image(
                bitmap = avatarBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.86f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun shouldShowTimeSeparator(
    previousMessage: ChatMessage?,
    currentMessage: ChatMessage,
): Boolean {
    previousMessage ?: return true
    return currentMessage.timestamp - previousMessage.timestamp >= TIME_SEPARATOR_GAP_MS
}

private fun formatTimeSeparator(timestamp: Long): String {
    val messageDate = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    val pattern = if (
        messageDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        messageDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    ) {
        "HH:mm"
    } else {
        "yyyy/MM/dd HH:mm"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}
