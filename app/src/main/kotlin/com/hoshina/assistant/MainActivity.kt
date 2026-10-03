package com.hoshina.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.hoshina.assistant.companion.CompanionMessageScheduler
import com.hoshina.assistant.data.remote.BackendUrlResolver
import com.hoshina.assistant.data.remote.RetrofitProvider
import com.hoshina.assistant.di.ChatViewModelFactory
import com.hoshina.assistant.di.SettingsViewModelFactory
import com.hoshina.assistant.reminder.ReminderPermissionHelper
import com.hoshina.assistant.ui.chat.ChatViewModel
import com.hoshina.assistant.ui.live2d.Live2DStage
import com.hoshina.assistant.ui.navigation.AppNavGraph
import com.hoshina.assistant.ui.settings.SettingsViewModel
import com.hoshina.assistant.ui.theme.HoshinaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private data class AppUpdateInfo(
    val latestVersionCode: Long,
    val latestVersionName: String,
    val apkUrl: String?,
    val releaseNotes: String?,
)

private data class StartupCheckResult(
    val updateInfo: AppUpdateInfo?,
)

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    private val exactAlarmLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestReminderPermissions()
        setupCompanionMessages()

        val app = application as HoshinaApplication

        setContent {
            val darkTheme by app.settingsRepository.isDarkMode.collectAsStateWithLifecycle(false)
            val appLanguage by app.settingsRepository.appLanguage.collectAsStateWithLifecycle("zh")
            var isStartupCheckComplete by remember { mutableStateOf(false) }
            var startupMessage by remember {
                mutableStateOf("\u6b63\u5728\u6e96\u5099\u555f\u52d5")
            }
            var startupError by remember { mutableStateOf<String?>(null) }
            var isLive2DReady by remember { mutableStateOf(false) }
            var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }

            LaunchedEffect(Unit) {
                delay(SPLASH_DURATION_MS / 2)
                runStartupCheck(
                    app = app,
                    onStatusChange = { message -> startupMessage = message },
                ).onSuccess { result ->
                    if (result.updateInfo != null) {
                        updateInfo = result.updateInfo
                        startupMessage = "\u767c\u73fe\u65b0\u7248\u672c\uff0c\u8acb\u5148\u78ba\u8a8d\u66f4\u65b0"
                    } else {
                        isStartupCheckComplete = true
                    }
                }.onFailure {
                    startupError = "\u7121\u6cd5\u9023\u63a5\u5f8c\u7aef API\uff0c\u8acb\u78ba\u8a8d\u5df2\u555f\u52d5\u5f8c\u7aef BAT"
                    startupMessage = "\u5f8c\u7aef API \u9023\u63a5\u5931\u6557"
                }
            }

            val localizedContext = remember(appLanguage) {
                localizedContext(baseContext, appLanguage)
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalActivityResultRegistryOwner provides this@MainActivity,
            ) {
                HoshinaTheme(darkTheme = darkTheme) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val navController = rememberNavController()
                            val chatViewModel: ChatViewModel = viewModel(
                                factory = ChatViewModelFactory(
                                    app,
                                    app.chatRepository,
                                    app.reminderRepository,
                                    app.userMemoryRepository,
                                ),
                            )
                            val settingsViewModel: SettingsViewModel = viewModel(
                                factory = SettingsViewModelFactory(
                                    app,
                                    app.settingsRepository,
                                    app.userMemoryRepository,
                                ),
                            )
                            val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()

                            if (isStartupCheckComplete) {
                                Live2DStage(
                                    isSpeaking = chatState.isAssistantSpeaking,
                                    onModelReadyChanged = { isReady -> isLive2DReady = isReady },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .zIndex(0f),
                                )
                                AppNavGraph(
                                    navController = navController,
                                    chatViewModel = chatViewModel,
                                    settingsViewModel = settingsViewModel,
                                    modifier = Modifier.zIndex(1f),
                                )
                            }

                            when {
                                !isStartupCheckComplete -> StartupCheckScreen(
                                    message = startupMessage,
                                    error = startupError,
                                    modifier = Modifier.zIndex(3f),
                                )
                                !isLive2DReady -> ModelLoadingBlocker(
                                    message = "\u6a21\u578b\u52a0\u8f09\u4e2d",
                                    modifier = Modifier.zIndex(3f),
                                )
                            }

                            updateInfo?.let { info ->
                                UpdateDialog(
                                    info = info,
                                    onDismiss = {
                                        updateInfo = null
                                        isStartupCheckComplete = true
                                    },
                                    onOpenUpdate = {
                                        updateInfo = null
                                        isStartupCheckComplete = true
                                        openUpdateUrl(info.apkUrl)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestReminderPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!ReminderPermissionHelper.hasNotificationPermission(this)) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (!ReminderPermissionHelper.canScheduleExactAlarms(this)) {
            exactAlarmLauncher.launch(ReminderPermissionHelper.exactAlarmSettingsIntent(this))
        }
    }

    private fun setupCompanionMessages() {
        val app = application as HoshinaApplication
        val scheduler = CompanionMessageScheduler(applicationContext, app.settingsRepository)
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    scheduler.cancel()
                }

                override fun onStop(owner: LifecycleOwner) {
                    lifecycleScope.launch {
                        scheduler.scheduleNext()
                    }
                }
            },
        )
    }

    private suspend fun runStartupCheck(
        app: HoshinaApplication,
        onStatusChange: (String) -> Unit,
    ): Result<StartupCheckResult> {
        return runCatching {
            onStatusChange("\u6b63\u5728\u5c0b\u627e\u5f8c\u7aef API")
            val configuredUrl = app.settingsRepository.getApiBaseUrl()
            val backendUrl = BackendUrlResolver.resolve(configuredUrl)
                ?: error("Backend API not reachable.")
            app.settingsRepository.setApiBaseUrl(backendUrl)

            onStatusChange("\u5df2\u9023\u63a5\u5f8c\u7aef\uff0c\u6b63\u5728\u78ba\u8a8d\u7248\u672c")
            val currentVersionCode = currentVersionCode()
            val remote = RetrofitProvider.createApiService(backendUrl).getAppVersion()
            val updateInfo = if (remote.latestVersionCode > currentVersionCode) {
                AppUpdateInfo(
                    latestVersionCode = remote.latestVersionCode,
                    latestVersionName = remote.latestVersionName,
                    apkUrl = resolveUpdateUrl(backendUrl, remote.apkUrl),
                    releaseNotes = remote.releaseNotes?.trim()?.takeIf { it.isNotBlank() },
                )
            } else {
                null
            }
            onStatusChange("\u7248\u672c\u78ba\u8a8d\u5b8c\u6210\uff0c\u6b63\u5728\u8f09\u5165\u4e3b\u756b\u9762")
            StartupCheckResult(updateInfo = updateInfo)
        }
    }

    private fun currentVersionCode(): Long {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        return PackageInfoCompat.getLongVersionCode(packageInfo)
    }

    private fun resolveUpdateUrl(baseUrl: String, apkUrl: String?): String? {
        val url = apkUrl?.trim().orEmpty()
        if (url.isBlank()) return null
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        val normalizedBase = RetrofitProvider.normalizeBaseUrl(baseUrl)
        return normalizedBase + url.trimStart('/')
    }

    private fun openUpdateUrl(apkUrl: String?) {
        val url = apkUrl?.trim().orEmpty()
        if (url.isBlank()) return
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    companion object {
        private const val SPLASH_DURATION_MS = 1400L
    }
}

