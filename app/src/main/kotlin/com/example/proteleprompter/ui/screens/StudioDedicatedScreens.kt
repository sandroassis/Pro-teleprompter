package com.example.proteleprompter.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.proteleprompter.ai.GeminiAssistantPanel
import com.example.proteleprompter.ai.OnDeviceGeminiAssistant
import com.example.proteleprompter.data.PrompterBackground
import com.example.proteleprompter.data.PrompterSettings
import com.example.proteleprompter.data.Script
import com.example.proteleprompter.hardware.RemoteInputBus
import com.example.proteleprompter.ui.adaptive.ProTeleprompterBrandIcon
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 1. TELA DEDICADA: Assistente IA Gemini Nano (Roteirista Conversacional)
 */
@Composable
fun GeminiAiScreen(
    assistant: OnDeviceGeminiAssistant,
    currentScript: Script? = null,
    onApplyScript: (title: String, scriptContent: String, startImmediately: Boolean) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = StudioBlack,
        topBar = {
            DedicatedScreenTopBar(
                title = "Estúdio Criador de Roteiros IA",
                subtitle = if (currentScript != null) {
                    "Editando: ${currentScript.title}"
                } else {
                    "Estúdio TV & Locução de Rádio"
                },
                icon = Icons.Default.AutoAwesome,
                iconTint = BroadcastAmber,
                onBack = onBack
            )
        }
    ) { innerPadding ->
        GeminiAssistantPanel(
            assistant = assistant,
            currentScriptTitle = currentScript?.title,
            currentScriptContent = currentScript?.content,
            onApplyScript = onApplyScript,
            onClose = null,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        )
    }
}

/**
 * 2. TELA DEDICADA: Importação de Roteiros (.txt) & Sincronização / Backup Nuvem (.json)
 */
@Composable
fun CloudBackupScreen(
    scriptsCount: Int,
    foldersCount: Int,
    onImportTxtScript: (fileName: String, content: String) -> Script,
    onExportBackupJson: () -> String,
    onRestoreBackupJson: (String) -> Boolean,
    onOpenImportedScript: (Script) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val windowInfo = rememberAdaptiveWindowInfo()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val importTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = resolveFileName(context, uri) ?: "Roteiro_Importado.txt"
            val text = readTextFromUri(context, uri)
            if (!text.isNullOrBlank()) {
                val created = onImportTxtScript(fileName, text)
                onOpenImportedScript(created)
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar("Não foi possível ler o arquivo selecionado")
                }
            }
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            val jsonPayload = onExportBackupJson()
            val ok = writeTextToUri(context, uri, jsonPayload)
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (ok) "Backup .json salvo com sucesso!" else "Falha ao salvar o arquivo de backup"
                )
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val raw = readTextFromUri(context, uri)
            val ok = !raw.isNullOrBlank() && onRestoreBackupJson(raw)
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (ok) "Backup restaurado com sucesso!" else "Arquivo de backup inválido"
                )
            }
        }
    }

    Scaffold(
        containerColor = StudioBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            DedicatedScreenTopBar(
                title = "Arquivos, Importação & Nuvem",
                subtitle = "Gerencie arquivos .txt e backups completos em .json",
                icon = Icons.Default.CloudSync,
                iconTint = BroadcastGreen,
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = windowInfo.horizontalPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current database overview card
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$scriptsCount",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = BroadcastCyan
                        )
                        Text(
                            text = "Roteiros Salvos",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.LightGray
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$foldersCount",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = BroadcastAmber
                        )
                        Text(
                            text = "Pastas de Projetos",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.LightGray
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Ativo",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = BroadcastGreen
                        )
                        Text(
                            text = "Armazenamento Local",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.LightGray
                        )
                    }
                }
            }

            // Section 1: Import External Script File
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FileOpen,
                            contentDescription = null,
                            tint = BroadcastAmber,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Importar Roteiro de Arquivo (.txt / .md)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Carregue laudas e textos do armazenamento interno, Google Drive ou Downloads.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            importTxtLauncher.launch(arrayOf("text/*", "application/octet-stream"))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BroadcastAmber,
                            contentColor = Color(0xFF1F1300)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Selecionar Arquivo de Roteiro (.txt)", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            // Section 2: Cloud Backup & Export
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = BroadcastCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Exportar & Sincronizar Backup Completo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Inclui todos os seus roteiros, pastas, favoritos, velocidades WPM e ajustes de estúdio.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            exportBackupLauncher.launch("pro_teleprompter_backup.json")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BroadcastCyan,
                            contentColor = Color(0xFF041E2B)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Salvar Arquivo de Backup (.json)", fontWeight = FontWeight.ExtraBold)
                    }

                    OutlinedButton(
                        onClick = {
                            val payload = onExportBackupJson()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_SUBJECT, "Backup Pro Teleprompter Studio")
                                putExtra(Intent.EXTRA_TEXT, payload)
                            }
                            context.startActivity(
                                Intent.createChooser(sendIntent, "Enviar Backup para Nuvem / Drive")
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Compartilhar para Google Drive / WhatsApp / E-mail")
                    }
                }
            }

            // Section 3: Restore Backup
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = BroadcastGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Restaurar Backup (.json)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Recupere todos os seus roteiros e pastas a partir de um backup salvo anteriormente.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            restoreBackupLauncher.launch(arrayOf("application/json", "text/*"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = BroadcastGreen)
                        Spacer(Modifier.width(8.dp))
                        Text("Selecionar Arquivo de Backup (.json) para Restaurar")
                    }
                }
            }
        }
    }
}

