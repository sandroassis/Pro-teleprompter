package com.example.proteleprompter.ui.editor

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.proteleprompter.ai.GeminiAssistantDialog
import com.example.proteleprompter.ai.GeminiAssistantPanel
import com.example.proteleprompter.ai.OnDeviceGeminiAssistant
import com.example.proteleprompter.data.Script
import com.example.proteleprompter.data.ScriptParser
import com.example.proteleprompter.ui.adaptive.rememberAdaptiveWindowInfo
import com.example.proteleprompter.ui.theme.BroadcastAmber
import com.example.proteleprompter.ui.theme.BroadcastCyan
import com.example.proteleprompter.ui.theme.BroadcastGreen
import com.example.proteleprompter.ui.theme.BroadcastPurple
import com.example.proteleprompter.ui.theme.BroadcastRed
import com.example.proteleprompter.ui.theme.StudioBlack
import com.example.proteleprompter.ui.theme.StudioBorder
import com.example.proteleprompter.ui.theme.StudioCard
import com.example.proteleprompter.ui.theme.StudioSurface
import kotlin.math.roundToInt

@Composable
fun ScriptEditorScreen(
    initialScript: Script,
    availableFolders: List<String>,
    geminiAssistant: OnDeviceGeminiAssistant,
    onSave: (Script) -> Unit,
    onStartPrompter: (Script) -> Unit,
    onOpenAiScreen: (Script) -> Unit,
    onBack: () -> Unit
) {
    val windowInfo = rememberAdaptiveWindowInfo()

    var title by remember(initialScript.id) { mutableStateOf(initialScript.title) }
    var folder by remember(initialScript.id) { mutableStateOf(initialScript.folder) }
    var textFieldValue by remember(initialScript.id) {
        mutableStateOf(TextFieldValue(initialScript.content))
    }
    var wpm by remember(initialScript.id) { mutableIntStateOf(initialScript.wpm) }
    var fontSizeSp by remember(initialScript.id) { mutableIntStateOf(initialScript.fontSizeSp) }
    var lineHeightMultiplier by remember(initialScript.id) {
        mutableFloatStateOf(initialScript.lineHeightMultiplier)
    }
    var sideMarginPercent by remember(initialScript.id) {
        mutableIntStateOf(initialScript.sideMarginPercent)
    }
    var mirrorHorizontal by remember(initialScript.id) {
        mutableStateOf(initialScript.mirrorHorizontal)
    }
    var mirrorVertical by remember(initialScript.id) {
        mutableStateOf(initialScript.mirrorVertical)
    }

    var showConfigPanel by remember { mutableStateOf(false) }
    var showFormattedPreview by remember { mutableStateOf(false) }
    var showAiDialogOnPhone by remember { mutableStateOf(false) }
    var splitRightPaneShowsAi by remember { mutableStateOf(true) }

    val draftScript = remember(
        title,
        folder,
        textFieldValue.text,
        wpm,
        fontSizeSp,
        lineHeightMultiplier,
        sideMarginPercent,
        mirrorHorizontal,
        mirrorVertical
    ) {
        initialScript.copy(
            title = title.ifBlank { "Roteiro sem título" },
            folder = folder.ifBlank { "Geral" },
            content = textFieldValue.text,
            wpm = wpm,
            fontSizeSp = fontSizeSp,
            lineHeightMultiplier = lineHeightMultiplier,
            sideMarginPercent = sideMarginPercent,
            mirrorHorizontal = mirrorHorizontal,
            mirrorVertical = mirrorVertical
        )
    }

    fun insertSnippet(prefix: String, suffix: String = "", placeholder: String = "") {
        val currentText = textFieldValue.text
        val selection = textFieldValue.selection
        val start = selection.min.coerceIn(0, currentText.length)
        val end = selection.max.coerceIn(0, currentText.length)
        val selectedText = if (start < end) currentText.substring(start, end) else placeholder
        val replacement = "$prefix$selectedText$suffix"
        val newText = currentText.replaceRange(start, end, replacement)
        val newCursor = start + replacement.length
        textFieldValue = TextFieldValue(
            text = newText,
            selection = TextRange(newCursor)
        )
    }

    Scaffold(
        containerColor = StudioBlack,
        topBar = {
            Surface(
                color = StudioSurface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = windowInfo.horizontalPadding, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                onSave(draftScript)
                                onBack()
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                tint = Color.White
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Editor de Estúdio",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                            Text(
                                text = "${draftScript.wordCount} pal. • ${draftScript.formattedDuration} (${draftScript.wpm} WPM)",
                                style = MaterialTheme.typography.labelSmall,
                                color = BroadcastCyan,
                                maxLines = 1
                            )
                        }

                        IconButton(
                            onClick = {
                                if (windowInfo.useSplitPane) {
                                    splitRightPaneShowsAi = true
                                } else {
                                    onSave(draftScript)
                                    onOpenAiScreen(draftScript)
                                }
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "IA Gemini",
                                tint = BroadcastAmber
                            )
                        }

                        if (!windowInfo.useSplitPane) {
                            IconButton(
                                onClick = { showFormattedPreview = !showFormattedPreview },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = "Alternar Prévia Formatada",
                                    tint = if (showFormattedPreview) BroadcastAmber else Color.White
                                )
                            }
                        }

                        IconButton(
                            onClick = { showConfigPanel = !showConfigPanel },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Ajustes do Roteiro",
                                tint = if (showConfigPanel) BroadcastCyan else Color.White
                            )
                        }

                        Button(
                            onClick = {
                                onSave(draftScript)
                                onStartPrompter(draftScript)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BroadcastRed,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "NO AR",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Live Studio Telemetry Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StudioCard.copy(alpha = 0.7f))
                            .padding(horizontal = windowInfo.horizontalPadding, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MetricBadge(
                            icon = Icons.Default.Timer,
                            label = "Duração",
                            value = draftScript.formattedDuration,
                            tint = BroadcastGreen
                        )
                        MetricBadge(
                            icon = Icons.Default.Speed,
                            label = "Ritmo",
                            value = "${draftScript.wpm} WPM",
                            tint = BroadcastCyan
                        )
                        MetricBadge(
                            icon = Icons.Default.PauseCircle,
                            label = "Pausas",
                            value = "${draftScript.totalPauseSeconds}s",
                            tint = BroadcastPurple
                        )
                        MetricBadge(
                            icon = Icons.Default.BookmarkAdd,
                            label = "Tópicos",
                            value = "${draftScript.topicMarkers.size}",
                            tint = BroadcastAmber
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = StudioSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = windowInfo.horizontalPadding, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onSave(draftScript)
                            onBack()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Salvar Roteiro")
                    }

                    Button(
                        onClick = {
                            onSave(draftScript)
                            onStartPrompter(draftScript)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BroadcastCyan,
                            contentColor = Color(0xFF041E2B)
                        ),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Iniciar Teleprompter", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = windowInfo.horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // LEFT / MAIN EDITOR PANE
            Column(
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight()
            ) {
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título do Roteiro / Episódio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pasta:",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.LightGray
                    )
                    availableFolders.filter { it != "Todos" }.forEach { item ->
                        FilterChip(
                            selected = folder.equals(item, ignoreCase = true),
                            onClick = { folder = item },
                            label = { Text(item) }
                        )
                    }
                }

                AnimatedVisibility(visible = showConfigPanel) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StudioCard),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Parâmetros de Leitura deste Roteiro",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = BroadcastCyan
                            )

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Velocidade de Leitura (WPM)", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "$wpm palavras/min",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = BroadcastAmber
                                    )
                                }
                                Slider(
                                    value = wpm.toFloat(),
                                    onValueChange = { wpm = it.roundToInt() },
                                    valueRange = 60f..260f
                                )
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Tamanho da Fonte no Estúdio", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "${fontSizeSp}sp",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = BroadcastCyan
                                    )
                                }
                                Slider(
                                    value = fontSizeSp.toFloat(),
                                    onValueChange = { fontSizeSp = it.roundToInt() },
                                    valueRange = 22f..68f
                                )
                            }

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Margens Laterais (Centralizar Olhar)", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "$sideMarginPercent%",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = BroadcastGreen
                                    )
                                }
                                Slider(
                                    value = sideMarginPercent.toFloat(),
                                    onValueChange = { sideMarginPercent = it.roundToInt() },
                                    valueRange = 0f..28f
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Flip,
                                        contentDescription = null,
                                        tint = BroadcastAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Modo Espelho Horizontal (Beam Splitter)", style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(
                                    checked = mirrorHorizontal,
                                    onCheckedChange = { mirrorHorizontal = it }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Espelhar Verticalmente (Flip Y)", style = MaterialTheme.typography.bodySmall)
                                Switch(
                                    checked = mirrorVertical,
                                    onCheckedChange = { mirrorVertical = it }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Inserir Marcadores de Estúdio (Pausas, Ênfase e Tópicos):",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
                )
                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CueTagChip(
                        label = "+ Pausa 2s",
                        icon = Icons.Default.PauseCircle,
                        tint = BroadcastPurple,
                        onClick = { insertSnippet("\n[PAUSA 2s]\n") }
                    )
                    CueTagChip(
                        label = "+ Pausa 5s",
                        icon = Icons.Default.PauseCircle,
                        tint = BroadcastPurple,
                        onClick = { insertSnippet("\n[PAUSA 5s]\n") }
                    )
                    CueTagChip(
                        label = "Destaque",
                        icon = Icons.Default.Highlight,
                        tint = BroadcastAmber,
                        onClick = { insertSnippet("[DESTAQUE]", "[/DESTAQUE]", "frase importante") }
                    )
                    CueTagChip(
                        label = "Alerta / Ênfase",
                        icon = Icons.Default.WarningAmber,
                        tint = BroadcastRed,
                        onClick = { insertSnippet("[ALERTA]", "[/ALERTA]", "atenção máxima") }
                    )
                    CueTagChip(
                        label = "Dica de Cena",
                        icon = Icons.Default.RecordVoiceOver,
                        tint = BroadcastCyan,
                        onClick = { insertSnippet("[DICA]", "[/DICA]", "Olhar para câmera 2") }
                    )
                    CueTagChip(
                        label = "+ Tópico ===",
                        icon = Icons.Default.BookmarkAdd,
                        tint = BroadcastGreen,
                        onClick = { insertSnippet("\n=== NOVO BLOCO ===\n") }
                    )
                }

                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(StudioSurface)
                        .border(1.dp, StudioBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    if (showFormattedPreview && !windowInfo.useSplitPane) {
                        val styled = remember(textFieldValue.text) {
                            ScriptParser.buildStyledScript(textFieldValue.text, Color.White)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "PRÉVIA DE LEITURA DO TELEPROMPTER",
                                style = MaterialTheme.typography.labelSmall,
                                color = BroadcastAmber,
                                fontWeight = FontWeight.Bold
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = StudioBorder
                            )
                            Text(
                                text = styled,
                                fontSize = 22.sp,
                                lineHeight = 32.sp
                            )
                        }
                    } else {
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { textFieldValue = it },
                            textStyle = TextStyle(
                                color = Color(0xFFF8FAFC),
                                fontSize = 18.sp,
                                lineHeight = 27.sp
                            ),
                            cursorBrush = SolidColor(BroadcastCyan),
                            modifier = Modifier.fillMaxSize(),
                            decorationBox = { innerTextField ->
                                if (textFieldValue.text.isEmpty()) {
                                    Text(
                                        text = "Digite seu roteiro ou peça para a IA Gemini criar automaticamente em linguagem natural...",
                                        color = Color.Gray,
                                        fontSize = 16.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            // RIGHT SPLIT PANE (Only on Tablet / Foldable / Android TV!)
            if (windowInfo.useSplitPane) {
                Column(
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight()
                        .padding(vertical = 10.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(StudioSurface)
                        .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StudioCard)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = splitRightPaneShowsAi,
                            onClick = { splitRightPaneShowsAi = true },
                            label = { Text("✨ Co-Piloto IA Gemini") }
                        )
                        FilterChip(
                            selected = !splitRightPaneShowsAi,
                            onClick = { splitRightPaneShowsAi = false },
                            label = { Text("Prévia do Teleprompter") }
                        )
                    }

                    if (splitRightPaneShowsAi) {
                        GeminiAssistantPanel(
                            assistant = geminiAssistant,
                            currentScriptTitle = title,
                            currentScriptContent = textFieldValue.text,
                            onApplyScript = { newTitle, newContent, startNow ->
                                if (title.isBlank() || title == "Novo Roteiro de Estúdio") {
                                    title = newTitle
                                }
                                textFieldValue = TextFieldValue(newContent)
                                val updated = draftScript.copy(title = title, content = newContent)
                                onSave(updated)
                                if (startNow) {
                                    onStartPrompter(updated)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val styled = remember(textFieldValue.text) {
                            ScriptParser.buildStyledScript(textFieldValue.text, Color.White)
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "PRÉVIA EM TEMPO REAL DO ESTÚDIO",
                                style = MaterialTheme.typography.labelSmall,
                                color = BroadcastAmber,
                                fontWeight = FontWeight.Bold
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = StudioBorder
                            )
                            Text(
                                text = styled,
                                fontSize = 24.sp,
                                lineHeight = 36.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Gemini Assistant on Compact Phones
    if (showAiDialogOnPhone) {
        GeminiAssistantDialog(
            assistant = geminiAssistant,
            currentScriptTitle = title,
            currentScriptContent = textFieldValue.text,
            onApplyScript = { newTitle, newContent, startNow ->
                showAiDialogOnPhone = false
                if (title.isBlank() || title == "Novo Roteiro de Estúdio") {
                    title = newTitle
                }
                textFieldValue = TextFieldValue(newContent)
                val updated = draftScript.copy(title = title, content = newContent)
                onSave(updated)
                if (startNow) {
                    onStartPrompter(updated)
                }
            },
            onDismiss = { showAiDialogOnPhone = false }
        )
    }
}

@Composable
private fun MetricBadge(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(4.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8),
                fontSize = 10.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun CueTagChip(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = StudioCard
        )
    )
}
