package com.example.proteleprompter.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proteleprompter.ai.OnDeviceGeminiAssistant
import com.example.proteleprompter.data.DataRepository
import com.example.proteleprompter.data.PrompterSettings
import com.example.proteleprompter.data.Script
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StudioDashboardState(
    val scripts: List<Script> = emptyList(),
    val filteredScripts: List<Script> = emptyList(),
    val folders: List<String> = DataRepository.defaultFolders(),
    val selectedFolder: String = "Todos",
    val searchQuery: String = "",
    val onlyFavorites: Boolean = false,
    val settings: PrompterSettings = PrompterSettings()
)

class MainScreenViewModel(
    private val repository: DataRepository = DataRepository.getInstance()
) : ViewModel() {

    val geminiAssistant = OnDeviceGeminiAssistant()

    private val _selectedFolder = MutableStateFlow("Todos")
    private val _searchQuery = MutableStateFlow("")
    private val _onlyFavorites = MutableStateFlow(false)
    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    val uiState: StateFlow<StudioDashboardState> = combine(
        repository.scripts,
        repository.folders,
        repository.settings,
        _selectedFolder,
        combine(_searchQuery, _onlyFavorites) { q, fav -> q to fav }
    ) { scripts, folders, settings, folder, (query, onlyFav) ->
        val filtered = scripts.filter { script ->
            val matchesFolder = folder == "Todos" || script.folder.equals(folder, ignoreCase = true)
            val matchesFav = !onlyFav || script.isFavorite
            val matchesQuery = query.isBlank() ||
                script.title.contains(query, ignoreCase = true) ||
                script.content.contains(query, ignoreCase = true) ||
                script.folder.contains(query, ignoreCase = true)
            matchesFolder && matchesFav && matchesQuery
        }.sortedByDescending { it.updatedAt }

        StudioDashboardState(
            scripts = scripts,
            filteredScripts = filtered,
            folders = listOf("Todos") + folders,
            selectedFolder = folder,
            searchQuery = query,
            onlyFavorites = onlyFav,
            settings = settings
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudioDashboardState(
            scripts = repository.scripts.value,
            filteredScripts = repository.scripts.value,
            folders = listOf("Todos") + repository.folders.value,
            settings = repository.settings.value
        )
    )

    fun selectFolder(folder: String) {
        _selectedFolder.value = folder
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleOnlyFavorites() {
        _onlyFavorites.value = !_onlyFavorites.value
    }

    fun toggleScriptFavorite(scriptId: String) {
        repository.toggleFavorite(scriptId)
    }

    fun createNewScript(folder: String? = null): Script {
        val targetFolder = folder
            ?.takeIf { it != "Todos" }
            ?: _selectedFolder.value.takeIf { it != "Todos" }
            ?: "Geral"
        return repository.createNewScript(folder = targetFolder)
    }

    fun createScriptFromAi(title: String, content: String): Script {
        val targetFolder = _selectedFolder.value.takeIf { it != "Todos" } ?: "Geral"
        val created = repository.createNewScript(
            title = title.ifBlank { "Roteiro IA Gemini" },
            folder = targetFolder,
            initialContent = content
        )
        showBanner("Roteiro '${created.title}' criado com IA Gemini!")
        return created
    }

    fun duplicateScript(scriptId: String) {
        val copy = repository.duplicateScript(scriptId)
        if (copy != null) {
            showBanner("Roteiro duplicado: ${copy.title}")
        }
    }

    fun deleteScript(scriptId: String) {
        repository.deleteScript(scriptId)
        showBanner("Roteiro removido")
    }

    fun addFolder(name: String) {
        repository.addFolder(name)
        _selectedFolder.value = name.trim()
        showBanner("Pasta '$name' criada")
    }

    fun getScript(scriptId: String): Script? = repository.getScriptById(scriptId)

    fun saveScript(script: Script) {
        repository.saveScript(script)
        showBanner("Roteiro salvo com sucesso")
    }

    fun updateSettings(transform: (PrompterSettings) -> PrompterSettings) {
        repository.updateSettings(transform)
    }

    fun importScriptFromText(fileName: String, content: String): Script {
        val targetFolder = _selectedFolder.value.takeIf { it != "Todos" } ?: "Geral"
        val imported = repository.importTextFile(fileName, content, targetFolder)
        showBanner("Roteiro '${imported.title}' importado!")
        return imported
    }

    fun exportBackupJson(): String = repository.exportBackupJson()

    fun restoreBackupJson(rawJson: String): Boolean {
        val ok = repository.restoreBackupJson(rawJson)
        if (ok) {
            showBanner("Backup restaurado com sucesso!")
        } else {
            showBanner("Arquivo de backup inválido")
        }
        return ok
    }

    fun showBanner(message: String?) {
        _statusBanner.value = message
    }
}
