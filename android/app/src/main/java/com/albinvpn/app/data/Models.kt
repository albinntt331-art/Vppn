package com.albinvpn.app.data

import android.graphics.Bitmap
import kotlinx.serialization.Serializable
import java.util.UUID

const val AUTO_PROFILE_ID = "__AUTO_BEST__"

@Serializable
enum class AppLanguage(val code: String, val nativeLabel: String) {
    RU("ru", "Русский"),
    EN("en", "English")
}

@Serializable
enum class VpnMode {
    TUN,
    PROXY_APPS
}

@Serializable
enum class PerAppPolicy {
    ONLY_SELECTED,
    EXCLUDE_SELECTED
}

@Serializable
enum class CoreEngineType {
    AUTO, XRAY, SING_BOX
}

@Serializable
enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

@Serializable
enum class AccentShade(val seedHex: Long, val labelRu: String, val labelEn: String) {
    PURPLE(0xFF8B5CF6, "Фиолетовый (База)", "Pixel Purple (Default)"),
    ULTRAVIOLET(0xFFA855F7, "Ультрафиолет", "Ultraviolet Neon"),
    OCEAN_BLUE(0xFF1A73E8, "Синий Google", "Google Blue"),
    EMERALD(0xFF00BFA5, "Кибер-изумруд", "Cyber Emerald"),
    ROSE(0xFFEC4899, "Неон-розовый", "Neon Rose"),
    AMBER(0xFFF59E0B, "Янтарный", "Solar Amber"),
    SYSTEM_DYNAMIC(0xFF8B5CF6, "Обои (Material You)", "Wallpaper (Material You)")
}

@Serializable
data class VpnProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: String,
    val address: String,
    val port: Int,
    val rawUri: String,
    val subscriptionId: String? = null,
    val pingMs: Int? = null,
    val isPriority: Boolean = false,
    val params: Map<String, String> = emptyMap()
)

@Serializable
data class Subscription(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val lastUpdated: Long = System.currentTimeMillis(),
    val usedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val expireTimestamp: Long = 0L
)

@Serializable
data class AppSettings(
    val language: AppLanguage = AppLanguage.RU,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentShade: AccentShade = AccentShade.PURPLE,
    val auroraEnabled: Boolean = true,
    val coreEngine: CoreEngineType = CoreEngineType.AUTO,
    val vpnMode: VpnMode = VpnMode.TUN,
    val perAppPolicy: PerAppPolicy = PerAppPolicy.ONLY_SELECTED,
    val selectedPackages: Set<String> = emptySet(),
    val tunMtu: Int = 1400,
    val ipv6Enabled: Boolean = false,
    val sniffingEnabled: Boolean = true,
    val remoteDns: String = "1.1.1.1",
    val fragmentEnabled: Boolean = false,
    val fragmentLength: String = "10-30",
    val fragmentInterval: String = "10-20",
    val muxEnabled: Boolean = false,
    val muxConcurrency: Int = 8
)

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

data class TrafficStats(
    val rxBytesPerSec: Long = 0L,
    val txBytesPerSec: Long = 0L,
    val totalRxBytes: Long = 0L,
    val totalTxBytes: Long = 0L,
    val rxHistory: List<Float> = List(26) { 0f },
    val txHistory: List<Float> = List(26) { 0f },
    val livePingMs: Int? = null,
    val connectedSeconds: Long = 0L,
    val activeServerName: String = "",
    val exitIpInfo: String? = null,
    val errorMessage: String? = null
)

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val icon: Bitmap?
)
