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
    private val tagStripRegex = Regex(
        """\[/?(DESTAQUE|ALERTA|DICA)]|\[PAUSA\s+\d+s]|===\s*.*?\s*===""",
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
     */
    fun buildStyledScript(
        rawText: String,
        defaultTextColor: Color = Color.White,
        activeWordRange: IntRange? = null
    ): AnnotatedString {
        return buildAnnotatedString {
            var cursor = 0
            val text = rawText
            val tokenRegex = Regex(
                """(\[DESTAQUE](.*?)\[/DESTAQUE])|(\[ALERTA](.*?)\[/ALERTA])|(\[DICA](.*?)\[/DICA])|(\[PAUSA\s+(\d+)s])|(===\s*(.*?)\s*===)""",
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

    private fun AnnotatedString.Builder.pushStyle(style: SpanStyle, start: Int, end: Int) {
        if (start < end) {
            addStyle(style, start, end)
        }
    }
}

