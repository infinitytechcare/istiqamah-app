package com.istiqamah.mac

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp
import com.istiqamah.app.core.PrayerStatus

val Emerald900 = Color(0xFF062A20)
val Emerald800 = Color(0xFF0B3D2E)
val Emerald600 = Color(0xFF17694F)
val Emerald50 = Color(0xFFE8F3EE)
val Gold = Color(0xFFD4AF37)
val GoldLight = Color(0xFFF1D98A)
val Cream = Color(0xFFFBF7EE)
val Ink = Color(0xFF1E2B26)
val Muted = Color(0xFF6B7A73)
val Line = Color(0xFFE6E0D2)

fun statusColor(s: PrayerStatus): Color = when (s) {
    PrayerStatus.JAMAAH -> Color(0xFF17694F)
    PrayerStatus.ALONE -> Color(0xFF4FA27F)
    PrayerStatus.QAZA -> Color(0xFFD9A441)
    PrayerStatus.MISSED -> Color(0xFF9AA5A0)
    PrayerStatus.EXCUSED -> Color(0xFFB9A6CF)
}

private fun bytes(name: String): ByteArray? =
    MacApp::class.java.classLoader.getResourceAsStream(name)?.use { it.readBytes() }

private fun family(vararg fonts: Pair<String, FontWeight>): FontFamily {
    val list = fonts.mapNotNull { (n, w) -> bytes(n)?.let { Font(identity = n, data = it, weight = w) } }
    return if (list.isEmpty()) FontFamily.Default else FontFamily(list)
}

/** হিন্দ শিলিগুড়ি — ফোনের অ্যাপের একই বাংলা ফন্ট */
val Bangla: FontFamily by lazy {
    family(
        "hindsiliguri_regular.ttf" to FontWeight.Normal,
        "hindsiliguri_semibold.ttf" to FontWeight.SemiBold,
        "hindsiliguri_bold.ttf" to FontWeight.Bold,
    )
}

val Quranic: FontFamily by lazy { family("amiri_quran.ttf" to FontWeight.Normal) }

@Composable
fun MacTheme(content: @Composable () -> Unit) {
    val base = Typography()
    fun TextStyle.bn() = copy(fontFamily = Bangla)
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Emerald600, onPrimary = Color.White,
            primaryContainer = Emerald50, onPrimaryContainer = Emerald900,
            secondary = Gold, onSecondary = Emerald900,
            background = Cream, onBackground = Ink,
            surface = Color.White, onSurface = Ink,
            surfaceVariant = Emerald50, onSurfaceVariant = Muted,
            outline = Line,
        ),
        typography = Typography(
            displaySmall = base.displaySmall.bn(),
            headlineMedium = base.headlineMedium.bn(),
            headlineSmall = base.headlineSmall.bn(),
            titleLarge = base.titleLarge.bn().copy(fontWeight = FontWeight.Bold),
            titleMedium = base.titleMedium.bn().copy(fontWeight = FontWeight.SemiBold),
            titleSmall = base.titleSmall.bn().copy(fontWeight = FontWeight.SemiBold),
            bodyLarge = base.bodyLarge.bn().copy(fontSize = 15.sp, lineHeight = 24.sp),
            bodyMedium = base.bodyMedium.bn().copy(fontSize = 14.sp, lineHeight = 22.sp),
            bodySmall = base.bodySmall.bn().copy(fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = base.labelLarge.bn().copy(fontWeight = FontWeight.SemiBold),
            labelMedium = base.labelMedium.bn(),
            labelSmall = base.labelSmall.bn(),
        ),
        content = content,
    )
}

/** মেনু বারের চাঁদ; চলমান ওয়াক্ত চিহ্নহীন থাকলে নিচে ছোট বিন্দু */
class CrescentPainter(private val dot: Boolean) : Painter() {
    override val intrinsicSize: Size = Size(64f, 64f)

    override fun DrawScope.onDraw() {
        val w = size.width
        val h = size.height
        val r = w * 0.36f
        val c = Offset(w * 0.46f, h * 0.46f)
        val outer = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
        val inner = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(c.x + r * 0.45f, c.y - r * 0.18f), r * 0.86f)) }
        val moon = Path().apply { op(outer, inner, PathOperation.Difference) }
        drawPath(moon, Gold)
        if (dot) drawCircle(Color(0xFFE2553F), radius = w * 0.13f, center = Offset(w * 0.80f, h * 0.80f))
    }
}
