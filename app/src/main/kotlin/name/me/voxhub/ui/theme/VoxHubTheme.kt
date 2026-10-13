package name.me.voxhub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

private val LightColors = lightColorScheme(
    primary = Color(0xFF315DA8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2EBFF),
    onPrimaryContainer = Color(0xFF182A3F),
    secondary = Color(0xFF596474),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE3ED),
    onSecondaryContainer = Color(0xFF1B2532),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF7F8FA),
    onSurface = Color(0xFF191C20),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFBFC),
    surfaceContainerHigh = Color(0xFFEEF0F3),
    onSurfaceVariant = Color(0xFF6F747C),
    outline = Color(0xFF7B8088),
    outlineVariant = Color(0xFFDFE2E6),
    error = Color(0xFF9B4D4D),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD8),
    onErrorContainer = Color(0xFF3B080A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF23364D),
    primaryContainer = Color(0xFF3A506A),
    onPrimaryContainer = Color(0xFFE2EBFF),
    secondary = Color(0xFFBBC7D7),
    onSecondary = Color(0xFF26313E),
    secondaryContainer = Color(0xFF3C4755),
    onSecondaryContainer = Color(0xFFDDE3ED),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE4E7EE),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE4E7EE),
    surfaceContainer = Color(0xFF1B1F27),
    surfaceContainerLow = Color(0xFF1A1D21),
    surfaceContainerHigh = Color(0xFF2A2E35),
    onSurfaceVariant = Color(0xFFA7ADB5),
    outline = Color(0xFF9298A1),
    outlineVariant = Color(0xFF343941),
    error = Color(0xFFD98B8B),
    onError = Color(0xFF561D20),
    errorContainer = Color(0xFF733337),
    onErrorContainer = Color(0xFFFFDAD8),
)

@Immutable
data class StatusColors(
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
)

private val LightStatusColors = StatusColors(
    success = Color(0xFF3F7659),
    successContainer = Color(0xFFD4EBDD),
    warning = Color(0xFF966D29),
    warningContainer = Color(0xFFFFE5B4),
)

private val DarkStatusColors = StatusColors(
    success = Color(0xFF82B89A),
    successContainer = Color(0xFF28513B),
    warning = Color(0xFFD2AA65),
    warningContainer = Color(0xFF5C451B),
)

val LocalStatusColors = compositionLocalOf { LightStatusColors }

private val AppShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
)

@Composable
fun VoxHubTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val dark = darkTheme
    androidx.compose.runtime.CompositionLocalProvider(
        LocalStatusColors provides if (dark) DarkStatusColors else LightStatusColors,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = Typography(
                headlineSmall = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
                titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
                titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
            ),
            shapes = AppShapes,
            content = content,
        )
    }
}
