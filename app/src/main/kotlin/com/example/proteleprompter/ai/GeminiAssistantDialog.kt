package com.example.proteleprompter.ai

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.proteleprompter.ui.theme.BroadcastAmber
import com.example.proteleprompter.ui.theme.BroadcastCyan
import com.example.proteleprompter.ui.theme.BroadcastGreen
import com.example.proteleprompter.ui.theme.BroadcastPurple
import com.example.proteleprompter.ui.theme.StudioBlack
import com.example.proteleprompter.ui.theme.StudioBorder
import com.example.proteleprompter.ui.theme.StudioCard
import com.example.proteleprompter.ui.theme.StudioSurface
import kotlinx.coroutines.launch
import java.util.Locale

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
    val scope = rememberCoroutineScope()
    val status by assistant.status.collectAsState()
    val downloadProgress by assistant.downloadProgressText.collectAsState()
    val messages by assistant.messages.collectAsState()
    val listState = rememberLazyListState()

    var inputPrompt by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        assistant.initializeModel()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val voiceInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                inputPrompt = spoken
            }
        }
    }

    fun submitPrompt(promptText: String) {
        val clean = promptText.trim()
        if (clean.isEmpty() || isGenerating) return
        inputPrompt = ""
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

        Spacer(Modifier.height(8.dp))

        // Natural-Language Chat Input + Voice Dictation + Send Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    try {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE,
                                Locale.forLanguageTag("pt-BR").toLanguageTag()
                            )
                            putExtra(
                                RecognizerIntent.EXTRA_PROMPT,
                                "Fale o que deseja no seu roteiro..."
                            )
                        }
                        voiceInputLauncher.launch(intent)
                    } catch (_: Exception) {
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(StudioCard)
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Falar com a IA",
                    tint = BroadcastAmber
                )
            }

            OutlinedTextField(
                value = inputPrompt,
                onValueChange = { inputPrompt = it },
                placeholder = {
                    Text(
                        "Converse com a IA (ex: 'Crie um vídeo sobre finanças')...",
                        fontSize = 13.sp
                    )
                },
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = { submitPrompt(inputPrompt) },
                enabled = inputPrompt.isNotBlank() && !isGenerating,
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

