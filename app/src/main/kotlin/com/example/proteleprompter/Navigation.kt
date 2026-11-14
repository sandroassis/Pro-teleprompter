package com.example.proteleprompter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.proteleprompter.data.DataRepository
import com.example.proteleprompter.ui.editor.ScriptEditorScreen
import com.example.proteleprompter.ui.main.MainScreen
import com.example.proteleprompter.ui.main.MainScreenViewModel
import com.example.proteleprompter.ui.prompter.PrompterScreen
import com.example.proteleprompter.ui.screens.AboutScreen
import com.example.proteleprompter.ui.screens.CloudBackupScreen
import com.example.proteleprompter.ui.screens.GeminiAiScreen
import com.example.proteleprompter.ui.screens.RemoteControlScreen
import com.example.proteleprompter.ui.screens.StudioSettingsScreen

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val repository = remember(context) { DataRepository.getInstance(context) }
    val mainViewModel: MainScreenViewModel = viewModel {
        MainScreenViewModel(repository)
    }
    val dashboardState by mainViewModel.uiState.collectAsState()
    val backStack = rememberNavBackStack(Main)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Main> {
                MainScreen(
                    onItemClick = { navKey -> backStack.add(navKey) },
                    viewModel = mainViewModel
                )
            }

            entry<Editor> { key ->
                val script = mainViewModel.getScript(key.scriptId)
                    ?: dashboardState.scripts.firstOrNull()
                    ?: mainViewModel.createNewScript()

                ScriptEditorScreen(
                    initialScript = script,
                    availableFolders = dashboardState.folders,
                    geminiAssistant = mainViewModel.geminiAssistant,
                    onSave = { updated -> mainViewModel.saveScript(updated) },
                    onStartPrompter = { saved ->
                        mainViewModel.saveScript(saved)
                        backStack.add(Prompter(saved.id))
                    },
                    onOpenAiScreen = { saved ->
                        mainViewModel.saveScript(saved)
                        backStack.add(GeminiAi(saved.id))
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }

            entry<Prompter> { key ->
                val script = mainViewModel.getScript(key.scriptId)
                    ?: dashboardState.scripts.firstOrNull()
                    ?: mainViewModel.createNewScript()

                PrompterScreen(
                    script = script,
                    initialSettings = dashboardState.settings,
                    onSaveScriptChanges = { updated -> mainViewModel.saveScript(updated) },
                    onUpdateSettings = { transform -> mainViewModel.updateSettings(transform) },
                    onBack = { backStack.removeLastOrNull() }
                )
            }

            entry<GeminiAi> { key ->
                val existingScript = key.scriptId?.let { mainViewModel.getScript(it) }
                GeminiAiScreen(
                    assistant = mainViewModel.geminiAssistant,
                    currentScript = existingScript,
                    onApplyScript = { title, content, startImmediately ->
                        val targetScript = if (existingScript != null) {
                            val updated = existingScript.copy(
                                title = if (existingScript.title.isBlank() || existingScript.title == "Novo Roteiro de Estúdio") {
                                    title
                                } else {
                                    existingScript.title
                                },
                                content = content
                            )
                            mainViewModel.saveScript(updated)
                            updated
                        } else {
                            mainViewModel.createScriptFromAi(title, content)
                        }

                        backStack.removeLastOrNull()
                        if (startImmediately) {
                            mainViewModel.updateSettings { it.copy(useCameraOverlay = true) }
                            backStack.add(Prompter(targetScript.id))
                        } else if (existingScript == null) {
                            backStack.add(Editor(targetScript.id))
                        }
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }

            entry<CloudBackup> {
                CloudBackupScreen(
                    scriptsCount = dashboardState.scripts.size,
                    foldersCount = (dashboardState.folders.size - 1).coerceAtLeast(1),
                    onImportTxtScript = { fileName, content ->
                        mainViewModel.importScriptFromText(fileName, content)
                    },
                    onExportBackupJson = { mainViewModel.exportBackupJson() },
                    onRestoreBackupJson = { raw -> mainViewModel.restoreBackupJson(raw) },
                    onOpenImportedScript = { imported ->
                        backStack.removeLastOrNull()
                        backStack.add(Editor(imported.id))
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }

            entry<RemoteControl> {
                RemoteControlScreen(
                    volumeKeysEnabled = dashboardState.settings.volumeKeysControlSpeed,
                    onToggleVolumeKeys = { enabled ->
                        mainViewModel.updateSettings { it.copy(volumeKeysControlSpeed = enabled) }
                    },
                    onBack = { backStack.removeLastOrNull() }
                )
            }

            entry<StudioSettings> {
                StudioSettingsScreen(
                    settings = dashboardState.settings,
                    onUpdateSettings = { transform -> mainViewModel.updateSettings(transform) },
                    onOpenAboutScreen = { backStack.add(About) },
                    onBack = { backStack.removeLastOrNull() }
                )
            }

            entry<About> {
                AboutScreen(
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}
