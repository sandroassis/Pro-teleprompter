package com.example.proteleprompter.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.proteleprompter.ui.theme.BroadcastAmber
import com.example.proteleprompter.ui.theme.BroadcastCyan
import com.example.proteleprompter.ui.theme.BroadcastGreen
import com.example.proteleprompter.ui.theme.BroadcastRed
import com.example.proteleprompter.ui.theme.StudioBlack
import com.example.proteleprompter.ui.theme.StudioBorder
import com.example.proteleprompter.ui.theme.StudioCard
import com.example.proteleprompter.ui.theme.StudioSurface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Low-latency, real-time continuous microphone controller.
 * Reuses a persistent SpeechRecognizer instance (avoiding service re-binding overhead),
 * streams word-by-word partial & segment results immediately, and stays active across pauses
 * until the user explicitly taps the microphone button to finish.
 */
private class ContinuousAiMicController(
    private val context: Context,
    private val onTextUpdated: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var committedBaseText: String = ""
    private var latestPartialSegment: String = ""
    private var isRestartScheduled = false

    private val _isMicActive = MutableStateFlow(false)
    val isMicActive: StateFlow<Boolean> = _isMicActive.asStateFlow()

    private val recognitionIntent: Intent by lazy {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.forLanguageTag("pt-BR").toLanguageTag()
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Enable low-latency continuous dictation mode where supported
            putExtra("android.speech.extra.DICTATION_MODE", true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putExtra(
                    RecognizerIntent.EXTRA_ENABLE_FORMATTING,
                    RecognizerIntent.FORMATTING_OPTIMIZE_LATENCY
                )
            }
        }
    }

    fun startContinuousListening(existingTextInField: String) {
        committedBaseText = existingTextInField.trim()
        latestPartialSegment = ""
        isRestartScheduled = false
        _isMicActive.value = true
        mainHandler.post {
            ensureRecognizerCreated()
            startListeningImmediate()
        }
    }

    fun syncManualEdit(newText: String) {
        if (!_isMicActive.value) {
            committedBaseText = newText
        }
    }

    fun stopContinuousListening() {
        if (!_isMicActive.value && speechRecognizer == null) return
        _isMicActive.value = false
        isRestartScheduled = false
        mainHandler.removeCallbacksAndMessages(null)
        if (latestPartialSegment.isNotBlank()) {
            committedBaseText = combineText(committedBaseText, latestPartialSegment)
            latestPartialSegment = ""
            onTextUpdated(committedBaseText)
        }
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
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _isMicActive.value = false
            return
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isRestartScheduled = false
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                // Keep session alive; onResults or onError will seamlessly re-trigger listening
            }

            override fun onError(error: Int) {
                if (!_isMicActive.value) return
                // Commit any live partial words before seamless restart
                if (latestPartialSegment.isNotBlank()) {
                    committedBaseText = combineText(committedBaseText, latestPartialSegment)
                    latestPartialSegment = ""
                    onTextUpdated(committedBaseText)
                }
                // If recognizer service got busy/corrupted, recreate once; otherwise reuse immediately
                if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    recreateRecognizerAndListen(60L)
                } else {
                    scheduleImmediateListen(15L)
                }
            }

            override fun onResults(results: Bundle?) {
                if (!_isMicActive.value) return
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val finalSegment = matches?.firstOrNull()?.trim().orEmpty()
                val segmentToCommit = finalSegment.ifBlank { latestPartialSegment }

                if (segmentToCommit.isNotBlank()) {
                    committedBaseText = combineText(committedBaseText, segmentToCommit)
                    latestPartialSegment = ""
                    onTextUpdated(committedBaseText)
                }
                // Zero-delay continuation on the same recognizer instance
                scheduleImmediateListen(10L)
            }

            override fun onSegmentResults(segmentResults: Bundle) {
                if (!_isMicActive.value) return
                val matches = segmentResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val segment = matches?.firstOrNull()?.trim().orEmpty()
                if (segment.isNotBlank()) {
                    committedBaseText = combineText(committedBaseText, segment)
                    latestPartialSegment = ""
                    onTextUpdated(committedBaseText)
                }
            }

            override fun onEndOfSegmentedSession() {
                if (!_isMicActive.value) return
                scheduleImmediateListen(10L)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!_isMicActive.value) return
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()?.trim().orEmpty()
                if (partial.isNotBlank() && partial != latestPartialSegment) {
                    latestPartialSegment = partial
                    onTextUpdated(combineText(committedBaseText, partial))
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer = recognizer
    }

    private fun startListeningImmediate() {
        if (!_isMicActive.value) return
        try {
            ensureRecognizerCreated()
            speechRecognizer?.startListening(recognitionIntent)
        } catch (_: Exception) {
            recreateRecognizerAndListen(80L)
        }
    }

    private fun scheduleImmediateListen(delayMs: Long) {
        if (!_isMicActive.value || isRestartScheduled) return
        isRestartScheduled = true
        mainHandler.postDelayed({
            isRestartScheduled = false
            if (_isMicActive.value) {
                startListeningImmediate()
            }
        }, delayMs)
    }

    private fun recreateRecognizerAndListen(delayMs: Long) {
        if (!_isMicActive.value) return
        mainHandler.removeCallbacksAndMessages(null)
        isRestartScheduled = true
        mainHandler.postDelayed({
            isRestartScheduled = false
            if (!_isMicActive.value) return@postDelayed
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            }
            speechRecognizer = null
            ensureRecognizerCreated()
            startListeningImmediate()
        }, delayMs)
    }

    private fun combineText(base: String, addition: String): String {
        val cleanBase = base.trim()
        val cleanAdd = addition.trim()
        return when {
            cleanBase.isEmpty() -> cleanAdd
            cleanAdd.isEmpty() -> cleanBase
            else -> "$cleanBase $cleanAdd"
        }
    }
}