/**
 * 3. TELA DEDICADA: Controle Remoto Bluetooth, Pedal, TV & Testador de Teclas ao Vivo
 */
@Composable
fun RemoteControlScreen(
    volumeKeysEnabled: Boolean,
    onToggleVolumeKeys: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val windowInfo = rememberAdaptiveWindowInfo()
    val focusRequester = remember { FocusRequester() }
    var lastDetectedKeyName by remember { mutableStateOf("Pressione qualquer botão do seu controle, pedal ou teclado...") }
    var lastMappedStudioAction by remember { mutableStateOf("Aguardando sinal de entrada...") }

    DisposableEffect(Unit) {
        val prevActive = RemoteInputBus.isPrompterActive
        RemoteInputBus.isPrompterActive = true
        onDispose {
            RemoteInputBus.isPrompterActive = prevActive
        }
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
        }
        RemoteInputBus.commands.collect { cmd ->
            lastMappedStudioAction = "Ação reconhecida: ${cmd.label}"
        }
    }

    Scaffold(
        containerColor = StudioBlack,
        topBar = {
            DedicatedScreenTopBar(
                title = "Controle Remoto, Pedal & TV",
                subtitle = "Mapeamento Bluetooth, USB, Teclado e Testador de Sinais",
                icon = Icons.Default.BluetoothConnected,
                iconTint = BroadcastCyan,
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (native.action == KeyEvent.ACTION_DOWN) {
                        lastDetectedKeyName = "Tecla detectada: ${KeyEvent.keyCodeToString(native.keyCode)} (código ${native.keyCode})"
                        RemoteInputBus.handleKeyDown(native.keyCode, native)
                        true
                    } else {
                        false
                    }
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = windowInfo.horizontalPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Interactive Key Tester Card
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, BroadcastCyan, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Keyboard,
                            contentDescription = null,
                            tint = BroadcastAmber,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "TESTADOR DE CONTROLE EM TEMPO REAL",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = BroadcastAmber
                        )
                    }
                    Text(
                        text = lastDetectedKeyName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = lastMappedStudioAction,
                        style = MaterialTheme.typography.bodySmall,
                        color = BroadcastGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Volume keys toggle card
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Usar Botões Físicos de Volume para Velocidade",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Permite usar Volume + / Volume - (inclusive de controles de selfie Bluetooth) para ajustar o WPM.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = volumeKeysEnabled,
                        onCheckedChange = { enabled ->
                            RemoteInputBus.interceptVolumeKeys = enabled
                            onToggleVolumeKeys(enabled)
                        }
                    )
                }
            }

            // Full Mapping Reference Card
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Tabela de Atalhos do Estúdio Teleprompter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BroadcastCyan
                    )
                    HorizontalDivider(color = StudioBorder)
                    ShortcutDetailItem(
                        keyLabel = "Espaço / Enter / OK (TV) / Play-Pause",
                        actionLabel = "Iniciar ou Pausar Rolagem do Roteiro"
                    )
                    ShortcutDetailItem(
                        keyLabel = "Seta para Cima / Volume + / Tecla +",
                        actionLabel = "Aumentar Velocidade (+10 WPM)"
                    )
                    ShortcutDetailItem(
                        keyLabel = "Seta para Baixo / Volume - / Tecla -",
                        actionLabel = "Diminuir Velocidade (-10 WPM)"
                    )
                    ShortcutDetailItem(
                        keyLabel = "Seta Direita / PageDown / Próxima Faixa",
                        actionLabel = "Pular para o Próximo Tópico (===)"
                    )
                    ShortcutDetailItem(
                        keyLabel = "Seta Esquerda / PageUp / Faixa Anterior",
                        actionLabel = "Voltar ao Tópico Anterior (===)"
                    )
                    ShortcutDetailItem(
                        keyLabel = "Tecla M",
                        actionLabel = "Ativar / Desativar Modo Espelho (Beam Splitter)"
                    )
                    ShortcutDetailItem(
                        keyLabel = "Tecla R / Home",
                        actionLabel = "Reiniciar Roteiro do Topo"
                    )
                }
            }
        }
    }
}