@Composable
private fun UpdateDialog(
    info: AppUpdateInfo,
    onDismiss: () -> Unit,
    onOpenUpdate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("\u767c\u73fe\u65b0\u7248\u672c") },
        text = {
            val notes = info.releaseNotes?.let { "\n\n\u66f4\u65b0\u5167\u5bb9\uff1a\n$it" }.orEmpty()
            Text("\u76ee\u524d\u6709\u65b0\u7248 ${info.latestVersionName} \u53ef\u4ee5\u66f4\u65b0\u3002$notes")
        },
        confirmButton = {
            if (!info.apkUrl.isNullOrBlank()) {
                Button(onClick = onOpenUpdate) {
                    Text("\u524d\u5f80\u66f4\u65b0")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("\u7a0d\u5f8c\u518d\u8aaa")
            }
        },
    )
}

@Composable
private fun StartupCheckScreen(
    message: String,
    error: String?,
    modifier: Modifier = Modifier,
) {
    LoadingBlocker(
        message = message,
        error = error,
        modifier = modifier,
    )
}

@Composable
private fun ModelLoadingBlocker(
    message: String,
    modifier: Modifier = Modifier,
) {
    LoadingBlocker(
        message = message,
        error = null,
        modifier = modifier,
    )
}

@Composable
private fun LoadingBlocker(
    message: String,
    error: String?,
    modifier: Modifier = Modifier,
) {
    var loadingDotCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            loadingDotCount = (loadingDotCount + 1) % 4
            delay(420L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.12f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.Black.copy(alpha = 0.62f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "$message${".".repeat(loadingDotCount)}",
                    color = Color.White.copy(alpha = 0.92f),
                )
                error?.let {
                    Text(
                        text = it,
                        color = Color(0xFFFFA3B0),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

private fun localizedContext(context: Context, language: String): Context {
    val locale = when (language) {
        "en" -> Locale.ENGLISH
        "ja" -> Locale.JAPANESE
        else -> Locale.TAIWAN
    }
    Locale.setDefault(locale)
    val config = Configuration(context.resources.configuration)
    config.setLocales(LocaleList(locale))
    return context.createConfigurationContext(config)
}

@Composable
private fun SplashLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.anji_logo),
                contentDescription = null,
                modifier = Modifier
                    .padding(24.dp)
                    .size(280.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
