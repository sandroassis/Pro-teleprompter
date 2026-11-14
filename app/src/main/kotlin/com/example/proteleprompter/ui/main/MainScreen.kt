package com.example.proteleprompter.ui.main

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavKey
import com.example.proteleprompter.About
import com.example.proteleprompter.CloudBackup
import com.example.proteleprompter.Editor
import com.example.proteleprompter.GeminiAi
import com.example.proteleprompter.Prompter
import com.example.proteleprompter.RemoteControl
import com.example.proteleprompter.StudioSettings
import com.example.proteleprompter.ai.ExternalAiProvider
import com.example.proteleprompter.ai.GeminiNanoStatus
import com.example.proteleprompter.ai.InstalledAiAppsBridge
import com.example.proteleprompter.data.Script
import com.example.proteleprompter.data.ScriptParser
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
import kotlin.math.roundToInt

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val windowInfo = rememberAdaptiveWindowInfo()
    val state by viewModel.uiState.collectAsState()
    val bannerMessage by viewModel.statusBanner.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val aiStatus by viewModel.geminiAssistant.status.collectAsState()
    val installedAiApps by viewModel.geminiAssistant.installedApps.collectAsState()
    val selectedAiApp by viewModel.geminiAssistant.selectedExternalApp.collectAsState()
    val cloudApiKey by viewModel.geminiAssistant.cloudApiKey.collectAsState()

    val hasAiReady = installedAiApps.isNotEmpty() ||
        cloudApiKey.isNotBlank() ||
        aiStatus == GeminiNanoStatus.AVAILABLE ||
        aiStatus == GeminiNanoStatus.CLOUD_GEMINI_PRO

    val activeAiShortLabel = when {
        cloudApiKey.isNotBlank() -> "Gemini 2.5"
        selectedAiApp != null -> selectedAiApp!!.displayName.replace("Google ", "")
        installedAiApps.isNotEmpty() -> installedAiApps.first().displayName.replace("Google ", "")
        aiStatus == GeminiNanoStatus.AVAILABLE -> "Gemini Nano"
        else -> null
    }

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showInstallGeminiPromptDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.geminiAssistant.refreshInstalledAppsAndConfig(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val handleOpenAiClick: () -> Unit = {
        viewModel.geminiAssistant.refreshInstalledAppsAndConfig(context)
        val currentlyReady = viewModel.geminiAssistant.installedApps.value.isNotEmpty() ||
            viewModel.geminiAssistant.cloudApiKey.value.isNotBlank() ||
            viewModel.geminiAssistant.status.value == GeminiNanoStatus.AVAILABLE ||
            viewModel.geminiAssistant.status.value == GeminiNanoStatus.CLOUD_GEMINI_PRO
        if (currentlyReady) {
            onItemClick(GeminiAi())
        } else {
            showInstallGeminiPromptDialog = true
        }
    }

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.showBanner(null)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = StudioBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                color = StudioSurface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = windowInfo.horizontalPadding, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ProTeleprompterBrandIcon(size = 38.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "PRO TELEPROMPTER",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        color = StudioCard,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = windowInfo.formFactor.label,
                                            fontSize = 9.sp,
                                            color = BroadcastAmber,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (activeAiShortLabel != null) {
                                        "Estúdio • IA $activeAiShortLabel Ativa • Câmera & Voz"
                                    } else {
                                        "Estúdio • IA Gemini / ChatGPT / Claude • Câmera & Voz"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BroadcastCyan,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Dedicated Screen Navigation Buttons in Top Bar
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = handleOpenAiClick,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "Tela IA Gemini",
                                    tint = if (hasAiReady) BroadcastAmber else Color(0xFF94A3B8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { onItemClick(CloudBackup) },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Default.CloudSync,
                                    contentDescription = "Tela Arquivos e Nuvem",
                                    tint = BroadcastGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { onItemClick(RemoteControl) },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Default.BluetoothConnected,
                                    contentDescription = "Tela Controle Remoto / Pedal",
                                    tint = BroadcastCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { onItemClick(StudioSettings) },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Tela Configurações do Estúdio",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { onItemClick(About) },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "Tela Sobre o App",
                                    tint = BroadcastAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExtendedFloatingActionButton(
                    onClick = handleOpenAiClick,
                    containerColor = StudioCard,
                    contentColor = if (hasAiReady) BroadcastAmber else Color(0xFFF59E0B),
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    text = {
                        Text(
                            text = when {
                                activeAiShortLabel != null -> "IA • $activeAiShortLabel"
                                else -> "Habilitar IA (Gemini)"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                )

                ExtendedFloatingActionButton(
                    onClick = {
                        val created = viewModel.createNewScript()
                        onItemClick(Editor(created.id))
                    },
                    containerColor = BroadcastCyan,
                    contentColor = Color(0xFF041E2B),
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Novo Roteiro", fontWeight = FontWeight.ExtraBold) }
                )
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (windowInfo.useNavigationRail) {
                AdaptiveStudioSidebar(
                    folders = state.folders,
                    selectedFolder = state.selectedFolder,
                    onlyFavorites = state.onlyFavorites,
                    onSelectFolder = viewModel::selectFolder,
                    onToggleFavorites = viewModel::toggleOnlyFavorites,
                    onNewFolder = { showNewFolderDialog = true },
                    onOpenGeminiAi = handleOpenAiClick,
                    onOpenAbout = { onItemClick(About) },
                    modifier = Modifier
                        .width(if (windowInfo.isTvDevice) 240.dp else 210.dp)
                        .fillMaxHeight()
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(windowInfo.gridColumns),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(
                    start = windowInfo.horizontalPadding,
                    end = windowInfo.horizontalPadding,
                    top = 12.dp,
                    bottom = 104.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Studio Telemetry Overview Card
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val totalWords = state.scripts.sumOf { it.wordCount }
                    val totalDurationSecs = state.scripts.sumOf { it.estimatedDurationSeconds }
                    val avgWpm = if (state.scripts.isNotEmpty()) {
                        state.scripts.map { it.wpm }.average().roundToInt()
                    } else {
                        140
                    }

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
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StudioStatColumn(
                                value = "${state.scripts.size}",
                                label = "Roteiros",
                                color = BroadcastCyan
                            )
                            StudioStatColumn(
                                value = "%02d:%02d".format(totalDurationSecs / 60, totalDurationSecs % 60),
                                label = "Tempo Total",
                                color = BroadcastGreen
                            )
                            StudioStatColumn(
                                value = "$avgWpm WPM",
                                label = "Ritmo Médio",
                                color = BroadcastAmber
                            )
                            StudioStatColumn(
                                value = "$totalWords",
                                label = "Palavras",
                                color = BroadcastPurple
                            )
                        }
                    }
                }

                // 2. Search Bar & Favorites Filter
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::updateSearchQuery,
                            placeholder = { Text("Buscar por título, texto ou pasta...") },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = BroadcastCyan)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )

                        if (!windowInfo.useNavigationRail) {
                            FilterChip(
                                selected = state.onlyFavorites,
                                onClick = viewModel::toggleOnlyFavorites,
                                label = { Text("Favoritos") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (state.onlyFavorites) {
                                            Icons.Default.Star
                                        } else {
                                            Icons.Default.StarBorder
                                        },
                                        contentDescription = null,
                                        tint = BroadcastAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                // 3. Folders / Projects Horizontal Bar (Compact Phone)
                if (!windowInfo.useNavigationRail) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            state.folders.forEach { folderName ->
                                val isSelected = state.selectedFolder.equals(folderName, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectFolder(folderName) },
                                    label = {
                                        Text(
                                            text = folderName,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (isSelected) BroadcastCyan else Color.Gray
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BroadcastCyan.copy(alpha = 0.2f),
                                        selectedLabelColor = BroadcastCyan
                                    )
                                )
                            }

                            AssistChip(
                                onClick = { showNewFolderDialog = true },
                                label = { Text("+ Pasta") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.CreateNewFolder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = BroadcastAmber
                                    )
                                }
                            )
                        }
                    }
                }

                // 4. Script Grid Cards
                if (state.filteredScripts.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = StudioSurface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Nenhum roteiro encontrado neste filtro",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Converse com a IA Gemini para gerar um roteiro completo ou importe um arquivo .txt.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                } else {
                    items(state.filteredScripts, key = { it.id }) { script ->
                        ScriptStudioCard(
                            script = script,
                            onStartPrompter = { onItemClick(Prompter(script.id)) },
                            onEdit = { onItemClick(Editor(script.id)) },
                            onToggleFavorite = { viewModel.toggleScriptFavorite(script.id) },
                            onDuplicate = { viewModel.duplicateScript(script.id) },
                            onDelete = { viewModel.deleteScript(script.id) }
                        )
                    }
                }
            }
        }
    }

    // Small input dialog only for typing a new folder name
    if (showNewFolderDialog) {
        var folderInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Criar Nova Pasta / Projeto") },
            text = {
                OutlinedTextField(
                    value = folderInput,
                    onValueChange = { folderInput = it },
                    label = { Text("Nome da pasta (ex: Podcast, Cliente X)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderInput.isNotBlank()) {
                            viewModel.addFolder(folderInput)
                            showNewFolderDialog = false
                        }
                    }
                ) {
                    Text("Criar Pasta")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showInstallGeminiPromptDialog) {
        AlertDialog(
            onDismissRequest = { showInstallGeminiPromptDialog = false },
            containerColor = StudioSurface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = BroadcastAmber
                    )
                    Text(
                        "Habilitar IA Potência Total",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Nenhum aplicativo oficial de IA (Google Gemini, ChatGPT ou Claude) foi detectado instalado neste aparelho Android.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFE2E8F0)
                    )
                    Text(
                        text = "Para habilitar o botão de IA com máxima inteligência na geração de roteiros de Teleprompter, recomendamos instalar o aplicativo oficial Google Gemini (ou conectar uma chave gratuita Gemini 2.5 direto no Estúdio IA).",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                    OutlinedButton(
                        onClick = {
                            showInstallGeminiPromptDialog = false
                            onItemClick(GeminiAi())
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Abrir Estúdio IA / Configurar Chave Gemini 2.5",
                            color = BroadcastCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showInstallGeminiPromptDialog = false
                        InstalledAiAppsBridge.openPlayStoreToInstall(
                            context,
                            ExternalAiProvider.GOOGLE_GEMINI
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BroadcastAmber,
                        contentColor = Color(0xFF1A1200)
                    )
                ) {
                    Text("📲 Baixar App Google Gemini", fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstallGeminiPromptDialog = false }) {
                    Text("Fechar", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
private fun AdaptiveStudioSidebar(
    folders: List<String>,
    selectedFolder: String,
    onlyFavorites: Boolean,
    onSelectFolder: (String) -> Unit,
    onToggleFavorites: () -> Unit,
    onNewFolder: () -> Unit,
    onOpenGeminiAi: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = StudioSurface,
        modifier = modifier.border(width = 1.dp, color = StudioBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOpenGeminiAi,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BroadcastAmber,
                    contentColor = Color(0xFF1F1300)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Roteirista IA", fontWeight = FontWeight.ExtraBold)
            }

            FilterChip(
                selected = onlyFavorites,
                onClick = onToggleFavorites,
                label = { Text("Apenas Favoritos") },
                leadingIcon = {
                    Icon(
                        imageVector = if (onlyFavorites) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = BroadcastAmber,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider(color = StudioBorder)

            Text(
                text = "PASTAS & PROJETOS",
                style = MaterialTheme.typography.labelSmall,
                color = BroadcastCyan,
                fontWeight = FontWeight.Bold
            )

            folders.forEach { folderName ->
                val isSelected = selectedFolder.equals(folderName, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectFolder(folderName) },
                    label = {
                        Text(
                            text = folderName,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isSelected) BroadcastCyan else Color.Gray
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedButton(
                onClick = onNewFolder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.CreateNewFolder,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("+ Nova Pasta")
            }

            HorizontalDivider(color = StudioBorder)

            OutlinedButton(
                onClick = onOpenAbout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = BroadcastAmber,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Sobre o App")
            }
        }
    }
}

@Composable
private fun ScriptStudioCard(
    script: Script,
    onStartPrompter: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val cleanPreview = remember(script.content) {
        ScriptParser.buildStyledScript(script.content, Color(0xFFCBD5E1)).text
            .replace("\n", " ")
            .trim()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
            .clickable { onEdit() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = BroadcastCyan.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = script.folder.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = BroadcastCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (script.mirrorHorizontal || script.mirrorVertical) {
                        Surface(
                            color = BroadcastAmber.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Flip,
                                    contentDescription = null,
                                    tint = BroadcastAmber,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "ESPELHO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BroadcastAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (script.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favoritar",
                        tint = if (script.isFavorite) BroadcastAmber else Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = script.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = cleanPreview,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        tint = BroadcastGreen,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = script.formattedDuration,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = BroadcastGreen
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Speed,
                        contentDescription = null,
                        tint = BroadcastCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${script.wpm} WPM",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }

                Text(
                    text = "${script.wordCount} pal.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = StudioBorder
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar Roteiro",
                            tint = Color.LightGray,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Duplicar Roteiro",
                            tint = Color.LightGray,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Excluir Roteiro",
                            tint = BroadcastRed.copy(alpha = 0.85f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Button(
                    onClick = onStartPrompter,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BroadcastCyan,
                        contentColor = Color(0xFF041E2B)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("TELEPROMPTER", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun StudioStatColumn(
    value: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF94A3B8)
        )
    }
}
