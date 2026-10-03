package com.hoshina.assistant.ui.live2d

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.live2d.demo.GLRenderer
import com.live2d.demo.JniBridgeJava
import kotlinx.coroutines.delay

@Composable
fun Live2DStage(
    isSpeaking: Boolean,
    onModelReadyChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isModelReady by remember { mutableStateOf(false) }
    val roomBackground = remember {
        context.assets.open("live2d/stage/bedroom_background.png").use { stream ->
            BitmapFactory.decodeStream(stream).asImageBitmap()
        }
    }

    LaunchedEffect(Unit) {
        while (!isModelReady) {
            isModelReady = runCatching { JniBridgeJava.nativeIsModelReady() }.getOrDefault(false)
            delay(300L)
        }
    }

    LaunchedEffect(isModelReady) {
        onModelReadyChanged(isModelReady)
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Image(
            bitmap = roomBackground,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(10.dp),
        )
        Live2DNativeLayer(
            isSpeaking = isSpeaking,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f),
        )
    }
}

@Composable
private fun Live2DNativeLayer(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val renderer = remember { GLRenderer() }

    DisposableEffect(Unit) {
        JniBridgeJava.SetContext(context.applicationContext)
        context.findActivity()?.let(JniBridgeJava::SetActivityInstance)
        JniBridgeJava.nativeOnStart()
        onDispose {
            JniBridgeJava.nativeOnPause()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            GLSurfaceView(context).apply {
                setEGLContextClientVersion(2)
                setZOrderOnTop(false)
                setZOrderMediaOverlay(false)
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                preserveEGLContextOnPause = true
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                setOnTouchListener { _, event ->
                    val pointX = event.x
                    val pointY = event.y
                    queueEvent {
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> JniBridgeJava.nativeOnTouchesBegan(pointX, pointY)
                            MotionEvent.ACTION_UP -> JniBridgeJava.nativeOnTouchesEnded(pointX, pointY)
                            MotionEvent.ACTION_MOVE -> JniBridgeJava.nativeOnTouchesMoved(pointX, pointY)
                        }
                    }
                    true
                }
            }
        },
        update = {
            // Native lip sync is intentionally disabled for now.
            isSpeaking
        },
    )
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

