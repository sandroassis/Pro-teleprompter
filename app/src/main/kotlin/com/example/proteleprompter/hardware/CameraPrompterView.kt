package com.example.proteleprompter.hardware

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StudioVideoRecorder(private val context: Context) {
    val controller: LifecycleCameraController = LifecycleCameraController(context).apply {
        setEnabledUseCases(CameraController.VIDEO_CAPTURE)
        videoCaptureQualitySelector = QualitySelector.fromOrderedList(
            listOf(Quality.FHD, Quality.HD, Quality.SD)
        )
    }

    private var activeRecording: Recording? = null

    fun setFrontCamera(useFront: Boolean) {
        try {
            controller.cameraSelector = if (useFront) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    fun toggleRecording(
        hasAudioPermission: Boolean,
        onStatusMessage: (String) -> Unit,
        onRecordingStateChanged: (Boolean) -> Unit
    ) {
        val current = activeRecording
        if (current != null) {
            current.stop()
            activeRecording = null
            onRecordingStateChanged(false)
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Teleprompter_$timestamp")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ProTeleprompter")
            }
        }

        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        val audioConfig = if (hasAudioPermission) {
            AudioConfig.create(true)
        } else {
            AudioConfig.AUDIO_DISABLED
        }

        try {
            activeRecording = controller.startRecording(
                mediaStoreOutput,
                audioConfig,
                ContextCompat.getMainExecutor(context)
            ) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        onRecordingStateChanged(true)
                        onStatusMessage("Gravando vídeo na Galeria...")
                    }
                    is VideoRecordEvent.Finalize -> {
                        activeRecording = null
                        onRecordingStateChanged(false)
                        if (!event.hasError()) {
                            onStatusMessage("Vídeo salvo em Filmes/ProTeleprompter!")
                        } else {
                            onStatusMessage("Gravação encerrada (${event.error})")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            onRecordingStateChanged(false)
            onStatusMessage("Não foi possível iniciar a câmera: ${e.localizedMessage ?: "erro"}")
        }
    }

    fun stopIfRecording() {
        try {
            activeRecording?.stop()
        } catch (_: Exception) {
        }
        activeRecording = null
    }
}

@Composable
fun CameraPrompterBackground(
    recorder: StudioVideoRecorder,
    useFrontCamera: Boolean,
    showGrid: Boolean,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(useFrontCamera) {
        recorder.setFrontCamera(useFrontCamera)
    }

    DisposableEffect(lifecycleOwner) {
        try {
            recorder.controller.bindToLifecycle(lifecycleOwner)
        } catch (_: Exception) {
        }
        onDispose {
            recorder.stopIfRecording()
            try {
                recorder.controller.unbind()
            } catch (_: Exception) {
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    controller = recorder.controller
                }
            },
            update = { previewView ->
                if (previewView.controller != recorder.controller) {
                    previewView.controller = recorder.controller
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showGrid) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 1.dp.toPx()
                val gridColor = Color.White.copy(alpha = 0.22f)
                val w = size.width
                val h = size.height

                // Rule of thirds vertical lines
                drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = stroke)
                drawLine(gridColor, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeWidth = stroke)

                // Rule of thirds horizontal lines
                drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = stroke)
                drawLine(gridColor, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth = stroke)
            }
        }
    }
}
