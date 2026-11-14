package com.example.proteleprompter.ui.adaptive

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.proteleprompter.ui.theme.BroadcastAmber
import com.example.proteleprompter.ui.theme.BroadcastCyan
import com.example.proteleprompter.ui.theme.BroadcastRed

enum class DeviceFormFactor(val label: String) {
    COMPACT_PHONE("Celular"),
    MEDIUM_TABLET("Tablet / Dobrável"),
    EXPANDED_STUDIO_OR_TV("Tablet Grande / TV")
}

data class AdaptiveWindowInfo(
    val formFactor: DeviceFormFactor,
    val isTvDevice: Boolean,
    val isLandscape: Boolean,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val gridColumns: Int,
    val horizontalPadding: Dp,
    val useSplitPane: Boolean,
    val useNavigationRail: Boolean,
    val basePrompterFontBoostSp: Int
)

@Composable
fun rememberAdaptiveWindowInfo(): AdaptiveWindowInfo {
    val configuration = LocalConfiguration.current
    return remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.orientation,
        configuration.uiMode
    ) {
        val widthDp = configuration.screenWidthDp
        val heightDp = configuration.screenHeightDp
        val isTv = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val formFactor = when {
            isTv || widthDp >= 840 -> DeviceFormFactor.EXPANDED_STUDIO_OR_TV
            widthDp >= 600 -> DeviceFormFactor.MEDIUM_TABLET
            else -> DeviceFormFactor.COMPACT_PHONE
        }

        val gridColumns = when {
            isTv || widthDp >= 1100 -> 3
            widthDp >= 600 -> 2
            else -> 1
        }

        val horizontalPadding = when {
            isTv -> 32.dp
            widthDp >= 840 -> 24.dp
            widthDp >= 600 -> 20.dp
            else -> 16.dp
        }

        AdaptiveWindowInfo(
            formFactor = formFactor,
            isTvDevice = isTv,
            isLandscape = isLandscape,
            screenWidthDp = widthDp,
            screenHeightDp = heightDp,
            gridColumns = gridColumns,
            horizontalPadding = horizontalPadding,
            useSplitPane = isTv || widthDp >= 720,
            useNavigationRail = isTv || widthDp >= 600,
            basePrompterFontBoostSp = when {
                isTv -> 14
                widthDp >= 840 -> 8
                widthDp >= 600 -> 4
                else -> 0
            }
        )
    }
}

/**
 * Custom vector composable matching the app's new adaptive launcher icon:
 * Beam-splitter monitor + active eye-line band + amber pointers + REC dot + Gemini AI sparkle.
 */
@Composable
fun ProTeleprompterBrandIcon(
    size: Dp = 42.dp,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.26f))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF060B14), Color(0xFF0E2341), Color(0xFF083344))
                )
            )
            .border(1.2.dp, BroadcastCyan.copy(alpha = 0.65f), RoundedCornerShape(size * 0.26f))
    ) {
        Canvas(modifier = Modifier.size(size * 0.78f)) {
            val w = this.size.width
            val h = this.size.height

            // Eye-line band
            drawRoundRect(
                color = BroadcastCyan.copy(alpha = 0.24f),
                topLeft = Offset(0f, h * 0.34f),
                size = Size(w, h * 0.32f),
                cornerRadius = CornerRadius(4f, 4f)
            )

            // Left Amber Pointer triangle
            val leftTriangle = Path().apply {
                moveTo(w * 0.04f, h * 0.40f)
                lineTo(w * 0.18f, h * 0.50f)
                lineTo(w * 0.04f, h * 0.60f)
                close()
            }
            drawPath(leftTriangle, color = BroadcastAmber)

            // Top dimmed line
            drawRoundRect(
                color = Color(0xFF94A3B8).copy(alpha = 0.55f),
                topLeft = Offset(w * 0.24f, h * 0.18f),
                size = Size(w * 0.52f, h * 0.08f),
                cornerRadius = CornerRadius(6f, 6f)
            )

            // Active White + Amber script lines
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.24f, h * 0.40f),
                size = Size(w * 0.64f, h * 0.09f),
                cornerRadius = CornerRadius(6f, 6f)
            )
            drawRoundRect(
                color = BroadcastAmber,
                topLeft = Offset(w * 0.24f, h * 0.54f),
                size = Size(w * 0.44f, h * 0.08f),
                cornerRadius = CornerRadius(6f, 6f)
            )

            // Bottom upcoming line
            drawRoundRect(
                color = Color(0xFF94A3B8).copy(alpha = 0.55f),
                topLeft = Offset(w * 0.24f, h * 0.74f),
                size = Size(w * 0.56f, h * 0.08f),
                cornerRadius = CornerRadius(6f, 6f)
            )

            // REC Red Dot
            drawCircle(
                color = BroadcastRed,
                radius = w * 0.065f,
                center = Offset(w * 0.11f, h * 0.18f)
            )

            // Gemini Sparkle Star (Top Right)
            drawCircle(
                color = BroadcastCyan,
                radius = w * 0.075f,
                center = Offset(w * 0.88f, h * 0.18f)
            )
        }
    }
}

