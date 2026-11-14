package com.example.proteleprompter.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
enum class PrompterBackground(val label: String, val colorHex: Long) {
    OLED_BLACK("Preto OLED (Beam Splitter)", 0xFF000000),
    STUDIO_NAVY("Azul Estúdio", 0xFF0A1120),
    HIGH_CONTRAST_YELLOW("Alto Contraste", 0xFF050505),
    CHROMA_GREEN("Chroma Key Verde", 0xFF00B140)
}

@Serializable
enum class ScriptFormatMode(
    val label: String,
    val shortLabel: String,
    val subtitle: String
) {
    TV_STUDIO(
        label = "Padrão Estúdio TV (Modelos do App)",
        shortLabel = "📺 Estúdio TV (Padrão App)",
        subtitle = "Blocos === ===, [DICA], [DESTAQUE], [ALERTA] e [PAUSA 2s] igual aos modelos do app"
    ),
    RADIO_ANNOUNCER(
        label = "Locução de Rádio (Pontuação Antecipada)",
        shortLabel = "🎙️ Locução de Rádio",
        subtitle = "Antecipa [? PERGUNTA] ¿, [! ENTUSIASMO] ¡ e [. AFIRMAÇÃO] no início da frase"
    )
}

@Serializable
data class Script(
    val id: String,
    val title: String,
    val folder: String = "Geral",
    val content: String,
    val wpm: Int = 140,
    val fontSizeSp: Int = 34,
    val lineHeightMultiplier: Float = 1.45f,
    val sideMarginPercent: Int = 8,
    val mirrorHorizontal: Boolean = false,
    val mirrorVertical: Boolean = false,
    val isFavorite: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val wordCount: Int
        get() = ScriptParser.countSpokenWords(content)

    val totalPauseSeconds: Int
        get() = ScriptParser.calculateTotalPauseSeconds(content)

    val estimatedDurationSeconds: Int
        get() {
            val safeWpm = wpm.coerceAtLeast(40)
            val speechSeconds = ((wordCount.toFloat() / safeWpm) * 60f).roundToInt()
            return speechSeconds + totalPauseSeconds
        }

    val formattedDuration: String
        get() {
            val total = estimatedDurationSeconds
            val mins = total / 60
            val secs = total % 60
            return "%02d:%02d".format(mins, secs)
        }

    val topicMarkers: List<TopicMarker>
        get() = ScriptParser.extractMarkers(content)
}

@Serializable
data class TopicMarker(
    val title: String,
    val lineIndex: Int,
    val charOffset: Int
)

@Serializable
data class PauseCue(
    val seconds: Int,
    val charIndex: Int,
    val progressFraction: Float
)

@Serializable
data class PrompterSettings(
    val countdownSeconds: Int = 3,
    val eyeLinePositionPercent: Float = 0.30f,
    val focusBandHeightDp: Int = 115,
    val focusShadeAlpha: Float = 0.65f,
    val backgroundTheme: PrompterBackground = PrompterBackground.OLED_BLACK,
    val textColorHex: Long = 0xFFFFFFFF,
    val useCameraOverlay: Boolean = false,
    val useFrontCamera: Boolean = true,
    val showCameraGrid: Boolean = true,
    val voiceScrollEnabled: Boolean = false,
    val volumeKeysControlSpeed: Boolean = true,
    val autoLoop: Boolean = false,
    val timedPausesEnabled: Boolean = true,
    val showTimerHud: Boolean = true,
    val showWpmHud: Boolean = true
)

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val folders: List<String>,
    val scripts: List<Script>,
    val settings: PrompterSettings
)

