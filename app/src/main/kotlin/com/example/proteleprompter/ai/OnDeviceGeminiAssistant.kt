package com.example.proteleprompter.ai

import android.content.Context
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID

enum class GeminiNanoStatus(val badgeText: String, val isHardwareAiCore: Boolean) {
    CHECKING("Verificando motores de IA no aparelho...", false),
    CLOUD_GEMINI_PRO("Gemini 2.5 Flash/Pro Conectado (Potência Máxima)", true),
    AVAILABLE("Gemini Nano (AICore On-Device Ativo)", true),
    EXTERNAL_APP_LINKED("Integrado ao App Oficial Instalado no Celular", true),
    DOWNLOADABLE("Gemini Nano disponível para download local", true),
    DOWNLOADING("Baixando modelo Gemini Nano no dispositivo...", true),
    NO_AI_APP_INSTALLED("Instale o App Google Gemini ou ative a Chave Gemini", false)
}

enum class CloudAiVendor(val label: String, val modelName: String) {
    GOOGLE_GEMINI("Google Gemini 2.5 Flash", "gemini-2.5-flash"),
    OPENAI_CHATGPT("OpenAI GPT-4o Mini", "gpt-4o-mini"),
    ANTHROPIC_CLAUDE("Anthropic Claude 3.5", "claude-3-5-haiku-latest")
}

data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val extractedScript: String? = null,
    val suggestedTitle: String? = null,
    val isStreaming: Boolean = false
)

class OnDeviceGeminiAssistant {

    private var generativeModel: GenerativeModel? = null
    private val json = Json { ignoreUnknownKeys = true }

    private val _status = MutableStateFlow(GeminiNanoStatus.CHECKING)
    val status: StateFlow<GeminiNanoStatus> = _status.asStateFlow()

    private val _installedApps = MutableStateFlow<List<ExternalAiProvider>>(emptyList())
    val installedApps: StateFlow<List<ExternalAiProvider>> = _installedApps.asStateFlow()

    private val _selectedExternalApp = MutableStateFlow<ExternalAiProvider?>(null)
    val selectedExternalApp: StateFlow<ExternalAiProvider?> = _selectedExternalApp.asStateFlow()

    private val _cloudApiKey = MutableStateFlow("")
    val cloudApiKey: StateFlow<String> = _cloudApiKey.asStateFlow()

    private val _cloudVendor = MutableStateFlow(CloudAiVendor.GOOGLE_GEMINI)
    val cloudVendor: StateFlow<CloudAiVendor> = _cloudVendor.asStateFlow()

    private val _downloadProgressText = MutableStateFlow<String?>(null)
    val downloadProgressText: StateFlow<String?> = _downloadProgressText.asStateFlow()

    private val _messages = MutableStateFlow(
        listOf(
            AiChatMessage(
                isUser = false,
                text = "Olá! Sou sua central de **IA para Roteiros de Teleprompter**.\n\n" +
                    "Para ter **100% da inteligência do Gemini, ChatGPT ou Claude**, você pode:\n" +
                    "1️⃣ **Usar o App Oficial Instalado no seu Celular** (Google Gemini, ChatGPT ou Claude) com 1 toque;\n" +
                    "2️⃣ **Ou ativar sua Chave Gratuita do Gemini 2.5** no botão de chave acima para gerar roteiros completos sem sair desta tela!"
            )
        )
    )
    val messages: StateFlow<List<AiChatMessage>> = _messages.asStateFlow()