/**
 * 4. TELA DEDICADA: Configurações Globais do Estúdio
 */
@Composable
fun StudioSettingsScreen(
    settings: PrompterSettings,
    onUpdateSettings: ((PrompterSettings) -> PrompterSettings) -> Unit,
    onOpenAboutScreen: () -> Unit,
    onBack: () -> Unit
) {
    val windowInfo = rememberAdaptiveWindowInfo()

    Scaffold(
        containerColor = StudioBlack,
        topBar = {
            DedicatedScreenTopBar(
                title = "Configurações de Estúdio",
                subtitle = "Calibração global de leitura, fundo, linha guia e automação",
                icon = Icons.Default.Settings,
                iconTint = BroadcastCyan,
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = windowInfo.horizontalPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 1: Countdown & Reading Guide Calibration
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Temporizador & Linha Guia de Leitura (Eye-Line)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BroadcastCyan
                    )

                    Text(
                        text = "Contagem regressiva antes de entrar no ar:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(0, 3, 5, 10).forEach { sec ->
                            FilterChip(
                                selected = settings.countdownSeconds == sec,
                                onClick = { onUpdateSettings { it.copy(countdownSeconds = sec) } },
                                label = { Text(if (sec == 0) "Sem contagem" else "${sec} segundos") }
                            )
                        }
                    }

                    HorizontalDivider(color = StudioBorder)

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Posição Vertical da Linha Guia", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${(settings.eyeLinePositionPercent * 100).roundToInt()}% do topo",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = BroadcastAmber
                            )
                        }
                        Slider(
                            value = settings.eyeLinePositionPercent,
                            onValueChange = { pos ->
                                onUpdateSettings { it.copy(eyeLinePositionPercent = pos) }
                            },
                            valueRange = 0.12f..0.72f
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Intensidade da Máscara Escura (Foco)", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${(settings.focusShadeAlpha * 100).roundToInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = BroadcastCyan
                            )
                        }
                        Slider(
                            value = settings.focusShadeAlpha,
                            onValueChange = { alpha ->
                                onUpdateSettings { it.copy(focusShadeAlpha = alpha) }
                            },
                            valueRange = 0f..0.92f
                        )
                    }
                }
            }

            // Card 2: Studio Background Theme
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Cor de Fundo do Monitor Teleprompter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BroadcastCyan
                    )

                    PrompterBackground.entries.forEach { bg ->
                        val isSelected = settings.backgroundTheme == bg
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) BroadcastCyan.copy(alpha = 0.16f) else StudioCard)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) BroadcastCyan else StudioBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onUpdateSettings { it.copy(backgroundTheme = bg) } }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(bg.colorHex))
                                        .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = bg.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = Color.White
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = BroadcastCyan
                                )
                            }
                        }
                    }
                }
            }

            // Card 3: Automation & Camera Defaults
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Automação de Leitura, Câmera & Voz",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BroadcastCyan
                    )

                    SettingToggleRow(
                        title = "Pausas Programadas Automáticas ([PAUSA Xs])",
                        subtitle = "Pausa a rolagem automaticamente pelo tempo exato quando a tag cruza a linha de leitura.",
                        checked = settings.timedPausesEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(timedPausesEnabled = enabled) }
                        }
                    )

                    HorizontalDivider(color = StudioBorder)

                    SettingToggleRow(
                        title = "Loop Contínuo ao Final do Roteiro",
                        subtitle = "Reinicia a leitura automaticamente do topo ao concluir o texto.",
                        checked = settings.autoLoop,
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(autoLoop = enabled) }
                        }
                    )

                    HorizontalDivider(color = StudioBorder)

                    SettingToggleRow(
                        title = "Grade de Enquadramento na Câmera (Regra dos Terços)",
                        subtitle = "Exibe linhas guia de composição quando a gravação de vídeo integrada estiver ativa.",
                        checked = settings.showCameraGrid,
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(showCameraGrid = enabled) }
                        }
                    )

                    HorizontalDivider(color = StudioBorder)

                    SettingToggleRow(
                        title = "Botões de Volume Controlam Velocidade (WPM)",
                        subtitle = "Permite acelerar ou desacelerar o texto usando as teclas físicas de volume.",
                        checked = settings.volumeKeysControlSpeed,
                        onCheckedChange = { enabled ->
                            onUpdateSettings { it.copy(volumeKeysControlSpeed = enabled) }
                        }
                    )
                }
            }

            // Card 4: Shortcut to Dedicated About Screen
            OutlinedButton(
                onClick = onOpenAboutScreen,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = BroadcastAmber)
                Spacer(Modifier.width(8.dp))
                Text("Sobre o Pro Teleprompter & Desenvolvedor", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * 5. TELA DEDICADA: Sobre o Aplicativo ("Sobre" contendo Desenvolvedor Sandro Assis, Versão e Informações)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val windowInfo = rememberAdaptiveWindowInfo()

    Scaffold(
        containerColor = StudioBlack,
        topBar = {
            DedicatedScreenTopBar(
                title = "Sobre o Aplicativo",
                subtitle = "Informações da versão, arquitetura e créditos de desenvolvimento",
                icon = Icons.Default.Info,
                iconTint = BroadcastAmber,
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = windowInfo.horizontalPadding, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Brand & Version Card
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BroadcastCyan.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF0E223B),
                                    StudioSurface
                                )
                            )
                        )
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ProTeleprompterBrandIcon(size = 82.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "PRO TELEPROMPTER",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text(
                        text = "Broadcast Studio & AI Script Co-Pilot",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BroadcastCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = BroadcastAmber.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BroadcastAmber)
                    ) {
                        Text(
                            text = "Versão 1.0.0 (Build 100 • Edição Profissional)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = BroadcastAmber,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Developer Credits Card (Sandro Assis)
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioCard),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, BroadcastAmber.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(BroadcastAmber.copy(alpha = 0.18f))
                            .border(1.5.dp, BroadcastAmber, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = BroadcastAmber,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DESENVOLVIDO POR",
                            style = MaterialTheme.typography.labelSmall,
                            color = BroadcastAmber,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Sandro Assis",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = "Verificado",
                                tint = BroadcastCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "Engenharia de Software & Produção Mobile Nativa Android",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }

            // Professional Features Included Card
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Recursos Profissionais Integrados",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BroadcastCyan
                    )
                    HorizontalDivider(color = StudioBorder)

                    AboutFeatureRow(
                        icon = Icons.Default.AutoAwesome,
                        tint = BroadcastAmber,
                        title = "IA Gemini Nano On-Device (AICore)",
                        description = "Criação e revisão de roteiros em linguagem natural por texto ou voz diretamente no aparelho."
                    )
                    AboutFeatureRow(
                        icon = Icons.Default.Videocam,
                        tint = BroadcastRed,
                        title = "Estúdio CameraX Integrado",
                        description = "Gravação de vídeo Full HD com áudio e texto sobreposto, câmera frontal/traseira e regra dos terços."
                    )
                    AboutFeatureRow(
                        icon = Icons.Default.Devices,
                        tint = BroadcastGreen,
                        title = "Design Adaptativo Multi-Tela",
                        description = "Interface otimizada automaticamente para Celulares, Tablets, Dobráveis e Smart TVs (Android TV)."
                    )
                    AboutFeatureRow(
                        icon = Icons.Default.BluetoothConnected,
                        tint = BroadcastCyan,
                        title = "Hardware Broadcast & Modo Espelho",
                        description = "Espelhamento X/Y para vidro refletor (Beam Splitter), rolagem por voz, janela flutuante PiP e controle Bluetooth/Pedal."
                    )
                }
            }

            // Technical Environment & System Info Card
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = null,
                            tint = BroadcastPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Ficha Técnica & Ambiente",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    HorizontalDivider(color = StudioBorder)
                    TechInfoRow("Desenvolvedor Responsável", "Sandro Assis")
                    TechInfoRow("Versão do Aplicativo", "1.0.0 (Código 1)")
                    TechInfoRow("Tecnologia Nativa", "Kotlin 2.3 + Jetpack Compose Material 3")
                    TechInfoRow("Motor de IA On-Device", "Google ML Kit GenAI Prompt API (Gemini Nano)")
                    TechInfoRow("Perfil de Tela Atual", "${windowInfo.formFactor.label} (${windowInfo.screenWidthDp}x${windowInfo.screenHeightDp}dp)")
                    TechInfoRow("Sistema Android", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                }
            }

            Text(
                text = "© 2026 Sandro Assis • Todos os direitos reservados.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun DedicatedScreenTopBar(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onBack: () -> Unit
) {
    val windowInfo = rememberAdaptiveWindowInfo()
    Surface(
        color = StudioSurface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = windowInfo.horizontalPadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = Color.White
                )
            }
            Spacer(Modifier.width(4.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.16f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = BroadcastCyan,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ShortcutDetailItem(
    keyLabel: String,
    actionLabel: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = keyLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = BroadcastAmber
        )
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8)
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AboutFeatureRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tint.copy(alpha = 0.16f))
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
private fun TechInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

private fun resolveFileName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else {
                null
            }
        }
    } catch (_: Exception) {
        null
    }
}

private fun readTextFromUri(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    } catch (_: Exception) {
        null
    }
}

private fun writeTextToUri(context: Context, uri: Uri, content: String): Boolean {
    return try {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
            it.write(content)
        }
        true
    } catch (_: Exception) {
        false
    }
}