object ScriptParser {
    private val pauseRegex = Regex("""\[PAUSA\s+(\d+)s]""", RegexOption.IGNORE_CASE)
    private val radioPrefixRegex = Regex(
        """^\s*(\[\?\s*[^\]]*]\s*¿?|\[\!\s*[^\]]*]\s*¡?|\[\.\.\.\s*[^\]]*]|\[\.\s*[^\]]*])\s*""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )
    private val tagStripRegex = Regex(
        """\[/?(DESTAQUE|ALERTA|DICA)]|\[PAUSA\s+\d+s]|===\s*.*?\s*===|\[\?\s*[^\]]*]\s*¿?|\[\!\s*[^\]]*]\s*¡?|\[\.\.\.\s*[^\]]*]|\[\.\s*[^\]]*]|[¿¡]""",
        RegexOption.IGNORE_CASE
    )

    fun countSpokenWords(rawText: String): Int {
        val cleaned = rawText.replace(tagStripRegex, " ").trim()
        if (cleaned.isBlank()) return 0
        return cleaned.split(Regex("""\s+""")).count { it.isNotBlank() }
    }

    fun calculateTotalPauseSeconds(rawText: String): Int {
        return pauseRegex.findAll(rawText).sumOf { match ->
            match.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
        }
    }

    fun extractMarkers(rawText: String): List<TopicMarker> {
        val markers = mutableListOf<TopicMarker>()
        val lines = rawText.lines()
        var runningOffset = 0
        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("===") && trimmed.endsWith("===") && trimmed.length > 6) {
                val label = trimmed.removePrefix("===").removeSuffix("===").trim()
                if (label.isNotEmpty()) {
                    markers.add(
                        TopicMarker(
                            title = label,
                            lineIndex = index,
                            charOffset = runningOffset
                        )
                    )
                }
            }
            runningOffset += line.length + 1
        }
        return markers
    }

    fun extractPauseCues(rawText: String): List<PauseCue> {
        if (rawText.isEmpty()) return emptyList()
        val totalLen = rawText.length.coerceAtLeast(1)
        return pauseRegex.findAll(rawText).map { match ->
            val secs = match.groupValues.getOrNull(1)?.toIntOrNull() ?: 2
            PauseCue(
                seconds = secs,
                charIndex = match.range.first,
                progressFraction = (match.range.first.toFloat() / totalLen.toFloat()).coerceIn(0f, 1f)
            )
        }.toList()
    }

    /**
     * Converts studio markup into a styled AnnotatedString for both preview and live teleprompter.
     * Supported tags:
     * - [DESTAQUE]texto[/DESTAQUE] -> Gold/Yellow bold highlight
     * - [ALERTA]texto[/ALERTA] -> Coral Red bold emphasis
     * - [DICA]instrução de cena[/DICA] -> Cyan italic stage direction
     * - [PAUSA 3s] -> Visual badge for timed pause
     * - === TÓPICO === -> Section header divider
     * - [? PERGUNTA] ¿ / [! EXCLAMAÇÃO] ¡ / [. AFIRMAÇÃO] / [... SUSPENSE] -> Radio Announcer anticipated intonation cues
     */
    fun normalizeUnclosedTags(rawText: String): String {
        return rawText.lines().joinToString("\n") { line ->
            var fixed = line
            listOf("DICA", "ALERTA", "DESTAQUE").forEach { tag ->
                val openRegex = Regex("""\[$tag]\s*""", RegexOption.IGNORE_CASE)
                val closeRegex = Regex("""\[/$tag]""", RegexOption.IGNORE_CASE)
                if (openRegex.containsMatchIn(fixed) && !closeRegex.containsMatchIn(fixed)) {
                    fixed = fixed.replace(openRegex, "[$tag]").trimEnd() + "[/$tag]"
                }
            }
            fixed
        }
    }

    fun buildStyledScript(
        rawText: String,
        defaultTextColor: Color = Color.White,
        activeWordRange: IntRange? = null
    ): AnnotatedString {
        return buildAnnotatedString {
            var cursor = 0
            val text = normalizeUnclosedTags(rawText)
            val tokenRegex = Regex(
                """(\[DESTAQUE](.*?)\[/DESTAQUE])|(\[ALERTA](.*?)\[/ALERTA])|(\[DICA](.*?)\[/DICA])|(\[PAUSA\s+(\d+)s])|(===\s*(.*?)\s*===)|(\[\?\s*([^\]]*)]\s*¿?)|(\[\!\s*([^\]]*)]\s*¡?)|(\[\.\.\.\s*([^\]]*)])|(\[\.\s*([^\]]*)])""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )

            for (match in tokenRegex.findAll(text)) {
                if (match.range.first > cursor) {
                    val plainChunk = text.substring(cursor, match.range.first)
                    val startIdx = length
                    append(plainChunk)
                    pushStyle(SpanStyle(color = defaultTextColor), startIdx, length)
                }

                when {
                    match.groups[1] != null -> {
                        val inner = match.groups[2]?.value.orEmpty()
                        val startIdx = length
                        append(inner)
                        addStyle(
                            SpanStyle(
                                color = Color(0xFFFBBF24),
                                background = Color(0x33FBBF24),
                                fontWeight = FontWeight.ExtraBold
                            ),
                            startIdx,
                            length
                        )
                    }
                    match.groups[3] != null -> {
                        val inner = match.groups[4]?.value.orEmpty()
                        val startIdx = length
                        append(inner)
                        addStyle(
                            SpanStyle(
                                color = Color(0xFFFF6B6B),
                                background = Color(0x33EF4444),
                                fontWeight = FontWeight.ExtraBold,
                                textDecoration = TextDecoration.Underline
                            ),
                            startIdx,
                            length
                        )
                    }
                    match.groups[5] != null -> {
                        val inner = match.groups[6]?.value.orEmpty()
                        val startIdx = length
                        append(" 🎬 [$inner] ")
                        addStyle(
                            SpanStyle(
                                color = Color(0xFF38BDF8),
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.SemiBold
                            ),
                            startIdx,
                            length
                        )
                    }
                    match.groups[7] != null -> {
                        val secs = match.groups[8]?.value ?: "2"
                        val startIdx = length
                        append(" ⏸ PAUSA ${secs}s ")
                        addStyle(
                            SpanStyle(
                                color = Color(0xFFA78BFA),
                                background = Color(0x33A78BFA),
                                fontWeight = FontWeight.Bold
                            ),
                            startIdx,
                            length
                        )
                    }
                    match.groups[9] != null -> {
                        val topic = match.groups[10]?.value.orEmpty().uppercase()
                        val startIdx = length
                        append("▸ $topic")
                        addStyle(
                            SpanStyle(
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Black
                            ),
                            startIdx,
                            length
                        )
                    }
                    match.groups[11] != null -> {
                        val label = match.groups[12]?.value?.trim()?.takeIf { it.isNotEmpty() } ?: "PERGUNTA"
                        val startIdx = length
                        append("❓ [$label] ¿ ")
                        addStyle(
                            SpanStyle(
                                color = Color(0xFF38BDF8),
                                background = Color(0x3338BDF8),
                                fontWeight = FontWeight.ExtraBold
                            ),
                            startIdx,
                            length
                        )
                    }
                    match.groups[13] != null -> {
                        val label = match.groups[14]?.value?.trim()?.takeIf { it.isNotEmpty() } ?: "ENTUSIASMO"
                        val startIdx = length
                        append("❗ [$label] ¡ ")
                        addStyle(
                            SpanStyle(
                                color = Color(0xFFFBBF24),
                                background = Color(0x33FBBF24),
                                fontWeight = FontWeight.ExtraBold
                            ),
                            startIdx,
                            length
                        )
                    }
                    match.groups[15] != null -> {
                        val label = match.groups[16]?.value?.trim()?.takeIf { it.isNotEmpty() } ?: "SUSPENSE"
                        val startIdx = length
                        append("〰️ [$label] ")
                        addStyle(
                            SpanStyle(
                                color = Color(0xFFC084FC),
                                background = Color(0x33C084FC),
                                fontWeight = FontWeight.Bold
                            ),
                            startIdx,
                            length
                        )
                    }
                    match.groups[17] != null -> {
                        val label = match.groups[18]?.value?.trim()?.takeIf { it.isNotEmpty() } ?: "AFIRMAÇÃO"
                        val startIdx = length
                        append("⏺ [$label] ")
                        addStyle(
                            SpanStyle(
                                color = Color(0xFF34D399),
                                background = Color(0x2610B981),
                                fontWeight = FontWeight.Bold
                            ),
                            startIdx,
                            length
                        )
                    }
                }
                cursor = match.range.last + 1
            }

            if (cursor < text.length) {
                val remainder = text.substring(cursor)
                val startIdx = length
                append(remainder)
                addStyle(SpanStyle(color = defaultTextColor), startIdx, length)
            }

            if (activeWordRange != null &&
                activeWordRange.first >= 0 &&
                activeWordRange.last < length &&
                activeWordRange.first <= activeWordRange.last
            ) {
                addStyle(
                    SpanStyle(
                        background = Color(0x6638BDF8),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    activeWordRange.first,
                    activeWordRange.last + 1
                )
            }
        }
    }

    /**
     * Converts any script into the selected professional format:
     * - TV_STUDIO: Structured with === BLOCO ===, [DICA], [DESTAQUE], [ALERTA], and [PAUSA 2s] like the built-in app models.
     * - RADIO_ANNOUNCER: Structured for radio/voiceover with anticipated punctuation at the start of every sentence
     *   ([? PERGUNTA] ¿, [! ENTUSIASMO] ¡, [. AFIRMAÇÃO], [... TRANSIÇÃO]) so the speaker knows the intonation upfront.
     */
    fun applyProfessionalFormat(rawScript: String, mode: ScriptFormatMode): String {
        return when (mode) {
            ScriptFormatMode.TV_STUDIO -> formatAsTvStudio(rawScript)
            ScriptFormatMode.RADIO_ANNOUNCER -> formatAsRadioAnnouncer(rawScript)
        }
    }

    fun formatAsTvStudio(rawScript: String): String {
        // Strip radio anticipated punctuation markers if converting from Radio mode
        var cleaned = normalizeUnclosedTags(rawScript)
            .replace(radioPrefixRegex, "")
            .replace(Regex("""^\s*[¿¡]\s*""", RegexOption.MULTILINE), "")
            .trim()

        val hasBlocks = cleaned.contains("===")
        val hasDica = cleaned.contains("[DICA", ignoreCase = true)
        val hasPause = cleaned.contains("[PAUSA", ignoreCase = true)

        if (hasBlocks && (hasDica || hasPause)) {
            return cleaned
        }

        // Organize plain paragraphs into the exact TV Studio model structure
        val paragraphs = cleaned
            .split(Regex("""\n\s*\n"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (paragraphs.isEmpty()) return cleaned

        return buildString {
            paragraphs.forEachIndexed { idx, para ->
                when {
                    idx == 0 -> {
                        if (!para.startsWith("===")) {
                            appendLine("=== ABERTURA & GANCHO ===")
                            if (!para.contains("[DICA", ignoreCase = true)) {
                                appendLine("[DICA]Olhar firme na lente, sorriso leve e energia alta[/DICA]")
                            }
                        }
                        appendLine(highlightKeyPhraseIfNeeded(para, isHighlight = true))
                        if (!para.contains("[PAUSA", ignoreCase = true)) {
                            appendLine("[PAUSA 2s]")
                        }
                    }
                    idx == paragraphs.lastIndex && paragraphs.size > 1 -> {
                        if (!para.startsWith("===")) {
                            appendLine()
                            appendLine("=== ENCERRAMENTO & CHAMADA (CTA) ===")
                            if (!para.contains("[DICA", ignoreCase = true)) {
                                appendLine("[DICA]Tom conclusivo, apontando ou convidando para ação[/DICA]")
                            }
                        }
                        appendLine(highlightKeyPhraseIfNeeded(para, isHighlight = false))
                    }
                    else -> {
                        if (idx == 1 && !para.startsWith("===")) {
                            appendLine()
                            appendLine("=== DESENVOLVIMENTO PRINCIPAL ===")
                            if (!para.contains("[DICA", ignoreCase = true)) {
                                appendLine("[DICA]Ritmo natural e articulado[/DICA]")
                            }
                        } else {
                            appendLine()
                        }
                        appendLine(highlightKeyPhraseIfNeeded(para, isHighlight = idx % 2 == 0))
                        if (!para.contains("[PAUSA", ignoreCase = true)) {
                            appendLine("[PAUSA 2s]")
                        }
                    }
                }
            }
        }.trim()
    }

    fun formatAsRadioAnnouncer(rawScript: String): String {
        val baseStudio = formatAsTvStudio(rawScript)
        val lines = baseStudio.lines()
        val result = mutableListOf<String>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() ||
                line.startsWith("===") ||
                line.startsWith("[DICA", ignoreCase = true) ||
                line.startsWith("[PAUSA", ignoreCase = true)
            ) {
                result.add(line)
                continue
            }

            // Split paragraph into individual sentences and prepend anticipated punctuation cue to each sentence
            val cleanLine = line.replace(radioPrefixRegex, "").trim()
            val sentences = cleanLine.split(Regex("""(?<=[.!?…])\s+""")).filter { it.isNotBlank() }

            if (sentences.isEmpty()) {
                result.add(line)
            } else {
                sentences.forEach { sentence ->
                    val s = sentence.trim().removePrefix("¿").removePrefix("¡").trim()
                    val plainCheck = s.replace(tagStripRegex, "").trim()
                    val formattedSentence = when {
                        plainCheck.endsWith("?") -> "[? PERGUNTA] ¿ $s"
                        plainCheck.endsWith("!") -> "[! ENTUSIASMO] ¡ $s"
                        plainCheck.endsWith("...") || plainCheck.endsWith("…") -> "[... TRANSIÇÃO] $s"
                        else -> "[. AFIRMAÇÃO] $s"
                    }
                    result.add(formattedSentence)
                }
            }
        }

        return result.joinToString("\n").replace("[DICA]Olhar firme na lente", "[DICA]Postura de locução, sorriso na voz")
    }

    private fun highlightKeyPhraseIfNeeded(paragraph: String, isHighlight: Boolean): String {
        if (paragraph.contains("[DESTAQUE", ignoreCase = true) ||
            paragraph.contains("[ALERTA", ignoreCase = true) ||
            paragraph.startsWith("===") ||
            paragraph.startsWith("[")
        ) {
            return paragraph
        }
        val words = paragraph.split(" ")
        if (words.size < 7) return paragraph
        val tag = if (isHighlight) "DESTAQUE" else "ALERTA"
        val start = (words.size / 3).coerceAtLeast(1)
        val end = (start + 4).coerceAtMost(words.size - 1)
        val before = words.subList(0, start).joinToString(" ")
        val middle = words.subList(start, end).joinToString(" ")
        val after = words.subList(end, words.size).joinToString(" ")
        return "$before [$tag]$middle[/$tag] $after"
    }

    private fun AnnotatedString.Builder.pushStyle(style: SpanStyle, start: Int, end: Int) {
        if (start < end) {
            addStyle(style, start, end)
        }
    }
}

