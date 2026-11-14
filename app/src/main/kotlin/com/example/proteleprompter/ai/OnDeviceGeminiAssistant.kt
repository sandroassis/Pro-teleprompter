package com.example.proteleprompter.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

enum class GeminiNanoStatus(val badgeText: String, val isHardwareAiCore: Boolean) {
    CHECKING("Verificando Gemini Nano no aparelho...", false),
    AVAILABLE("Gemini Nano (AICore On-Device Ativo)", true),
    DOWNLOADABLE("Gemini Nano disponível para download local", true),
    DOWNLOADING("Baixando modelo Gemini Nano no dispositivo...", true),
    LOCAL_SMART_ENGINE("Motor Local Inteligente Ativo (Offline)", false)
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

    private val _status = MutableStateFlow(GeminiNanoStatus.CHECKING)
    val status: StateFlow<GeminiNanoStatus> = _status.asStateFlow()

    private val _downloadProgressText = MutableStateFlow<String?>(null)
    val downloadProgressText: StateFlow<String?> = _downloadProgressText.asStateFlow()

    private val _messages = MutableStateFlow(
        listOf(
            AiChatMessage(
                isUser = false,
                text = "Olá! Sou seu assistente de roteiros integrado ao dispositivo com **Gemini Nano**.\n\n" +
                    "Converse comigo em linguagem natural! Por exemplo:\n" +
                    "• *\"Crie um roteiro de 1 minuto para YouTube sobre produtividade\"*\n" +
                    "• *\"Adapte meu roteiro atual para um Reels de 30 segundos mais dinâmico\"*\n" +
                    "• *\"Adicione pausas estratégicas e destaques de entonação\"*"
            )
        )
    )
    val messages: StateFlow<List<AiChatMessage>> = _messages.asStateFlow()

