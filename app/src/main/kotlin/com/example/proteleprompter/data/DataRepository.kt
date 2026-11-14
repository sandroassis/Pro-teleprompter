package com.example.proteleprompter.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class DataRepository(private val storageFile: File? = null) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val _folders = MutableStateFlow(defaultFolders())
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    private val _scripts = MutableStateFlow(defaultScripts())
    val scripts: StateFlow<List<Script>> = _scripts.asStateFlow()

    private val _settings = MutableStateFlow(PrompterSettings())
    val settings: StateFlow<PrompterSettings> = _settings.asStateFlow()

    init {
        loadFromDisk()
    }

    fun getScriptById(id: String): Script? {
        return _scripts.value.find { it.id == id }
    }

    fun saveScript(script: Script) {
        val updatedScript = script.copy(updatedAt = System.currentTimeMillis())
        _scripts.update { current ->
            val index = current.indexOfFirst { it.id == updatedScript.id }
            if (index >= 0) {
                current.toMutableList().apply { set(index, updatedScript) }
            } else {
                listOf(updatedScript) + current
            }
        }
        if (updatedScript.folder.isNotBlank() && updatedScript.folder !in _folders.value) {
            _folders.update { it + updatedScript.folder }
        }
        persistToDisk()
    }

    fun createNewScript(
        title: String = "Novo Roteiro de Estúdio",
        folder: String = "Geral",
        initialContent: String = ""
    ): Script {
        val newScript = Script(
            id = UUID.randomUUID().toString(),
            title = title,
            folder = folder.ifBlank { "Geral" },
            content = initialContent.ifBlank {
                """
                === ABERTURA ===
                [DICA]Olhar firme na lente, sorriso leve[/DICA]
                Olá pessoal! Sejam muito bem-vindos a mais um vídeo especial no nosso canal.
                [PAUSA 2s]

                === DESENVOLVIMENTO ===
                Hoje nós vamos falar sobre [DESTAQUE]como gravar com segurança e naturalidade[/DESTAQUE] usando um teleprompter profissional.

                Lembre-se: [ALERTA]mantenha a linha de leitura próxima à câmera[/ALERTA] para que sua audiência sinta conexão visual direta com você!
                [PAUSA 3s]

                === ENCERRAMENTO ===
                Se este conteúdo te ajudou, inscreva-se e compartilhe com sua equipe de produção!
                """.trimIndent()
            },
            updatedAt = System.currentTimeMillis()
        )
        saveScript(newScript)
        return newScript
    }

    fun duplicateScript(scriptId: String): Script? {
        val original = getScriptById(scriptId) ?: return null
        val copy = original.copy(
            id = UUID.randomUUID().toString(),
            title = "${original.title} (Cópia)",
            updatedAt = System.currentTimeMillis()
        )
        saveScript(copy)
        return copy
    }

    fun deleteScript(scriptId: String) {
        _scripts.update { list -> list.filterNot { it.id == scriptId } }
        persistToDisk()
    }

    fun toggleFavorite(scriptId: String) {
        _scripts.update { list ->
            list.map { script ->
                if (script.id == scriptId) script.copy(isFavorite = !script.isFavorite) else script
            }
        }
        persistToDisk()
    }

    fun addFolder(folderName: String) {
        val cleaned = folderName.trim()
        if (cleaned.isNotEmpty() && cleaned !in _folders.value) {
            _folders.update { it + cleaned }
            persistToDisk()
        }
    }

    fun deleteFolder(folderName: String) {
        if (folderName == "Geral") return
        _folders.update { list -> list.filterNot { it == folderName } }
        _scripts.update { list ->
            list.map { if (it.folder == folderName) it.copy(folder = "Geral") else it }
        }
        persistToDisk()
    }

    fun updateSettings(transform: (PrompterSettings) -> PrompterSettings) {
        _settings.update(transform)
        persistToDisk()
    }

    fun importTextFile(fileName: String, rawText: String, targetFolder: String = "Geral"): Script {
        val cleanTitle = fileName
            .substringBeforeLast(".")
            .replace("_", " ")
            .replace("-", " ")
            .trim()
            .ifEmpty { "Roteiro Importado" }

        val imported = Script(
            id = UUID.randomUUID().toString(),
            title = cleanTitle,
            folder = targetFolder,
            content = rawText.trim(),
            updatedAt = System.currentTimeMillis()
        )
        saveScript(imported)
        return imported
    }

    fun exportBackupJson(): String {
        val payload = BackupPayload(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            folders = _folders.value,
            scripts = _scripts.value,
            settings = _settings.value
        )
        return json.encodeToString(payload)
    }

    fun restoreBackupJson(rawJson: String): Boolean {
        return try {
            val payload = json.decodeFromString<BackupPayload>(rawJson)
            if (payload.scripts.isNotEmpty()) {
                _folders.value = (defaultFolders() + payload.folders).distinct()
                _scripts.value = payload.scripts
                _settings.value = payload.settings
                persistToDisk()
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun loadFromDisk() {
        val file = storageFile ?: return
        if (!file.exists()) return
        try {
            val raw = file.readText()
            val payload = json.decodeFromString<BackupPayload>(raw)
            if (payload.scripts.isNotEmpty()) {
                _folders.value = (defaultFolders() + payload.folders).distinct()
                _scripts.value = payload.scripts
                _settings.value = payload.settings
            }
        } catch (_: Exception) {
            // Keep default scripts if corrupted
        }
    }

    private fun persistToDisk() {
        val file = storageFile ?: return
        try {
            val payload = BackupPayload(
                version = 1,
                exportedAt = System.currentTimeMillis(),
                folders = _folders.value,
                scripts = _scripts.value,
                settings = _settings.value
            )
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(payload))
        } catch (_: Exception) {
            // Ignore write errors in restricted environments
        }
    }

    companion object {
        @Volatile
        private var instance: DataRepository? = null

        fun getInstance(context: Context? = null): DataRepository {
            return instance ?: synchronized(this) {
                instance ?: DataRepository(
                    storageFile = context?.applicationContext?.let {
                        File(it.filesDir, "pro_teleprompter_store.json")
                    }
                ).also { instance = it }
            }
        }

        fun defaultFolders(): List<String> = listOf(
            "Geral",
            "YouTube",
            "Jornalismo",
            "Comerciais",
            "Aulas & Cursos"
        )

        fun defaultScripts(): List<Script> = listOf(
            Script(
                id = "demo-broadcast-1",
                title = "Telejornal / Plantão de Notícias",
                folder = "Jornalismo",
                wpm = 150,
                fontSizeSp = 38,
                isFavorite = true,
                content = """
                === MANCHETE PRINCIPAL ===
                [DICA]Postura firme, tom jornalístico sereno[/DICA]
                Boa noite. Começamos esta edição com as principais atualizações do mercado de tecnologia e produção audiovisual.
                [PAUSA 2s]

                De acordo com o novo relatório global, o uso de [DESTAQUE]estúdios móveis com teleprompter integrado[/DESTAQUE] cresceu mais de 68% entre criadores profissionais e emissoras independentes.

                === DADOS DO SETOR ===
                [DICA]Mudar enquadramento para Câmera 2 ou aproximar[/DICA]
                O grande diferencial está na precisão: [ALERTA]manter o contato visual contínuo com a lente[/ALERTA] reduz o tempo de gravação pela metade e elimina regravações desnecessárias.
                [PAUSA 3s]

                === ENCERRAMENTO DO BLOCO ===
                Voltamos em instantes com entrevistas exclusivas direto da redação. Até já!
                """.trimIndent()
            ),
            Script(
                id = "demo-youtube-2",
                title = "Review Tech & Lançamento no YouTube",
                folder = "YouTube",
                wpm = 140,
                fontSizeSp = 34,
                isFavorite = true,
                content = """
                === GANCHO INICIAL (0:00) ===
                [DICA]Energia alta! Sorriso no rosto[/DICA]
                Você já travou na frente da câmera bem na hora de gravar aquele vídeo importante?
                [PAUSA 2s]

                Hoje eu vou te mostrar como configurar um [DESTAQUE]fluxo de gravação digno de cinema[/DESTAQUE] usando apenas o seu smartphone Android!

                === 3 SEGREDOS DE LEITURA ===
                Primeiro segredo: ajuste as margens laterais do texto. Quando a linha é mais estreita, [ALERTA]seus olhos não parecem estar lendo da esquerda para a direita[/ALERTA].
                [PAUSA 2s]

                Segundo segredo: posicione a barra de foco bem no topo, pertinho da câmera frontal.

                Terceiro segredo: use marcadores de pausa e destaques coloridos para dar ritmo natural à sua voz!
                [PAUSA 3s]

                === CHAMADA PARA AÇÃO (CTA) ===
                [DICA]Apontar para o botão abaixo[/DICA]
                Curtiu essa dica? Então já deixa o like, se inscreve no canal e ativa o sininho para não perder os próximos tutoriais!
                """.trimIndent()
            ),
            Script(
                id = "demo-commercial-3",
                title = "Pitch Comercial de 60 Segundos (VSL)",
                folder = "Comerciais",
                wpm = 135,
                fontSizeSp = 36,
                isFavorite = false,
                content = """
                === PROBLEMA ===
                [DICA]Tom empático e próximo[/DICA]
                Quantas horas a sua equipe perde toda semana regravando vídeos corporativos por causa de pequenos erros no texto?
                [PAUSA 2s]

                === SOLUÇÃO ===
                Com o [DESTAQUE]Pro Teleprompter Studio[/DESTAQUE], você transforma qualquer celular ou tablet em um equipamento completo de broadcast:
                • Modo espelho horizontal e vertical para vidro refletor;
                • Controle de velocidade em palavras por minuto;
                • Gravação em alta definição com leitura simultânea;
                • Controle remoto Bluetooth, pedal e rolagem guiada por voz.
                [PAUSA 3s]

                === OFERTA FINAL ===
                [ALERTA]Comece a gravar como um profissional hoje mesmo![/ALERTA] Clique no link da descrição e faça o teste agora.
                """.trimIndent()
            )
        )
    }
}
