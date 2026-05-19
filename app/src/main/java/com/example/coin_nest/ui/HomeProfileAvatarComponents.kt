package com.example.coin_nest.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
internal fun LineAvatarArt(presetId: String, modifier: Modifier = Modifier) {
    val palette = lineAvatarPalette(presetId)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val faceCenter = Offset(cx, h * 0.42f)
        val faceRadius = minOf(w, h) * 0.25f
        val strokeWidth = minOf(w, h) * 0.045f

        drawCircle(color = palette.skin, radius = faceRadius, center = faceCenter)
        drawArc(
            color = palette.hair,
            startAngle = 198f,
            sweepAngle = 144f,
            useCenter = false,
            topLeft = Offset(faceCenter.x - faceRadius * 0.95f, faceCenter.y - faceRadius * 1.02f),
            size = Size(faceRadius * 1.9f, faceRadius * 1.52f),
            style = Stroke(width = strokeWidth * 1.45f, cap = StrokeCap.Round)
        )
        drawArc(
            color = palette.hair,
            startAngle = 218f,
            sweepAngle = 104f,
            useCenter = false,
            topLeft = Offset(faceCenter.x - faceRadius * 0.78f, faceCenter.y - faceRadius * 0.88f),
            size = Size(faceRadius * 1.56f, faceRadius * 1.16f),
            style = Stroke(width = strokeWidth * 0.92f, cap = StrokeCap.Round)
        )
        drawCircle(
            color = palette.line,
            radius = strokeWidth * 0.68f,
            center = Offset(faceCenter.x - faceRadius * 0.34f, faceCenter.y - faceRadius * 0.02f)
        )
        drawCircle(
            color = palette.line,
            radius = strokeWidth * 0.68f,
            center = Offset(faceCenter.x + faceRadius * 0.34f, faceCenter.y - faceRadius * 0.02f)
        )
        drawArc(
            color = palette.line,
            startAngle = 28f,
            sweepAngle = 124f,
            useCenter = false,
            topLeft = Offset(faceCenter.x - faceRadius * 0.33f, faceCenter.y + faceRadius * 0.05f),
            size = Size(faceRadius * 0.66f, faceRadius * 0.42f),
            style = Stroke(width = strokeWidth * 0.72f, cap = StrokeCap.Round)
        )
        drawArc(
            color = palette.accent,
            startAngle = 208f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(cx - w * 0.31f, h * 0.60f),
            size = Size(w * 0.62f, h * 0.32f),
            style = Stroke(width = strokeWidth * 1.65f, cap = StrokeCap.Round)
        )
        drawLine(
            color = palette.accent,
            start = Offset(cx - w * 0.22f, h * 0.76f),
            end = Offset(cx + w * 0.22f, h * 0.76f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
internal fun avatarFrameColor(reward: RewardDefinition?): Color {
    return when (reward?.kind) {
        RewardKind.AvatarFrame -> Color(0xFFFFD166)
        RewardKind.Title -> Color(0xFF84DCC6)
        RewardKind.ConsoleSkin -> Color(0xFFA7F3D0)
        else -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
    }
}

internal fun avatarPresetColor(id: String): Brush {
    val colors = when (id) {
        "nami" -> listOf(Color(0xFFEFF6FF), Color(0xFFBAE6FD))
        "yuzu" -> listOf(Color(0xFFFFF7ED), Color(0xFFFDE68A))
        "luna" -> listOf(Color(0xFFFAF5FF), Color(0xFFE9D5FF))
        "mika" -> listOf(Color(0xFFF8FAFC), Color(0xFFCBD5E1))
        "aoki" -> listOf(Color(0xFFECFDF5), Color(0xFFA7F3D0))
        else -> listOf(Color(0xFF0D7377), Color(0xFF84DCC6))
    }
    return Brush.linearGradient(colors)
}

private data class LineAvatarPalette(
    val skin: Color,
    val hair: Color,
    val line: Color,
    val accent: Color
)

private fun lineAvatarPalette(id: String): LineAvatarPalette {
    return when (id) {
        "nami" -> LineAvatarPalette(Color(0xFFFFE0C7), Color(0xFF1D4ED8), Color(0xFF0F172A), Color(0xFF38BDF8))
        "yuzu" -> LineAvatarPalette(Color(0xFFFFDFB4), Color(0xFFB7791F), Color(0xFF3A2A12), Color(0xFFFFD166))
        "luna" -> LineAvatarPalette(Color(0xFFFFD9E8), Color(0xFF7C3AED), Color(0xFF20163A), Color(0xFFC4B5FD))
        "mika" -> LineAvatarPalette(Color(0xFFFFE5D0), Color(0xFF111827), Color(0xFF111827), Color(0xFF94A3B8))
        "aoki" -> LineAvatarPalette(Color(0xFFD8FFF4), Color(0xFF0D7377), Color(0xFF073B3A), Color(0xFF84DCC6))
        else -> LineAvatarPalette(Color(0xFFFFE1CC), Color(0xFF0F766E), Color(0xFF103B38), Color(0xFF84DCC6))
    }
}