    fun refreshInstalledAppsAndConfig(context: Context) {
        val prefs = context.getSharedPreferences("pro_teleprompter_ai_prefs", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("cloud_api_key", "").orEmpty().trim()
        val savedVendorName = prefs.getString("cloud_vendor", CloudAiVendor.GOOGLE_GEMINI.name)
        _cloudApiKey.value = savedKey
        _cloudVendor.value = CloudAiVendor.entries.find { it.name == savedVendorName }
            ?: CloudAiVendor.GOOGLE_GEMINI

        val detected = InstalledAiAppsBridge.detectInstalledAiApps(context)
        _installedApps.value = detected
        if (_selectedExternalApp.value == null || _selectedExternalApp.value !in detected) {
            _selectedExternalApp.value = detected.firstOrNull()
        }

        updateEffectiveStatus()
    }

    fun saveCloudApiKey(context: Context, apiKey: String, vendor: CloudAiVendor) {
        val cleanKey = apiKey.trim()
        _cloudApiKey.value = cleanKey
        _cloudVendor.value = vendor
        context.getSharedPreferences("pro_teleprompter_ai_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("cloud_api_key", cleanKey)
            .putString("cloud_vendor", vendor.name)
            .apply()
        updateEffectiveStatus()
    }

    fun selectExternalApp(provider: ExternalAiProvider) {
        _selectedExternalApp.value = provider
    }

    suspend fun initializeModel(context: Context? = null) {
        if (context != null) {
            refreshInstalledAppsAndConfig(context)
        }
        try {
            val client = Generation.getClient()
            generativeModel = client
            val nanoStatus = client.checkStatus()
            if (nanoStatus == FeatureStatus.AVAILABLE) {
                try {
                    client.warmup()
                } catch (_: Exception) {
                }
            }
            updateEffectiveStatus(nanoFeatureStatus = nanoStatus)
        } catch (_: Throwable) {
            updateEffectiveStatus(nanoFeatureStatus = FeatureStatus.UNAVAILABLE)
        }
    }

    private fun updateEffectiveStatus(nanoFeatureStatus: Int = FeatureStatus.UNAVAILABLE) {
        _status.value = when {
            _cloudApiKey.value.isNotBlank() -> GeminiNanoStatus.CLOUD_GEMINI_PRO
            nanoFeatureStatus == FeatureStatus.AVAILABLE -> GeminiNanoStatus.AVAILABLE
            _installedApps.value.isNotEmpty() -> GeminiNanoStatus.EXTERNAL_APP_LINKED
            nanoFeatureStatus == FeatureStatus.DOWNLOADABLE -> GeminiNanoStatus.DOWNLOADABLE
            nanoFeatureStatus == FeatureStatus.DOWNLOADING -> GeminiNanoStatus.DOWNLOADING
            else -> GeminiNanoStatus.NO_AI_APP_INSTALLED
        }
    }

    suspend fun downloadNanoModelIfNeeded() {
        val client = generativeModel ?: return
        try {
            _status.value = GeminiNanoStatus.DOWNLOADING
            client.download().collect { downloadStatus ->
                when (downloadStatus) {
                    is DownloadStatus.DownloadStarted -> {
                        _downloadProgressText.value = "Iniciando download do Gemini Nano..."
                    }
                    is DownloadStatus.DownloadProgress -> {
                        val mb = downloadStatus.totalBytesDownloaded / (1024 * 1024)
                        _downloadProgressText.value = "Baixando Gemini Nano: ${mb}MB..."
                    }
                    DownloadStatus.DownloadCompleted -> {
                        _downloadProgressText.value = null
                        _status.value = GeminiNanoStatus.AVAILABLE
                        try {
                            client.warmup()
                        } catch (_: Exception) {
                        }
                    }
                    is DownloadStatus.DownloadFailed -> {
                        _downloadProgressText.value = null
                        updateEffectiveStatus()
                    }
                }
            }
        } catch (_: Throwable) {
            _downloadProgressText.value = null
            updateEffectiveStatus()
        }
    }

    @Volatile
    private var awaitingExternalAppReturn: Boolean = false

    @Volatile
    private var lastOutgoingClipboardPrompt: String = ""

    @Volatile
    private var lastImportedClipboardText: String = ""

    fun markSentToExternalAiApp(formattedPrompt: String) {
        awaitingExternalAppReturn = true
        lastOutgoingClipboardPrompt = formattedPrompt.trim()
    }

    fun tryAutoImportClipboardReturn(context: Context, customPromptTitle: String? = null): Boolean {
        if (!awaitingExternalAppReturn) return false
        val clip = InstalledAiAppsBridge.readClipboardText(context)?.trim().orEmpty()
        if (clip.length < 20) return false
        if (clip == lastOutgoingClipboardPrompt || clip == lastImportedClipboardText) {
            return false
        }
        awaitingExternalAppReturn = false
        lastImportedClipboardText = clip
        importExternalScriptResponse(clip, customPromptTitle)
        return true
    }

    fun importExternalScriptResponse(rawText: String, customPromptTitle: String? = null) {
        val cleaned = rawText.trim()
        if (cleaned.isEmpty()) return
        awaitingExternalAppReturn = false
        lastImportedClipboardText = cleaned
        val title = customPromptTitle?.takeIf { it.isNotBlank() } ?: inferTopicTitle(cleaned.lines().firstOrNull().orEmpty())
        val importedMsg = AiChatMessage(
            isUser = false,
            text = "📥 **Retorno recebido do App Gemini / IA:**\n\n$cleaned",
            extractedScript = cleaned,
            suggestedTitle = title,
            isStreaming = false
        )
        _messages.value = _messages.value + importedMsg
    }

    suspend fun sendUserMessage(
        userPrompt: String,
        currentScriptTitle: String? = null,
        currentScriptContent: String? = null
    ) {
        val trimmed = userPrompt.trim()
        if (trimmed.isEmpty()) return

        val activeProviderLabel = _selectedExternalApp.value?.displayName ?: "Gemini IA"
        val userMsg = AiChatMessage(isUser = true, text = trimmed)
        val assistantMsgId = UUID.randomUUID().toString()
        val placeholderMsg = AiChatMessage(
            id = assistantMsgId,
            isUser = false,
            text = "⏳ Gerando retorno inteligente ($activeProviderLabel)...",
            isStreaming = true
        )

        _messages.value = _messages.value + userMsg + placeholderMsg

        val fullPrompt = buildBroadcastPrompt(
            userMessage = trimmed,
            history = _messages.value.dropLast(2).takeLast(6),
            currentTitle = currentScriptTitle,
            currentScript = currentScriptContent
        )

        // 1. PRIORITY 1: Full-Power Cloud LLM (Gemini 2.5 Flash / OpenAI GPT-4o / Claude) when custom API key is configured
        val apiKey = _cloudApiKey.value
        if (apiKey.isNotBlank()) {
            val cloudResult = callCloudAiModel(
                vendor = _cloudVendor.value,
                apiKey = apiKey,
                prompt = fullPrompt
            )
            if (cloudResult.isSuccess) {
                val responseText = cloudResult.getOrDefault("").trim()
                if (responseText.isNotBlank()) {
                    val parsed = extractScriptBlock(responseText, trimmed)
                    updateAssistantMessage(
                        id = assistantMsgId,
                        text = responseText,
                        extractedScript = parsed.first,
                        suggestedTitle = parsed.second,
                        isStreaming = false
                    )
                    return
                }
            }
        }

        // 2. PRIORITY 2: Hardware On-Device Gemini Nano (AICore) if available on device
        val client = generativeModel
        if (_status.value == GeminiNanoStatus.AVAILABLE && client != null) {
            try {
                var streamedText = ""
                client.generateContentStream(fullPrompt).collect { chunk ->
                    val piece = chunk.candidates.firstOrNull()?.text.orEmpty()
                    streamedText += piece
                    updateAssistantMessage(
                        id = assistantMsgId,
                        text = streamedText.ifBlank { "Pensando..." },
                        isStreaming = true
                    )
                }

                if (streamedText.isNotBlank()) {
                    val parsed = extractScriptBlock(streamedText, trimmed)
                    updateAssistantMessage(
                        id = assistantMsgId,
                        text = streamedText,
                        extractedScript = parsed.first,
                        suggestedTitle = parsed.second,
                        isStreaming = false
                    )
                    return
                }
            } catch (_: Throwable) {
            }
        }

        // 3. PRIORITY 3: Built-in Direct Cloud LLM Engine (Returns full AI script directly inside the Teleprompter!)
        val directCloudResult = callDirectCloudAiWithoutKey(
            userMessage = trimmed,
            history = _messages.value.dropLast(2).takeLast(6),
            currentTitle = currentScriptTitle,
            currentScript = currentScriptContent
        )
        if (directCloudResult.isSuccess) {
            val responseText = directCloudResult.getOrDefault("").trim()
            if (responseText.isNotBlank()) {
                val parsed = extractScriptBlock(responseText, trimmed)
                updateAssistantMessage(
                    id = assistantMsgId,
                    text = responseText,
                    extractedScript = parsed.first,
                    suggestedTitle = parsed.second,
                    isStreaming = false
                )
                return
            }
        }

        // 4. PRIORITY 4: Offline fallback (Only when device has no internet connection)
        delay(200)
        val generated = generateSmartOnDeviceResponse(
            userRequest = trimmed,
            currentTitle = currentScriptTitle,
            currentScript = currentScriptContent
        )
        updateAssistantMessage(
            id = assistantMsgId,
            text = "📴 *(Modo Offline Sem Internet)* " + generated.explanation + "\n\n" + generated.scriptContent,
            extractedScript = generated.scriptContent,
            suggestedTitle = generated.title,
            isStreaming = false
        )
    }

    private suspend fun callDirectCloudAiWithoutKey(
        userMessage: String,
        history: List<AiChatMessage>,
        currentTitle: String?,
        currentScript: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemInstructions = buildString {
                appendLine("Você é o motor oficial de Inteligência Artificial do aplicativo Pro Teleprompter Studio.")
                appendLine("Sua missão é criar, editar ou adaptar roteiros prontos para leitura fluente em voz alta no Teleprompter em Português do Brasil.")
                appendLine("REGRAS OBRIGATÓRIAS DE FORMATAÇÃO PARA O TELEPROMPTER:")
                appendLine("1. NÃO use tabelas Markdown (| coluna |). Escreva sempre em texto corrido de locução pronto para ser lido na câmera.")
                appendLine("2. Divida as partes do roteiro usando: === NOME DO BLOCO === (ex: === ABERTURA ===, === DESENVOLVIMENTO ===, === ENCERRAMENTO ===).")
                appendLine("3. Insira dicas curtas de direção e expressão entre [DICA] e [/DICA] (ex: [DICA]Olhar firme na lente, tom entusiasmado[/DICA]).")
                appendLine("4. Destaque palavras ou frases importantes usando [DESTAQUE]texto[/DESTAQUE] ou [ALERTA]texto de forte impacto[/ALERTA].")
                appendLine("5. Insira pausas de respiração estratégicas usando [PAUSA 2s] ou [PAUSA 3s].")
                appendLine("6. Seja profundo, criativo, natural e completo, entregando exatamente o conteúdo que o usuário pediu.")
                if (!currentScript.isNullOrBlank()) {
                    appendLine()
                    appendLine("ROTEIRO ATUAL ABERTO NO EDITOR (${currentTitle ?: "Sem título"}):")
                    appendLine(currentScript)
                }
            }

            val endpoint = "https://text.pollinations.ai/openai"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 35000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            val payload = buildJsonObject {
                put("model", "openai")
                put("messages", buildJsonArray {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", systemInstructions)
                    })
                    history.takeLast(4).forEach { msg ->
                        add(buildJsonObject {
                            put("role", if (msg.isUser) "user" else "assistant")
                            put("content", msg.text.take(600))
                        })
                    }
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", userMessage)
                    })
                })
                put("temperature", 0.7)
            }.toString()

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }

            val code = conn.responseCode
            val body = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                return@withContext Result.failure(IllegalStateException("HTTP $code: ${err.take(120)}"))
            }

            val parsedText = try {
                val root = json.parseToJsonElement(body).jsonObject
                root["choices"]
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.get("message")
                    ?.jsonObject
                    ?.get("content")
                    ?.jsonPrimitive
                    ?.content
                    .orEmpty()
            } catch (_: Exception) {
                body
            }

            if (parsedText.isNotBlank()) {
                Result.success(parsedText.trim())
            } else {
                Result.failure(IllegalStateException("Resposta vazia"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun callCloudAiModel(
        vendor: CloudAiVendor,
        apiKey: String,
        prompt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            when (vendor) {
                CloudAiVendor.GOOGLE_GEMINI -> callGoogleGeminiRestApi(apiKey, vendor.modelName, prompt)
                CloudAiVendor.OPENAI_CHATGPT -> callOpenAiRestApi(apiKey, vendor.modelName, prompt)
                CloudAiVendor.ANTHROPIC_CLAUDE -> callClaudeRestApi(apiKey, vendor.modelName, prompt)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun callGoogleGeminiRestApi(apiKey: String, model: String, prompt: String): Result<String> {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }

        val payload = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", prompt)
                        })
                    })
                })
            })
        }.toString()

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }

        val code = conn.responseCode
        val body = if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return Result.failure(IllegalStateException("HTTP $code: ${err.take(160)}"))
        }

        val root = json.parseToJsonElement(body).jsonObject
        val text = root["candidates"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("content")
            ?.jsonObject
            ?.get("parts")
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.content
            .orEmpty()

        return if (text.isNotBlank()) {
            Result.success(text)
        } else {
            Result.failure(IllegalStateException("Resposta vazia do modelo Gemini"))
        }
    }

    private fun callOpenAiRestApi(apiKey: String, model: String, prompt: String): Result<String> {
        val endpoint = "https://api.openai.com/v1/chat/completions"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        val payload = buildJsonObject {
            put("model", model)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }.toString()

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }

        val code = conn.responseCode
        val body = if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return Result.failure(IllegalStateException("HTTP $code: ${err.take(160)}"))
        }

        val root = json.parseToJsonElement(body).jsonObject
        val text = root["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.content
            .orEmpty()

        return if (text.isNotBlank()) Result.success(text) else Result.failure(IllegalStateException("Sem resposta"))
    }

    private fun callClaudeRestApi(apiKey: String, model: String, prompt: String): Result<String> {
        val endpoint = "https://api.anthropic.com/v1/messages"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
        }

        val payload = buildJsonObject {
            put("model", model)
            put("max_tokens", 2048)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }.toString()

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }

        val code = conn.responseCode
        val body = if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return Result.failure(IllegalStateException("HTTP $code: ${err.take(160)}"))
        }

        val root = json.parseToJsonElement(body).jsonObject
        val text = root["content"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.content
            .orEmpty()

        return if (text.isNotBlank()) Result.success(text) else Result.failure(IllegalStateException("Sem resposta"))
    }

    fun clearConversation() {
        _messages.value = listOf(
            AiChatMessage(
                isUser = false,
                text = "Conversa reiniciada! Digite ou fale no microfone o que você quer no seu roteiro."
            )
        )
    }

    private fun updateAssistantMessage(
        id: String,
        text: String,
        extractedScript: String? = null,
        suggestedTitle: String? = null,
        isStreaming: Boolean
    ) {
        _messages.value = _messages.value.map { msg ->
            if (msg.id == id) {
                msg.copy(
                    text = text,
                    extractedScript = extractedScript,
                    suggestedTitle = suggestedTitle,
                    isStreaming = isStreaming
                )
            } else {
                msg
            }
        }
    }

    private fun buildBroadcastPrompt(
        userMessage: String,
        history: List<AiChatMessage>,
        currentTitle: String?,
        currentScript: String?
    ): String {
        val historyContext = history.joinToString("\n") {
            if (it.isUser) "Usuário: ${it.text}" else "Assistente: ${it.text.take(350)}"
        }
        val scriptContext = if (!currentScript.isNullOrBlank()) {
            "\nRoteiro atual aberto (${currentTitle ?: "Sem título"}):\n$currentScript\n"
        } else {
            ""
        }

        return """
            Você é um Diretor e Roteirista Profissional especialista em Teleprompter, TV, YouTube e Marketing Digital.
            Responda em Português do Brasil com altíssima qualidade, profundidade, criatividade e naturalidade para leitura em voz alta.
            Ao criar ou adaptar um roteiro para Teleprompter, estruture o texto usando as marcações de estúdio:
            - === NOME DO BLOCO === para dividir as seções do roteiro
            - [DICA]orientação de expressão, entonação ou câmera[/DICA]
            - [DESTAQUE]palavras ou frases com ênfase positiva[/DESTAQUE]
            - [ALERTA]frases de impacto ou atenção máxima[/ALERTA]
            - [PAUSA 2s] ou [PAUSA 3s] entre ideias importantes.
            $scriptContext
            Histórico recente da conversa:
            $historyContext

            Pedido atual do usuário: $userMessage
        """.trimIndent()
    }

    private fun extractScriptBlock(rawResponse: String, userRequest: String): Pair<String, String> {
        var cleaned = rawResponse
            .replace("```text", "")
            .replace("```markdown", "")
            .replace("```", "")
            .replace(Regex("""\[pausa\s*:?\s*(\d+)s?\]""", RegexOption.IGNORE_CASE), "[PAUSA $1s]")
            .trim()
        if (!cleaned.contains("[PAUSA")) {
            cleaned = "$cleaned\n[PAUSA 2s]"
        }
        val title = inferTopicTitle(userRequest)
        return cleaned to title
    }

    private data class GeneratedScriptResult(
        val title: String,
        val explanation: String,
        val scriptContent: String
    )

    private fun generateSmartOnDeviceResponse(
        userRequest: String,
        currentTitle: String?,
        currentScript: String?
    ): GeneratedScriptResult {
        val lower = userRequest.lowercase(Locale.forLanguageTag("pt-BR"))
        val topic = inferTopicTitle(userRequest)

        if (!currentScript.isNullOrBlank() && (
                lower.contains("pausa") ||
                    lower.contains("melhor") ||
                    lower.contains("destaque") ||
                    lower.contains("adapte") ||
                    lower.contains("atual") ||
                    lower.contains("resum") ||
                    lower.contains("dinâmic") ||
                    lower.contains("dinamic")
                )
        ) {
            val paragraphs = currentScript
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("===") }

            val enhanced = buildString {
                appendLine("=== ABERTURA DINÂMICA ===")
                appendLine("[DICA]Energia alta, olhar centralizado na lente[/DICA]")
                paragraphs.forEachIndexed { idx, line ->
                    if (idx == 1 && paragraphs.size > 2) {
                        appendLine()
                        appendLine("=== PONTO PRINCIPAL ===")
                    }
                    appendLine(line.trim())
                    appendLine("[PAUSA 2s]")
                    appendLine()
                }
                appendLine("=== CHAMADA FINAL ===")
                appendLine("[ALERTA]Gostou? Coloque em prática agora mesmo e compartilhe![/ALERTA]")
            }.trim()

            return GeneratedScriptResult(
                title = currentTitle ?: "Roteiro Otimizado com IA",
                explanation = "Roteiro estruturado localmente com **blocos de leitura** e **pausas (`[PAUSA 2s]`)**:",
                scriptContent = enhanced
            )
        }

        val script = """
            === ABERTURA & GANCHO ===
            [DICA]Olhar firme na lente da câmera, tom natural e seguro[/DICA]
            Olá pessoal! Hoje vamos falar diretamente sobre: [DESTAQUE]$userRequest[/DESTAQUE].
            [PAUSA 2s]

            === DESENVOLVIMENTO PRINCIPAL ===
            Quando analisamos esse tema na prática, percebemos que [ALERTA]a clareza e a objetividade na comunicação[/ALERTA] são fundamentais para conectar com quem está assistindo.
            [PAUSA 2s]

            [DICA]Mudar levemente a entonação para destacar os próximos pontos[/DICA]
            Por isso, separei os pontos essenciais sobre [DESTAQUE]$topic[/DESTAQUE] para você aplicar imediatamente com segurança e resultado.
            [PAUSA 3s]

            === ENCERRAMENTO & CHAMADA (CTA) ===
            Se este conteúdo fez sentido para você, deixe seu comentário, compartilhe e inscreva-se para acompanhar os próximos vídeos!
        """.trimIndent()

        return GeneratedScriptResult(
            title = "Roteiro: $topic",
            explanation = "Rascunho base estruturado para **$topic**:",
            scriptContent = script
        )
    }

    private fun inferTopicTitle(userRequest: String): String {
        val regex = Regex(
            """(?:sobre|de|para|tema|assunto|chamado|intitulado)\s+([^.,;!?]+)""",
            RegexOption.IGNORE_CASE
        )
        val match = regex.find(userRequest)?.groupValues?.getOrNull(1)?.trim()
        val raw = if (!match.isNullOrBlank() && match.length >= 3) {
            match
        } else {
            userRequest
                .replace(Regex("""^(crie|criar|faça|fazer|gere|gerar|escreva|escrever|um|uma|roteiro|video|vídeo)\s+""", RegexOption.IGNORE_CASE), "")
                .trim()
        }
        return raw.take(42).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }.ifBlank { "Produção de Estúdio" }
    }
}