@Composable
fun GeminiAssistantDialog(
    assistant: OnDeviceGeminiAssistant,
    currentScriptTitle: String? = null,
    currentScriptContent: String? = null,
    onApplyScript: (title: String, scriptContent: String, startImmediately: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = StudioBlack,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .border(1.dp, BroadcastCyan.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
        ) {
            GeminiAssistantPanel(
                assistant = assistant,
                currentScriptTitle = currentScriptTitle,
                currentScriptContent = currentScriptContent,
                onApplyScript = onApplyScript,
                onClose = onDismiss,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun GeminiAssistantPanel(
    assistant: OnDeviceGeminiAssistant,
    currentScriptTitle: String? = null,
    currentScriptContent: String? = null,
    onApplyScript: (title: String, scriptContent: String, startImmediately: Boolean) -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by assistant.status.collectAsState()
    val downloadProgress by assistant.downloadProgressText.collectAsState()
    val messages by assistant.messages.collectAsState()
    val listState = rememberLazyListState()

    var inputField by remember { mutableStateOf(TextFieldValue("")) }
    var isGenerating by remember { mutableStateOf(false) }
    val inputScrollState = rememberScrollState()

    val micController = remember(context) {
        ContinuousAiMicController(context) { liveDictatedText ->
            inputField = TextFieldValue(
                text = liveDictatedText,
                selection = TextRange(liveDictatedText.length)
            )
        }
    }
    val isMicActive by micController.isMicActive.collectAsState()

    // Instant scroll to bottom whenever dictated text or line count updates
    LaunchedEffect(inputField.text, inputScrollState.maxValue) {
        if (isMicActive || inputField.selection.end >= inputField.text.length - 2) {
            inputScrollState.scrollTo(inputScrollState.maxValue)
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micController.startContinuousListening(inputField.text)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            micController.stopContinuousListening()
        }
    }

    LaunchedEffect(Unit) {
        assistant.initializeModel()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    fun toggleMicrophone() {
        if (isMicActive) {
            micController.stopContinuousListening()
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                micController.startContinuousListening(inputField.text)
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    fun submitPrompt(promptText: String) {
        if (isMicActive) {
            micController.stopContinuousListening()
        }
        val clean = promptText.trim()
        if (clean.isEmpty() || isGenerating) return
        inputField = TextFieldValue("")
        isGenerating = true
        scope.launch {
            assistant.sendUserMessage(
                userPrompt = clean,
                currentScriptTitle = currentScriptTitle,
                currentScriptContent = currentScriptContent
            )
            isGenerating = false
        }
    }

    Column(
        modifier = modifier
            .background(StudioBlack)
            .padding(14.dp)
    ) {
        // Header with On-Device Gemini Nano status badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(BroadcastCyan.copy(alpha = 0.18f))
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = BroadcastCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Assistente IA Gemini (No Aparelho)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = downloadProgress ?: status.badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (status.isHardwareAiCore) BroadcastGreen else BroadcastAmber
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (status == GeminiNanoStatus.DOWNLOADABLE) {
                    AssistChip(
                        onClick = {
                            scope.launch { assistant.downloadNanoModelIfNeeded() }
                        },
                        label = { Text("Baixar Nano", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
                IconButton(onClick = { assistant.clearConversation() }) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Limpar Conversa",
                        tint = Color.LightGray
                    )
                }
                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = StudioBorder
        )

        // Quick Natural-Language Suggestion Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val suggestions = if (!currentScriptContent.isNullOrBlank()) {
                listOf(
                    "Melhore o roteiro atual e adicione pausas",
                    "Adapte para Reels/TikTok de 30 segundos",
                    "Transforme em estilo de Telejornal",
                    "Crie um Pitch Comercial persuasivo"
                )
            } else {
                listOf(
                    "Crie um roteiro para YouTube sobre Inteligência Artificial",
                    "Roteiro curto de 30s para Reels sobre marketing digital",
                    "Notícia de telejornal sobre inovação e tecnologia",
                    "Pitch de vendas de 1 minuto para apresentar meu serviço"
                )
            }

            suggestions.forEach { suggestion ->
                AssistChip(
                    onClick = { submitPrompt(suggestion) },
                    label = {
                        Text(
                            text = suggestion,
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

        Spacer(Modifier.height(8.dp))

        // Conversation Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatBubbleItem(
                    message = msg,
                    hasExistingScript = !currentScriptContent.isNullOrBlank(),
                    onApplyScript = { title, content, startNow ->
                        onApplyScript(title, content, startNow)
                    }
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Active Continuous Microphone Banner
        AnimatedVisibility(visible = isMicActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BroadcastRed.copy(alpha = 0.20f))
                    .border(1.dp, BroadcastRed, RoundedCornerShape(10.dp))
                    .clickable { toggleMicrophone() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = BroadcastRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Microfone ativo em tempo real • Toque no microfone para finalizar",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Natural-Language Chat Input + Continuous Voice Dictation Toggle + Send Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { toggleMicrophone() },
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (isMicActive) BroadcastRed else StudioCard)
                    .border(
                        width = 1.5.dp,
                        color = if (isMicActive) Color.White else BroadcastAmber.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (isMicActive) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isMicActive) {
                        "Finalizar Microfone"
                    } else {
                        "Ativar Microfone Contínuo"
                    },
                    tint = if (isMicActive) Color.White else BroadcastAmber
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp, max = 210.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(StudioSurface)
                    .border(
                        width = 1.dp,
                        color = if (isMicActive) BroadcastCyan else StudioBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .verticalScroll(inputScrollState)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = inputField,
                    onValueChange = {
                        inputField = it
                        micController.syncManualEdit(it.text)
                    },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    ),
                    cursorBrush = SolidColor(BroadcastCyan),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (inputField.text.isEmpty()) {
                            Text(
                                text = if (isMicActive) {
                                    "Ouvindo em tempo real... fale normalmente e clique no microfone para parar"
                                } else {
                                    "Converse com a IA (ex: 'Crie um vídeo sobre finanças')..."
                                },
                                color = Color.Gray,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }

            Button(
                onClick = { submitPrompt(inputField.text) },
                enabled = inputField.text.isNotBlank() && !isGenerating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BroadcastCyan,
                    contentColor = Color(0xFF041E2B)
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF041E2B)
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(
    message: AiChatMessage,
    hasExistingScript: Boolean,
    onApplyScript: (title: String, scriptContent: String, startImmediately: Boolean) -> Unit
) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (message.isUser) {
        BroadcastCyan.copy(alpha = 0.22f)
    } else {
        StudioSurface
    }
    val borderColor = if (message.isUser) {
        BroadcastCyan.copy(alpha = 0.6f)
    } else {
        StudioBorder
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth(if (message.isUser) 0.85f else 0.96f)
                .border(1.dp, borderColor, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (message.isUser) "Você" else "✨ Gemini Nano (Roteirista IA)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (message.isUser) BroadcastCyan else BroadcastAmber
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    lineHeight = 20.sp
                )

                val scriptCandidate = message.extractedScript
                if (!message.isUser && !message.isStreaming && !scriptCandidate.isNullOrBlank()) {
                    val title = message.suggestedTitle ?: "Roteiro Gerado por IA"
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = StudioBorder
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onApplyScript(title, scriptCandidate, false) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (hasExistingScript) "Usar no Editor" else "Salvar Roteiro",
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = { onApplyScript(title, scriptCandidate, true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BroadcastGreen,
                                contentColor = Color(0xFF042217)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Abrir no Prompter",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
