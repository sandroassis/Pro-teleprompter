package com.example.proteleprompter.ui.prompter

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.proteleprompter.data.PrompterSettings
import com.example.proteleprompter.data.Script
import com.example.proteleprompter.data.ScriptParser
import com.example.proteleprompter.hardware.CameraPrompterBackground
import com.example.proteleprompter.hardware.RemoteCommand
import com.example.proteleprompter.hardware.RemoteInputBus
import com.example.proteleprompter.hardware.StudioVideoRecorder
import com.example.proteleprompter.hardware.VoiceScrollController
import com.example.proteleprompter.ui.adaptive.rememberAdaptiveWindowInfo
import com.example.proteleprompter.ui.theme.BroadcastAmber
import com.example.proteleprompter.ui.theme.BroadcastCyan
import com.example.proteleprompter.ui.theme.BroadcastGreen
import com.example.proteleprompter.ui.theme.BroadcastPurple
import com.example.proteleprompter.ui.theme.BroadcastRed
import com.example.proteleprompter.ui.theme.StudioBlack
import com.example.proteleprompter.ui.theme.StudioCard
import com.example.proteleprompter.ui.theme.StudioSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PrompterScreen(
    script: Script,
    initialSettings: PrompterSettings,
    onSaveScriptChanges: (Script) -> Unit,
    onUpdateSettings: ((PrompterSettings) -> PrompterSettings) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val windowInfo = rememberAdaptiveWindowInfo()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Live adjustable teleprompter parameters (with automatic boost for Tablet/TV distance)
    var wpm by remember(script.id) { mutableIntStateOf(script.wpm) }
    var fontSizeSp by remember(script.id) {
        mutableIntStateOf((script.fontSizeSp + windowInfo.basePrompterFontBoostSp).coerceAtMost(84))
    }
    var sideMarginPercent by remember(script.id) { mutableIntStateOf(script.sideMarginPercent) }
    var mirrorH by remember(script.id) { mutableStateOf(script.mirrorHorizontal) }
    var mirrorV by remember(script.id) { mutableStateOf(script.mirrorVertical) }
    var eyeLinePercent by remember { mutableFloatStateOf(initialSettings.eyeLinePositionPercent) }
    var focusShadeAlpha by remember { mutableFloatStateOf(initialSettings.focusShadeAlpha) }

    // Camera & Audio permissions and states
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    var cameraOverlayActive by remember { mutableStateOf(initialSettings.useCameraOverlay) }
    var useFrontCamera by remember { mutableStateOf(initialSettings.useFrontCamera) }
    var showCameraGrid by remember { mutableStateOf(initialSettings.showCameraGrid) }
    var isRecordingVideo by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }

    // Playback, Countdown, Timed Pause & Voice Scroll states
    var isPlaying by remember { mutableStateOf(false) }
    var countdownRemaining by remember { mutableIntStateOf(0) }
    var activePauseRemaining by remember { mutableIntStateOf(0) }
    val triggeredPauseIndices = remember(script.id) { mutableStateListOf<Int>() }
    var voiceScrollActive by remember { mutableStateOf(initialSettings.voiceScrollEnabled) }
    var showOverlayHud by remember { mutableStateOf(true) }
    var showQuickTuningSheet by remember { mutableStateOf(false) }
    var toastNotification by remember { mutableStateOf<String?>(null) }
    var elapsedPlaySeconds by remember { mutableIntStateOf(0) }

    val studioRecorder = remember(context) { StudioVideoRecorder(context) }

    // Voice Scroll Controller
    val voiceController = remember(context) {
        VoiceScrollController(context) { targetFraction ->
            scope.launch {
                val max = scrollState.maxValue
                if (max > 0) {
                    val targetPx = (targetFraction * max).roundToInt()
                    if (targetPx > scrollState.value) {
                        scrollState.animateScrollTo(targetPx)
                    }
                }
            }
        }
    }
    val lastHeardPhrase by voiceController.lastHeardPhrase.collectAsState()
    val isVoiceListening by voiceController.isListening.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCameraPermission = result[Manifest.permission.CAMERA] == true || hasCameraPermission
        hasAudioPermission = result[Manifest.permission.RECORD_AUDIO] == true || hasAudioPermission
        if (hasCameraPermission && cameraOverlayActive) {
            toastNotification = "Câmera de estúdio ativada"
        }
        if (hasAudioPermission && voiceScrollActive) {
            voiceController.startListening()
        }
    }

    // Prepare voice indexing & pause cues
    val pauseCues = remember(script.content) {
        ScriptParser.extractPauseCues(script.content)
    }
    val topicMarkers = remember(script.content) {
        script.topicMarkers
    }
    val styledScript = remember(script.content) {
        ScriptParser.buildStyledScript(script.content, Color.White)
    }

    LaunchedEffect(script.content) {
        voiceController.prepareScript(script.content)
    }

    // Keep screen awake & activate hardware remote listener
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        RemoteInputBus.isPrompterActive = true
        RemoteInputBus.interceptVolumeKeys = initialSettings.volumeKeysControlSpeed

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            RemoteInputBus.isPrompterActive = false
            voiceController.stopListening()
            studioRecorder.stopIfRecording()
            onSaveScriptChanges(
                script.copy(
                    wpm = wpm,
                    fontSizeSp = fontSizeSp,
                    sideMarginPercent = sideMarginPercent,
                    mirrorHorizontal = mirrorH,
                    mirrorVertical = mirrorV
                )
            )
        }
    }

    fun triggerPlayWithOptionalCountdown() {
        if (isPlaying || countdownRemaining > 0) {
            isPlaying = false
            countdownRemaining = 0
            activePauseRemaining = 0
            return
        }
        if (scrollState.value >= scrollState.maxValue && scrollState.maxValue > 0) {
            scope.launch {
                scrollState.scrollTo(0)
                triggeredPauseIndices.clear()
            }
        }
        if (initialSettings.countdownSeconds > 0 && scrollState.value < 40) {
            countdownRemaining = initialSettings.countdownSeconds
        } else {
            isPlaying = true
        }
    }

    fun jumpToMarker(next: Boolean) {
        val max = scrollState.maxValue
        if (max <= 0 || topicMarkers.isEmpty()) return
        val totalLen = script.content.length.coerceAtLeast(1)
        val markerPositions = topicMarkers.map {
            ((it.charOffset.toFloat() / totalLen.toFloat()) * max).roundToInt()
        }
        val current = scrollState.value
        val target = if (next) {
            markerPositions.firstOrNull { it > current + 40 } ?: max
        } else {
            markerPositions.lastOrNull { it < current - 40 } ?: 0
        }
        scope.launch {
            scrollState.animateScrollTo(target)
        }
    }

    // Listen to Bluetooth Remote / Pedal / Keyboard commands
    LaunchedEffect(Unit) {
        RemoteInputBus.commands.collect { cmd ->
            toastNotification = "Controle: ${cmd.label}"
            when (cmd) {
                RemoteCommand.TOGGLE_PLAY_PAUSE -> triggerPlayWithOptionalCountdown()
                RemoteCommand.SPEED_UP -> wpm = (wpm + 10).coerceAtMost(280)
                RemoteCommand.SPEED_DOWN -> wpm = (wpm - 10).coerceAtLeast(50)
                RemoteCommand.NEXT_MARKER -> jumpToMarker(next = true)
                RemoteCommand.PREV_MARKER -> jumpToMarker(next = false)
                RemoteCommand.RESTART -> {
                    isPlaying = false
                    activePauseRemaining = 0
                    triggeredPauseIndices.clear()
                    scope.launch { scrollState.animateScrollTo(0) }
                }
                RemoteCommand.TOGGLE_MIRROR -> mirrorH = !mirrorH
            }
        }
    }

    // Auto-clear HUD toast notification
    LaunchedEffect(toastNotification) {
        if (toastNotification != null) {
            delay(2200)
            toastNotification = null
        }
    }

    // Countdown timer before starting scroll
    LaunchedEffect(countdownRemaining) {
        if (countdownRemaining > 0) {
            delay(1000)
            countdownRemaining -= 1
            if (countdownRemaining == 0) {
                isPlaying = true
            }
        }
    }

    // Timed [PAUSA Xs] countdown handler
    LaunchedEffect(activePauseRemaining) {
        if (activePauseRemaining > 0) {
            delay(1000)
            activePauseRemaining -= 1
        }
    }

    // Recording duration timer
    LaunchedEffect(isRecordingVideo) {
        if (isRecordingVideo) {
            recordingSeconds = 0
            while (isRecordingVideo) {
                delay(1000)
                recordingSeconds += 1
            }
        }
    }

    // Elapsed reading timer
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(1000)
            elapsedPlaySeconds += 1
        }
    }

    // Manage Voice Scroll lifecycle
    LaunchedEffect(voiceScrollActive, hasAudioPermission) {
        if (voiceScrollActive) {
            if (hasAudioPermission) {
                voiceController.startListening()
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            }
        } else {
            voiceController.stopListening()
        }
    }

    // High-precision 60/120fps WPM Auto-Scroll Engine
    LaunchedEffect(isPlaying, wpm, fontSizeSp, activePauseRemaining, initialSettings.timedPausesEnabled) {
        if (!isPlaying || activePauseRemaining > 0) return@LaunchedEffect

        var subPixelRemainder = 0f
        var lastFrameNanos = withFrameNanos { it }

        while (isPlaying && activePauseRemaining == 0) {
            val currentNanos = withFrameNanos { it }
            val deltaSeconds = ((currentNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
            lastFrameNanos = currentNanos

            val maxScroll = scrollState.maxValue
            if (maxScroll > 0) {
                val totalWords = script.wordCount.coerceAtLeast(25)
                val totalSpeechSeconds = (totalWords.toFloat() / wpm.coerceAtLeast(40)) * 60f
                // Compute exact pixels/sec so the entire script scrolls in totalSpeechSeconds
                val pixelsPerSecond = (maxScroll.toFloat() / totalSpeechSeconds.coerceAtLeast(5f))
                    .coerceIn(18f, 420f)

                val totalDeltaPx = pixelsPerSecond * deltaSeconds + subPixelRemainder
                val wholePixels = totalDeltaPx.toInt()
                subPixelRemainder = totalDeltaPx - wholePixels

                if (wholePixels > 0) {
                    val nextValue = (scrollState.value + wholePixels).coerceAtMost(maxScroll)
                    scrollState.scrollTo(nextValue)

                    val progress = nextValue.toFloat() / maxScroll.toFloat()
                    voiceController.updateCursorFromScroll(progress)

                    // Check if we crossed a [PAUSA Xs] cue
                    if (initialSettings.timedPausesEnabled) {
                        val hitCueIndex = pauseCues.indexOfFirst { cue ->
                            cue.progressFraction <= progress &&
                                cue.progressFraction >= (progress - 0.03f)
                        }
                        if (hitCueIndex >= 0 && hitCueIndex !in triggeredPauseIndices) {
                            triggeredPauseIndices.add(hitCueIndex)
                            activePauseRemaining = pauseCues[hitCueIndex].seconds
                        }
                    }

                    if (nextValue >= maxScroll) {
                        if (initialSettings.autoLoop) {
                            triggeredPauseIndices.clear()
                            scrollState.scrollTo(0)
                        } else {
                            isPlaying = false
                        }
                    }
                }
            }
        }
    }

    val progressFraction = if (scrollState.maxValue > 0) {
        (scrollState.value.toFloat() / scrollState.maxValue.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val remainingSeconds = ((1f - progressFraction) * script.estimatedDurationSeconds).roundToInt()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(initialSettings.backgroundTheme.colorHex))
    ) {
        val isCompactPipWindow = maxWidth < 290.dp || maxHeight < 340.dp
        val viewportHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val eyeLineOffsetY = (viewportHeightPx * eyeLinePercent).roundToInt()
        val bandHeightPx = with(LocalDensity.current) { initialSettings.focusBandHeightDp.dp.toPx().roundToInt() }
        val horizontalMarginDp = (maxWidth * (sideMarginPercent / 100f)).coerceIn(12.dp, 120.dp)

        // 1. Live CameraX Background (when enabled)
        if (cameraOverlayActive && hasCameraPermission) {
            CameraPrompterBackground(
                recorder = studioRecorder,
                useFrontCamera = useFrontCamera,
                showGrid = showCameraGrid && !isCompactPipWindow,
                modifier = Modifier.fillMaxSize()
            )
            // Subtle readable contrast scrim over camera feed
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
            )
        }

        // 2. Scrolling Teleprompter Text Layer (with Horizontal & Vertical Mirroring!)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showOverlayHud = !showOverlayHud },
                        onDoubleTap = { triggerPlayWithOptionalCountdown() }
                    )
                }
                .graphicsLayer(
                    scaleX = if (mirrorH) -1f else 1f,
                    scaleY = if (mirrorV) -1f else 1f
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = horizontalMarginDp)
            ) {
                // Top lead-in padding so line 1 starts right at the Eye-Line guide
                Spacer(
                    modifier = Modifier.height(
                        with(LocalDensity.current) { eyeLineOffsetY.toDp() }
                    )
                )

                Text(
                    text = styledScript,
                    fontSize = if (isCompactPipWindow) (fontSizeSp * 0.62f).sp else fontSizeSp.sp,
                    lineHeight = if (isCompactPipWindow) {
                        (fontSizeSp * 0.62f * script.lineHeightMultiplier).sp
                    } else {
                        (fontSizeSp * script.lineHeightMultiplier).sp
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = Color(initialSettings.textColorHex),
                    modifier = Modifier.fillMaxWidth()
                )

                // Bottom lead-out padding so the last line can scroll all the way through the Eye-Line
                Spacer(
                    modifier = Modifier.height(
                        with(LocalDensity.current) {
                            (viewportHeightPx - eyeLineOffsetY).coerceAtLeast(100f).toDp()
                        }
                    )
                )
            }
        }

        // 3. Eye-Line Focus Guide & Upper/Lower Dimmer Masks
        Box(modifier = Modifier.fillMaxSize()) {
            // Upper dimmer shade above the eye-line band
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { eyeLineOffsetY.toDp() })
                    .background(Color.Black.copy(alpha = focusShadeAlpha * 0.75f))
            )

            // Active Eye-Line Reading Band
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, eyeLineOffsetY) }
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { bandHeightPx.toDp() })
                    .border(
                        width = 1.5.dp,
                        color = BroadcastCyan.copy(alpha = 0.55f)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "▶",
                        color = BroadcastAmber,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "◀",
                        color = BroadcastAmber,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Lower dimmer shade below the eye-line band
            val lowerTopPx = (eyeLineOffsetY + bandHeightPx).coerceAtMost(viewportHeightPx.roundToInt())
            val lowerHeightPx = (viewportHeightPx.roundToInt() - lowerTopPx).coerceAtLeast(0)
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, lowerTopPx) }
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { lowerHeightPx.toDp() })
                    .background(Color.Black.copy(alpha = focusShadeAlpha * 0.75f))
            )
        }

        // 4. Compact PiP Floating Controls (when in Picture-in-Picture window)
        if (isCompactPipWindow) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$wpm WPM",
                    color = BroadcastCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = { triggerPlayWithOptionalCountdown() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = BroadcastAmber
                    )
                }
            }
        }

        // 5. Full Studio HUD Overlay (Hidden in PiP or when presenter taps screen for clean view)
        AnimatedVisibility(
            visible = showOverlayHud && !isCompactPipWindow,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // TOP STUDIO BAR
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioBlack.copy(alpha = 0.84f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Sair do Teleprompter",
                                tint = Color.White
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = script.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Restante: %02d:%02d".format(
                                        remainingSeconds / 60,
                                        remainingSeconds % 60
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BroadcastGreen
                                )
                                Text(
                                    text = "${(progressFraction * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BroadcastCyan
                                )
                                if (mirrorH || mirrorV) {
                                    Text(
                                        text = "ESPELHADO",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = BroadcastAmber
                                    )
                                }
                            }
                        }

                        // Recording Indicator or Record Button (when Camera Overlay is active)
                        if (cameraOverlayActive) {
                            Button(
                                onClick = {
                                    if (!hasCameraPermission) {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.CAMERA,
                                                Manifest.permission.RECORD_AUDIO
                                            )
                                        )
                                    } else {
                                        studioRecorder.toggleRecording(
                                            hasAudioPermission = hasAudioPermission,
                                            onStatusMessage = { toastNotification = it },
                                            onRecordingStateChanged = { recording ->
                                                isRecordingVideo = recording
                                                if (recording && !isPlaying) {
                                                    triggerPlayWithOptionalCountdown()
                                                }
                                            }
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRecordingVideo) BroadcastRed else StudioCard,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isRecordingVideo) {
                                        Icons.Default.Stop
                                    } else {
                                        Icons.Default.FiberManualRecord
                                    },
                                    contentDescription = "Gravar Vídeo",
                                    tint = if (isRecordingVideo) Color.White else BroadcastRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (isRecordingVideo) {
                                        "REC %02d:%02d".format(
                                            recordingSeconds / 60,
                                            recordingSeconds % 60
                                        )
                                    } else {
                                        "GRAVAR"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Picture-in-Picture (Janela Flutuante) Button
                        IconButton(
                            onClick = {
                                val entered = enterFloatingPipMode(context)
                                if (!entered) {
                                    toastNotification = "Modo PiP indisponível neste dispositivo"
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.PictureInPictureAlt,
                                contentDescription = "Janela Flutuante (PiP)",
                                tint = BroadcastCyan
                            )
                        }

                        // Quick Studio Tuning Button
                        IconButton(onClick = { showQuickTuningSheet = !showQuickTuningSheet }) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "Ajustes de Estúdio",
                                tint = if (showQuickTuningSheet) BroadcastAmber else Color.White
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .height(3.dp),
                        color = BroadcastCyan,
                        trackColor = StudioCard
                    )

                    // Topic Jump Bar (=== TÓPICO ===)
                    if (topicMarkers.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tópicos:",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                            topicMarkers.forEach { marker ->
                                AssistChip(
                                    onClick = {
                                        scope.launch {
                                            val max = scrollState.maxValue
                                            val totalLen = script.content.length.coerceAtLeast(1)
                                            val targetPx =
                                                ((marker.charOffset.toFloat() / totalLen) * max).roundToInt()
                                            scrollState.animateScrollTo(targetPx)
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = marker.title,
                                            fontSize = 11.sp,
                                            color = Color.White
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = StudioCard
                                    )
                                )
                            }
                        }
                    }

                    // Voice-Activated Scrolling Status Strip
                    if (voiceScrollActive) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(BroadcastPurple.copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (isVoiceListening) BroadcastGreen else BroadcastAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (lastHeardPhrase.isNotBlank()) {
                                    "Voz detectada: \"$lastHeardPhrase\""
                                } else {
                                    "Rolagem por Voz ativa — leia o roteiro em voz alta..."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // COLLAPSIBLE LIVE STUDIO TUNING CARD
                AnimatedVisibility(visible = showQuickTuningSheet) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = StudioSurface.copy(alpha = 0.95f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Calibração Rápida de Estúdio",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = BroadcastAmber
                            )

                            // Font Size live slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Fonte: ${fontSizeSp}sp",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(95.dp)
                                )
                                Slider(
                                    value = fontSizeSp.toFloat(),
                                    onValueChange = { fontSizeSp = it.roundToInt() },
                                    valueRange = 22f..70f,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Eye-Line position live slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Linha Guia: ${(eyeLinePercent * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(95.dp)
                                )
                                Slider(
                                    value = eyeLinePercent,
                                    onValueChange = {
                                        eyeLinePercent = it
                                        onUpdateSettings { s -> s.copy(eyeLinePositionPercent = it) }
                                    },
                                    valueRange = 0.12f..0.72f,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Focus Mask Dimmer Alpha
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Máscara: ${(focusShadeAlpha * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(95.dp)
                                )
                                Slider(
                                    value = focusShadeAlpha,
                                    onValueChange = {
                                        focusShadeAlpha = it
                                        onUpdateSettings { s -> s.copy(focusShadeAlpha = it) }
                                    },
                                    valueRange = 0f..0.92f,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Side Margin live slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Margem: $sideMarginPercent%",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(95.dp)
                                )
                                Slider(
                                    value = sideMarginPercent.toFloat(),
                                    onValueChange = { sideMarginPercent = it.roundToInt() },
                                    valueRange = 2f..28f,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Mirror Vertical + Camera Flip + Grid toggles
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AssistChip(
                                    onClick = { mirrorV = !mirrorV },
                                    label = {
                                        Text(if (mirrorV) "Flip Y: ON" else "Flip Y: OFF")
                                    }
                                )
                                if (cameraOverlayActive) {
                                    AssistChip(
                                        onClick = { useFrontCamera = !useFrontCamera },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Cameraswitch,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = {
                                            Text(if (useFrontCamera) "Câm. Frontal" else "Câm. Traseira")
                                        }
                                    )
                                    AssistChip(
                                        onClick = { showCameraGrid = !showCameraGrid },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.GridOn,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text("Grade") }
                                    )
                                }
                            }
                        }
                    }
                }

                // BOTTOM TRANSPORT & HARDWARE DOCK
                Surface(
                    color = StudioBlack.copy(alpha = 0.88f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Speed WPM fine adjustment row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { wpm = (wpm - 5).coerceAtLeast(40) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Diminuir WPM", tint = Color.White)
                                }
                                Text(
                                    text = "$wpm WPM",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = BroadcastCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )
                                IconButton(
                                    onClick = { wpm = (wpm + 5).coerceAtMost(280) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Aumentar WPM", tint = Color.White)
                                }
                            }

                            Slider(
                                value = wpm.toFloat(),
                                onValueChange = { wpm = it.roundToInt() },
                                valueRange = 40f..280f,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )
                        }

                        // Primary Transport & Feature Toggles Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Restart from top
                            IconButton(
                                onClick = {
                                    isPlaying = false
                                    activePauseRemaining = 0
                                    triggeredPauseIndices.clear()
                                    scope.launch { scrollState.animateScrollTo(0) }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Replay,
                                    contentDescription = "Reiniciar do Topo",
                                    tint = Color.White
                                )
                            }

                            // Horizontal Mirror (Beam Splitter Rig)
                            IconButton(
                                onClick = {
                                    mirrorH = !mirrorH
                                    toastNotification = if (mirrorH) {
                                        "Modo Espelho (Beam Splitter) ATIVADO"
                                    } else {
                                        "Modo Espelho DESATIVADO"
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Flip,
                                    contentDescription = "Espelhar Texto",
                                    tint = if (mirrorH) BroadcastAmber else Color.White
                                )
                            }

                            // Main Play / Pause Broadcast Button
                            FilledIconButton(
                                onClick = { triggerPlayWithOptionalCountdown() },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (isPlaying) BroadcastAmber else BroadcastCyan,
                                    contentColor = Color(0xFF041E2B)
                                ),
                                modifier = Modifier.size(58.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pausar" else "Iniciar Leitura",
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            // Voice-Activated Scrolling Toggle
                            IconButton(
                                onClick = {
                                    voiceScrollActive = !voiceScrollActive
                                    onUpdateSettings { s -> s.copy(voiceScrollEnabled = voiceScrollActive) }
                                    toastNotification = if (voiceScrollActive) {
                                        "Rolagem por Voz ATIVADA"
                                    } else {
                                        "Rolagem por Voz DESATIVADA"
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (voiceScrollActive) Icons.Default.Mic else Icons.Default.MicOff,
                                    contentDescription = "Rolagem por Voz",
                                    tint = if (voiceScrollActive) BroadcastGreen else Color.White
                                )
                            }

                            // Integrated Camera Overlay Toggle
                            IconButton(
                                onClick = {
                                    val nextState = !cameraOverlayActive
                                    cameraOverlayActive = nextState
                                    onUpdateSettings { s -> s.copy(useCameraOverlay = nextState) }
                                    if (nextState && !hasCameraPermission) {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.CAMERA,
                                                Manifest.permission.RECORD_AUDIO
                                            )
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (cameraOverlayActive) {
                                        Icons.Default.Videocam
                                    } else {
                                        Icons.Default.VideocamOff
                                    },
                                    contentDescription = "Câmera Integrada",
                                    tint = if (cameraOverlayActive) BroadcastRed else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // 6. Center Countdown Overlay (3, 2, 1...)
        AnimatedVisibility(
            visible = countdownRemaining > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .background(StudioBlack.copy(alpha = 0.9f))
                    .border(4.dp, BroadcastAmber, CircleShape)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$countdownRemaining",
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Black,
                        color = BroadcastAmber
                    )
                    Text(
                        text = "PREPARE-SE",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 7. Timed [PAUSA Xs] Active Badge Overlay
        AnimatedVisibility(
            visible = activePauseRemaining > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 110.dp)
        ) {
            Surface(
                color = BroadcastPurple,
                shape = RoundedCornerShape(50),
                shadowElevation = 8.dp,
                modifier = Modifier.clickable { activePauseRemaining = 0 }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⏸ PAUSA PROGRAMADA: ${activePauseRemaining}s (Toque para pular)",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // 8. Remote Control / Status Toast Pill
        AnimatedVisibility(
            visible = toastNotification != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 128.dp)
        ) {
            Surface(
                color = StudioCard.copy(alpha = 0.95f),
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.dp, BroadcastCyan)
            ) {
                Text(
                    text = toastNotification.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

private fun enterFloatingPipMode(context: Context): Boolean {
    val activity = context.findActivity() ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(9, 16))
                .build()
            activity.enterPictureInPictureMode(params)
        } catch (_: Exception) {
            false
        }
    } else {
        false
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

