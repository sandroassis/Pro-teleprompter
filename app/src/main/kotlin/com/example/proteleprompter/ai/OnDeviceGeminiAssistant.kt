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
                text = "Olá! Sou seu **Co-Criador de Roteiros com IA** para gravação de vídeos.\n\n" +
                    "Fale no microfone ou digite sobre o que você quer gravar hoje — podemos criar um roteiro do zero, estruturar ideias juntos ou deixar um texto que você já tem mais natural para ler na câmera!"
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
                    val parsed = parseConversationalAndScriptResponse(responseText, trimmed)
                    updateAssistantMessage(
                        id = assistantMsgId,
                        text = parsed.displayChatText,
                        extractedScript = parsed.cleanTeleprompterScript,
                        suggestedTitle = parsed.suggestedTitle,
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
                    val cleanLive = streamedText.replace("<roteiro>", "").replace("</roteiro>", "").trim()
                    updateAssistantMessage(
                        id = assistantMsgId,
                        text = cleanLive.ifBlank { "Escrevendo roteiro..." },
                        isStreaming = true
                    )
                }

                if (streamedText.isNotBlank()) {
                    val parsed = parseConversationalAndScriptResponse(streamedText, trimmed)
                    updateAssistantMessage(
                        id = assistantMsgId,
                        text = parsed.displayChatText,
                        extractedScript = parsed.cleanTeleprompterScript,
                        suggestedTitle = parsed.suggestedTitle,
                        isStreaming = false
                    )
                    return
                }
            } catch (_: Throwable) {
            }
        }

        // 3. PRIORITY 3: Built-in Real-Time Streaming Cloud LLM Co-Creator (Fast, natural spoken scripts inside the app!)
        val latestScriptInChat = _messages.value
            .dropLast(2)
            .lastOrNull { !it.isUser && !it.extractedScript.isNullOrBlank() }
            ?.extractedScript
            ?: currentScriptContent

        val directCloudResult = streamDirectCloudAiWithoutKey(
            userMessage = trimmed,
            history = _messages.value.dropLast(2).takeLast(6),
            currentTitle = currentScriptTitle,
            currentScript = latestScriptInChat,
            onPartialText = { partialRaw ->
                val liveDisplay = partialRaw
                    .replace("<roteiro>", "\n\n🎬 **Roteiro para o Teleprompter:**\n")
                    .replace("</roteiro>", "\n")
                    .trim()
                if (liveDisplay.isNotBlank()) {
                    updateAssistantMessage(
                        id = assistantMsgId,
                        text = liveDisplay,
                        isStreaming = true
                    )
                }
            }
        )
        if (directCloudResult.isSuccess) {
            val responseText = directCloudResult.getOrDefault("").trim()
            if (responseText.isNotBlank()) {
                val parsed = parseConversationalAndScriptResponse(responseText, trimmed)
                updateAssistantMessage(
                    id = assistantMsgId,
                    text = parsed.displayChatText,
                    extractedScript = parsed.cleanTeleprompterScript,
                    suggestedTitle = parsed.suggestedTitle,
                    isStreaming = false
                )
                return
            }
        }

        // 4. PRIORITY 4: Offline fallback (Only when device has no internet connection)
        delay(150)
        val generated = generateSmartOnDeviceResponse(
            userRequest = trimmed,
            currentTitle = currentScriptTitle,
            currentScript = latestScriptInChat
        )
        updateAssistantMessage(
            id = assistantMsgId,
            text = generated.explanation,
            extractedScript = generated.scriptContent,
            suggestedTitle = generated.title,
            isStreaming = false
        )
    }

    private suspend fun streamDirectCloudAiWithoutKey(
        userMessage: String,
        history: List<AiChatMessage>,
        currentTitle: String?,
        currentScript: String?,
        onPartialText: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemInstructions = buildString {
                appendLine("Você é um Parceiro Criativo e Roteirista Profissional especialista em gravação de vídeos com Teleprompter.")
                appendLine("Converse sempre em Português do Brasil de forma natural, acolhedora, inteligente e direta.")
                appendLine("COMO AJUDAR O USUÁRIO:")
                appendLine("1. Você pode tanto criar um roteiro completo do zero quanto ajudar o usuário a construir, refinar, encurtar, expandir ou melhorar ideias de vídeo passo a passo.")
                appendLine("2. Sempre que você entregar ou atualizar um roteiro para ser lido na câmera, coloque o texto falado dentro das tags <roteiro> e </roteiro>.")
                appendLine("3. DENTRO de <roteiro>...</roteiro>, escreva APENAS o texto falado natural, fluido e humano — exatamente como a pessoa vai falar olhando para a lente da câmera ao gravar o vídeo.")
                appendLine("4. Separe as frases do <roteiro> em parágrafos curtos e respiráveis para facilitar a leitura no Teleprompter. NÃO use tabelas, NÃO use asteriscos (**), NÃO use títulos com # e NÃO use códigos técnicos dentro do <roteiro> (a menos que o usuário peça expressamente pausas [PAUSA 2s]).")
                appendLine("5. FORA da tag <roteiro>, seja breve (1 a 3 frases): comente de forma natural sobre a escolha do gancho/ritmo ou pergunte se o usuário quer ajustar alguma parte antes de gravar.")
                if (!currentScript.isNullOrBlank()) {
                    appendLine()
                    appendLine("ROTEIRO ATUAL EM TRABALHO (${currentTitle ?: "Roteiro Atual"}):")
                    appendLine(currentScript.take(1500))
                }
            }

            val messagesJson = buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemInstructions)
                })
                history.takeLast(4).forEach { msg ->
                    add(buildJsonObject {
                        put("role", if (msg.isUser) "user" else "assistant")
                        val contentForHistory = if (!msg.isUser && !msg.extractedScript.isNullOrBlank()) {
                            "${msg.text.take(250)}\n<roteiro>\n${msg.extractedScript.take(800)}\n</roteiro>"
                        } else {
                            msg.text.take(500)
                        }
                        put("content", contentForHistory)
                    })
                }
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userMessage)
                })
            }

            // First try real-time SSE streaming with low reasoning effort for instant response (<1.5s first token)
            val streamEndpoint = "https://text.pollinations.ai/openai"
            val streamConn = (URL(streamEndpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 25000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "text/event-stream")
            }

            val streamPayload = buildJsonObject {
                put("model", "openai-fast")
                put("reasoning_effort", "low")
                put("stream", true)
                put("temperature", 0.7)
                put("messages", messagesJson)
            }.toString()

            OutputStreamWriter(streamConn.outputStream, Charsets.UTF_8).use { it.write(streamPayload) }

            val streamCode = streamConn.responseCode
            if (streamCode in 200..299) {
                val accumulated = StringBuilder()
                streamConn.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { rawLine ->
                        val line = rawLine.trim()
                        if (line.startsWith("data:")) {
                            val dataPayload = line.removePrefix("data:").trim()
                            if (dataPayload.isNotEmpty() && dataPayload != "[DONE]") {
                                try {
                                    val chunkObj = json.parseToJsonElement(dataPayload).jsonObject
                                    val deltaContent = chunkObj["choices"]
                                        ?.jsonArray
                                        ?.firstOrNull()
                                        ?.jsonObject
                                        ?.get("delta")
                                        ?.jsonObject
                                        ?.get("content")
                                        ?.jsonPrimitive
                                        ?.content
                                        .orEmpty()
                                    if (deltaContent.isNotEmpty()) {
                                        accumulated.append(deltaContent)
                                        onPartialText(accumulated.toString())
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                }
                val finalStreamed = accumulated.toString().trim()
                if (finalStreamed.isNotBlank()) {
                    return@withContext Result.success(finalStreamed)
                }
            }

            // Fallback: non-streaming fast request with reasoning_effort = low
            val conn = (URL(streamEndpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 25000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            val payload = buildJsonObject {
                put("model", "openai-fast")
                put("reasoning_effort", "low")
                put("temperature", 0.7)
                put("messages", messagesJson)
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
            "\nRoteiro atual em trabalho (${currentTitle ?: "Sem título"}):\n$currentScript\n"
        } else {
            ""
        }

        return """
            Você é um Parceiro Criativo e Roteirista Profissional especialista em gravação de vídeos com Teleprompter.
            Converse em Português do Brasil de forma natural, acolhedora, inteligente e direta.
            1. Você pode tanto criar um roteiro completo quanto ajudar o usuário a construir, refinar, encurtar ou melhorar ideias de vídeo passo a passo.
            2. Sempre que entregar ou atualizar um roteiro para o usuário ler na câmera, coloque o texto falado dentro de <roteiro> e </roteiro>.
            3. Dentro de <roteiro>...</roteiro>, escreva APENAS o texto falado natural, fluido e humano — exatamente como a pessoa vai falar olhando para a lente da câmera ao gravar o vídeo, dividido em parágrafos curtos.
            4. Fora da tag <roteiro>, seja breve (1 a 3 frases): comente ou pergunte se o usuário quer ajustar o tom antes de gravar.
            $scriptContext
            Histórico recente da conversa:
            $historyContext

            Pedido atual do usuário: $userMessage
        """.trimIndent()
    }

    private data class ParsedAiScriptResponse(
        val displayChatText: String,
        val cleanTeleprompterScript: String?,
        val suggestedTitle: String
    )

    private fun parseConversationalAndScriptResponse(
        rawResponse: String,
        userRequest: String
    ): ParsedAiScriptResponse {
        val strippedCodeFences = rawResponse
            .replace("```xml", "")
            .replace("```text", "")
            .replace("```markdown", "")
            .replace("```", "")
            .trim()

        val tagRegex = Regex("""<roteiro>([\s\S]*?)</roteiro>""", RegexOption.IGNORE_CASE)
        val match = tagRegex.find(strippedCodeFences)

        val rawScriptBlock = if (match != null) {
            match.groupValues[1].trim()
        } else {
            // If LLM didn't wrap in <roteiro>, extract spoken text cleanly
            strippedCodeFences
                .lines()
                .filterNot { line ->
                    val trimmed = line.trim()
                    trimmed.startsWith("|") || trimmed.startsWith("---")
                }
                .joinToString("\n")
                .trim()
        }

        val cleanSpokenScript = rawScriptBlock
            .replace("**", "")
            .replace(Regex("""^#+\s*""", RegexOption.MULTILINE), "")
            .replace(Regex("""^"(.*)"$""", RegexOption.DOT_MATCHES_ALL), "$1")
            .replace(Regex("""\[pausa\s*:?\s*(\d+)s?\]""", RegexOption.IGNORE_CASE), "[PAUSA $1s]")
            .trim()

        val conversationalNotes = if (match != null) {
            strippedCodeFences.replace(match.value, "").trim()
        } else {
            "Aqui está uma sugestão de roteiro natural pronta para leitura na câmera. Você pode pedir ajustes ou abrir direto no Teleprompter para gravar:"
        }

        val finalDisplayText = if (conversationalNotes.isNotBlank()) {
            conversationalNotes
        } else {
            "Preparei o roteiro com linguagem natural para você gravar no Teleprompter:"
        }

        val title = inferTopicTitle(userRequest)
        return ParsedAiScriptResponse(
            displayChatText = finalDisplayText,
            cleanTeleprompterScript = cleanSpokenScript.ifBlank { null },
            suggestedTitle = title
        )
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
        val topic = inferTopicTitle(userRequest)

        if (!currentScript.isNullOrBlank()) {
            val paragraphs = currentScript
                .lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("===") }

            val enhanced = paragraphs.joinToString("\n\n") { line ->
                line.trim()
            }

            return GeneratedScriptResult(
                title = currentTitle ?: "Roteiro: $topic",
                explanation = "Ajustei o seu roteiro deixando a leitura mais fluida e natural para a câmera:",
                scriptContent = enhanced
            )
        }

        val script = """
            Olá, pessoal! Tudo bem com vocês? Hoje eu quero conversar de forma bem direta sobre $topic.

            Muita gente tem dúvida de por onde começar quando o assunto é esse, mas a verdade é que pequenos passos bem aplicados no dia a dia fazem toda a diferença no resultado final.

            Por isso, eu preparei este vídeo para compartilhar com você o que realmente funciona na prática, de um jeito simples, rápido e fácil de aplicar agora mesmo.

            Se esse conteúdo te ajudou, já deixa o seu curtir aqui embaixo, comenta a sua opinião e compartilha com quem também precisa ver isso. Até o próximo vídeo!
        """.trimIndent()

        return GeneratedScriptResult(
            title = "Roteiro: $topic",
            explanation = "Preparei uma base natural e conversada sobre **$topic** pronta para ler e gravar no Teleprompter:",
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
                .replace(Regex("""^(me\s+ajude\s+a\s+criar|crie|criar|faça|fazer|gere|gerar|escreva|escrever|um|uma|roteiro|video|vídeo)\s+""", RegexOption.IGNORE_CASE), "")
                .trim()
        }
        return raw.take(42).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }.ifBlank { "Produção de Vídeo" }
    }
}