    suspend fun initializeModel() {
        try {
            val client = Generation.getClient()
            generativeModel = client
            when (client.checkStatus()) {
                FeatureStatus.AVAILABLE -> {
                    _status.value = GeminiNanoStatus.AVAILABLE
                    try {
                        client.warmup()
                    } catch (_: Exception) {
                    }
                }
                FeatureStatus.DOWNLOADABLE -> {
                    _status.value = GeminiNanoStatus.DOWNLOADABLE
                }
                FeatureStatus.DOWNLOADING -> {
                    _status.value = GeminiNanoStatus.DOWNLOADING
                }
                else -> {
                    _status.value = GeminiNanoStatus.LOCAL_SMART_ENGINE
                }
            }
        } catch (_: Throwable) {
            _status.value = GeminiNanoStatus.LOCAL_SMART_ENGINE
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
                        _status.value = GeminiNanoStatus.LOCAL_SMART_ENGINE
                    }
                }
            }
        } catch (_: Throwable) {
            _downloadProgressText.value = null
            _status.value = GeminiNanoStatus.LOCAL_SMART_ENGINE
        }
    }

    suspend fun sendUserMessage(
        userPrompt: String,
        currentScriptTitle: String? = null,
        currentScriptContent: String? = null
    ) {
        val trimmed = userPrompt.trim()
        if (trimmed.isEmpty()) return

        val userMsg = AiChatMessage(isUser = true, text = trimmed)
        val assistantMsgId = UUID.randomUUID().toString()
        val placeholderMsg = AiChatMessage(
            id = assistantMsgId,
            isUser = false,
            text = "Gerando roteiro no dispositivo...",
            isStreaming = true
        )

        _messages.value = _messages.value + userMsg + placeholderMsg

        val fullPrompt = buildBroadcastPrompt(
            userMessage = trimmed,
            history = _messages.value.dropLast(2).takeLast(4),
            currentTitle = currentScriptTitle,
            currentScript = currentScriptContent
        )

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
                // Fall back to on-device smart generator if AICore quota or hardware error occurs
            }
        }

        // Local On-Device Conversational Script Generator fallback
        delay(350)
        val generated = generateSmartOnDeviceResponse(
            userRequest = trimmed,
            currentTitle = currentScriptTitle,
            currentScript = currentScriptContent
        )
        updateAssistantMessage(
            id = assistantMsgId,
            text = generated.explanation + "\n\n" + generated.scriptContent,
            extractedScript = generated.scriptContent,
            suggestedTitle = generated.title,
            isStreaming = false
        )
    }

    fun clearConversation() {
        _messages.value = listOf(
            AiChatMessage(
                isUser = false,
                text = "Conversa reiniciada! Me diga sobre qual tema, duração ou estilo você quer criar ou aprimorar seu roteiro."
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
            if (it.isUser) "Usuário: ${it.text}" else "Assistente: ${it.text.take(300)}"
        }
        val scriptContext = if (!currentScript.isNullOrBlank()) {
            "\nRoteiro atual aberto (${currentTitle ?: "Sem título"}):\n$currentScript\n"
        } else {
            ""
        }

        return """
            Você é o assistente de IA Gemini Nano integrado ao app Pro Teleprompter no Android.
            Responda sempre em Português do Brasil de forma natural, prestativa e direta.
            Ao criar ou revisar um roteiro para teleprompter, estruture o texto usando as marcações de estúdio:
            - === NOME DO BLOCO === para dividir seções
            - [DICA]orientação de expressão ou câmera[/DICA]
            - [DESTAQUE]palavras com ênfase positiva[/DESTAQUE]
            - [ALERTA]frases de impacto ou atenção[/ALERTA]
            - [PAUSA 2s] ou [PAUSA 3s] entre ideias importantes.
            $scriptContext
            Histórico recente:
            $historyContext

            Pedido do usuário: $userMessage
        """.trimIndent()
    }

    private fun extractScriptBlock(rawResponse: String, userRequest: String): Pair<String, String> {
        val cleaned = rawResponse
            .replace("```text", "")
            .replace("```markdown", "")
            .replace("```", "")
            .trim()
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

        // Case 1: User wants to improve/add pauses/highlights to current script
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
                    val withEmphasis = if (!line.contains("[DESTAQUE]") && line.length > 35) {
                        val words = line.split(" ")
                        if (words.size > 6) {
                            val mid = words.size / 2
                            words.subList(0, mid).joinToString(" ") +
                                " [DESTAQUE]" +
                                words.subList(mid, (mid + 3).coerceAtMost(words.size)).joinToString(" ") +
                                "[/DESTAQUE] " +
                                words.subList((mid + 3).coerceAtMost(words.size), words.size).joinToString(" ")
                        } else {
                            line
                        }
                    } else {
                        line
                    }
                    appendLine(withEmphasis.trim())
                    appendLine("[PAUSA 2s]")
                    appendLine()
                }
                appendLine("=== CHAMADA FINAL ===")
                appendLine("[ALERTA]Gostou? Coloque em prática agora mesmo e compartilhe![/ALERTA]")
            }.trim()

            return GeneratedScriptResult(
                title = currentTitle ?: "Roteiro Otimizado com IA",
                explanation = "Otimizei seu roteiro adicionando **blocos de leitura**, **pausas estratégicas (`[PAUSA 2s]`)** e **destaques de entonação** para leitura fluida no Teleprompter:",
                scriptContent = enhanced
            )
        }

        // Case 2: Short Reels / TikTok / Shorts (30s)
        if (lower.contains("reels") || lower.contains("tiktok") || lower.contains("shorts") || lower.contains("30 seg") || lower.contains("curto")) {
            val script = """
                === GANCHO DE 3 SEGUNDOS ===
                [DICA]Olhar intenso para a lente, fala rápida e energética[/DICA]
                Pare tudo por 30 segundos! Se você quer dominar [DESTAQUE]$topic[/DESTAQUE], precisa conhecer essa estratégia agora.
                [PAUSA 2s]

                === SEGREDO PRÁTICO ===
                A maioria das pessoas erra porque complica demais. O segredo real de [DESTAQUE]$topic[/DESTAQUE] está em aplicar consistência, clareza e foco no que traz resultado imediato.
                [PAUSA 2s]

                === VIRADA DE CHAVE ===
                [DICA]Aproximar levemente do enquadramento[/DICA]
                [ALERTA]Teste isso hoje mesmo[/ALERTA] e veja a diferença na prática logo no primeiro dia!
                [PAUSA 2s]

                === CTA RÁPIDO ===
                Salva esse vídeo para não esquecer e manda para quem precisa ver isso!
            """.trimIndent()

            return GeneratedScriptResult(
                title = "Reels / Shorts: $topic",
                explanation = "Criei um roteiro vertical rápido (aprox. 30–40 segundos) com **gancho forte de retenção** sobre **$topic**:",
                scriptContent = script
            )
        }

        // Case 3: Jornalismo / Notícia
        if (lower.contains("jornal") || lower.contains("notícia") || lower.contains("noticia") || lower.contains("reportagem")) {
            val script = """
                === MANCHETE ===
                [DICA]Postura formal, dicção clara e cadenciada[/DICA]
                Olá, muito boa noite. Destaques de hoje: novas atualizações importantes sobre [DESTAQUE]$topic[/DESTAQUE] movimentam o cenário nacional.
                [PAUSA 2s]

                === DETALHAMENTO DOS FATOS ===
                Especialistas apontam que o avanço de [DESTAQUE]$topic[/DESTAQUE] traz impactos diretos para profissionais e consumidores já nas próximas semanas.
                [PAUSA 2s]

                [DICA]Olhar firme, transição de entonação[/DICA]
                [ALERTA]A recomendação principal é acompanhar os indicadores[/ALERTA] e se preparar para as novas oportunidades do setor.
                [PAUSA 3s]

                === ENCERRAMENTO ===
                Continuamos acompanhando o caso em tempo real. Mais informações na nossa próxima edição.
            """.trimIndent()

            return GeneratedScriptResult(
                title = "Boletim: $topic",
                explanation = "Preparei uma lauda jornalística completa sobre **$topic** com marcações de pausa e entonação de bancada:",
                scriptContent = script
            )
        }

        // Case 4: Vendas / Comercial / Pitch
        if (lower.contains("venda") || lower.contains("comercial") || lower.contains("anúncio") || lower.contains("anuncio") || lower.contains("pitch") || lower.contains("cliente")) {
            val script = """
                === CONEXÃO E DOR ===
                [DICA]Tom empático, conversa olho no olho[/DICA]
                Você sente que poderia alcançar resultados muito maiores com [DESTAQUE]$topic[/DESTAQUE], mas ainda perde tempo com processos lentos?
                [PAUSA 2s]

                === APRESENTAÇÃO DA SOLUÇÃO ===
                Foi exatamente para resolver isso que desenvolvemos uma abordagem prática e direta para [DESTAQUE]$topic[/DESTAQUE].
                Com ela, você ganha produtividade, segurança e previsibilidade desde o primeiro passo.
                [PAUSA 2s]

                === PROVA E BENEFÍCIO ===
                [DICA]Sorriso confiante, ritmo firme[/DICA]
                Mais de centenas de profissionais já transformaram sua rotina aplicando esse método simples.
                [PAUSA 2s]

                === CHAMADA DE AÇÃO (OFERTA) ===
                [ALERTA]Toque no botão abaixo agora mesmo[/ALERTA] e fale com nossa equipe para garantir sua condição especial!
            """.trimIndent()

            return GeneratedScriptResult(
                title = "Pitch Comercial: $topic",
                explanation = "Estruturei um roteiro persuasivo no formato **Problema → Solução → Benefício → CTA** sobre **$topic**:",
                scriptContent = script
            )
        }

        // Default Case: Complete YouTube / Presentation / Class Script
        val script = """
            === ABERTURA & GANCHO ===
            [DICA]Sorriso acolhedor, olhar fixo na lente da câmera[/DICA]
            Fala pessoal, tudo bem com vocês? Hoje nós vamos mergulhar em um tema essencial: [DESTAQUE]$topic[/DESTAQUE].
            [PAUSA 2s]

            Se você quer entender de forma prática e sem enrolação como dominar esse assunto, fica comigo até o final deste vídeo!
            [PAUSA 2s]

            === 1. CONTEXTO E IMPORTÂNCIA ===
            Para começar, vale entender por que [DESTAQUE]$topic[/DESTAQUE] se tornou tão importante.
            Quando aplicamos os fundamentos certos, economizamos tempo e elevamos imediatamente a qualidade do nosso resultado.
            [PAUSA 3s]

            === 2. DICA PRÁTICA PASSO A PASSO ===
            [DICA]Mudar levemente a entonação para destacar a dica principal[/DICA]
            O ponto mais importante que você precisa lembrar é: [ALERTA]planejamento claro e execução consistente fazem toda a diferença[/ALERTA].
            Comece definindo seu objetivo principal, organize as etapas e valide cada resultado com calma.
            [PAUSA 3s]

            === CONCLUSÃO E ENCERRAMENTO ===
            Agora me conta aqui nos comentários: como você pretende aplicar [DESTAQUE]$topic[/DESTAQUE] no seu dia a dia?
            [PAUSA 2s]

            Não esqueça de deixar o seu gostei, se inscrever no canal e compartilhar este vídeo. Até o próximo episódio!
        """.trimIndent()

        return GeneratedScriptResult(
            title = "Roteiro: $topic",
            explanation = "Elaborei um roteiro completo sobre **$topic** já formatado com divisores de tópicos, dicas de câmera, destaques e pausas programadas:",
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

