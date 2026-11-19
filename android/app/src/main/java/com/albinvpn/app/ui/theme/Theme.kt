package com.albinvpn.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.albinvpn.app.data.AccentShade
import com.albinvpn.app.data.ThemeMode

private val PurpleDark = darkColorScheme(
    primary = Color(0xFFD5B8FF), onPrimary = Color(0xFF280566),
    primaryContainer = Color(0xFF4F28A8), onPrimaryContainer = Color(0xFFEDE2FF),
    secondary = Color(0xFFCBB6F2), secondaryContainer = Color(0xFF362757),
    tertiary = Color(0xFFF472B6),
    background = Color(0xFF0A0812), surface = Color(0xFF0A0812),
    surfaceContainer = Color(0xFF151123), surfaceContainerHigh = Color(0xFF201A34),
    outlineVariant = Color(0xFF3A2F58)
)
private val PurpleLight = lightColorScheme(
    primary = Color(0xFF6D28D9), onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE2FF), onPrimaryContainer = Color(0xFF22005D),
    tertiary = Color(0xFFDB2777),
    background = Color(0xFFFCF8FF), surface = Color(0xFFFCF8FF),
    surfaceContainer = Color(0xFFF3EBFC), surfaceContainerHigh = Color(0xFFE8DCF8),
    outlineVariant = Color(0xFFD4C4EC)
)

private val UltraDark = darkColorScheme(
    primary = Color(0xFFE0AAFF), onPrimary = Color(0xFF3C096C),
    primaryContainer = Color(0xFF6B21A8), onPrimaryContainer = Color(0xFFF5E1FF),
    tertiary = Color(0xFF38BDF8),
    background = Color(0xFF090612), surface = Color(0xFF090612),
    surfaceContainer = Color(0xFF170F26), surfaceContainerHigh = Color(0xFF24173B),
    outlineVariant = Color(0xFF472C6E)
)
private val UltraLight = lightColorScheme(
    primary = Color(0xFF9333EA), onPrimary = Color.White,
    primaryContainer = Color(0xFFF3E8FF), onPrimaryContainer = Color(0xFF3B0764),
    tertiary = Color(0xFF0284C7),
    background = Color(0xFFFDF8FF), surface = Color(0xFFFDF8FF),
    surfaceContainer = Color(0xFFF5EBFE), surfaceContainerHigh = Color(0xFFEBD9FD)
)

private val BlueDark = darkColorScheme(
    primary = Color(0xFF9ECAFF), onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D), onPrimaryContainer = Color(0xFFD1E4FF),
    tertiary = Color(0xFFA78BFA),
    background = Color(0xFF090D16), surface = Color(0xFF090D16),
    surfaceContainer = Color(0xFF131A29), surfaceContainerHigh = Color(0xFF1C263B),
    outlineVariant = Color(0xFF2E3D5C)
)
private val BlueLight = lightColorScheme(
    primary = Color(0xFF1A73E8), onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E3FC), onPrimaryContainer = Color(0xFF001D36),
    tertiary = Color(0xFF7C3AED),
    background = Color(0xFFF8FAFF), surface = Color(0xFFF8FAFF),
    surfaceContainer = Color(0xFFECF2FC), surfaceContainerHigh = Color(0xFFDFEAFA)
)

private val EmeraldDark = darkColorScheme(
    primary = Color(0xFF5EEAD4), onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF00574B), onPrimaryContainer = Color(0xFF99F6E4),
    tertiary = Color(0xFF818CF8),
    background = Color(0xFF071211), surface = Color(0xFF071211),
    surfaceContainer = Color(0xFF10201E), surfaceContainerHigh = Color(0xFF192E2B),
    outlineVariant = Color(0xFF284A45)
)
private val EmeraldLight = lightColorScheme(
    primary = Color(0xFF00796B), onPrimary = Color.White,
    primaryContainer = Color(0xFFB2DFDB), onPrimaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF4F46E5),
    background = Color(0xFFF4FCFA), surface = Color(0xFFF4FCFA),
    surfaceContainer = Color(0xFFE6F4F1), surfaceContainerHigh = Color(0xFFD7ECE7)
)

private val RoseDark = darkColorScheme(
    primary = Color(0xFFF9A8D4), onPrimary = Color(0xFF500724),
    primaryContainer = Color(0xFF831843), onPrimaryContainer = Color(0xFFFCE7F3),
    tertiary = Color(0xFFC084FC),
    background = Color(0xFF14090F), surface = Color(0xFF14090F),
    surfaceContainer = Color(0xFF22111B), surfaceContainerHigh = Color(0xFF301926),
    outlineVariant = Color(0xFF522940)
)
private val RoseLight = lightColorScheme(
    primary = Color(0xFFDB2777), onPrimary = Color.White,
    primaryContainer = Color(0xFFFCE7F3), onPrimaryContainer = Color(0xFF500724),
    tertiary = Color(0xFF7E22CE),
    background = Color(0xFFFFF8FA), surface = Color(0xFFFFF8FA),
    surfaceContainer = Color(0xFFFCEBF2), surfaceContainerHigh = Color(0xFFF8DCE7)
)

private val AmberDark = darkColorScheme(
    primary = Color(0xFFFCD34D), onPrimary = Color(0xFF451A03),
    primaryContainer = Color(0xFF78350F), onPrimaryContainer = Color(0xFFFEF3C7),
    tertiary = Color(0xFFFB7185),
    background = Color(0xFF130E08), surface = Color(0xFF130E08),
    surfaceContainer = Color(0xFF21180E), surfaceContainerHigh = Color(0xFF2F2315),
    outlineVariant = Color(0xFF523D24)
)
private val AmberLight = lightColorScheme(
    primary = Color(0xFFD97706), onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7), onPrimaryContainer = Color(0xFF451A03),
    tertiary = Color(0xFFE11D48),
    background = Color(0xFFFFFAF4), surface = Color(0xFFFFFAF4),
    surfaceContainer = Color(0xFFF9EFE1), surfaceContainerHigh = Color(0xFFF2E2CC)
)

@Composable
fun AlbinVpnTheme(
    themeMode: ThemeMode,
    accentShade: AccentShade,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = if (accentShade == AccentShade.SYSTEM_DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        when (accentShade) {
            AccentShade.PURPLE, AccentShade.SYSTEM_DYNAMIC -> if (darkTheme) PurpleDark else PurpleLight
            AccentShade.ULTRAVIOLET -> if (darkTheme) UltraDark else UltraLight
            AccentShade.OCEAN_BLUE -> if (darkTheme) BlueDark else BlueLight
            AccentShade.EMERALD -> if (darkTheme) EmeraldDark else EmeraldLight
            AccentShade.ROSE -> if (darkTheme) RoseDark else RoseLight
            AccentShade.AMBER -> if (darkTheme) AmberDark else AmberLight
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
