package com.albinvpn.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

class ProfileRepository(context: Context) {
    private val prefs = context.getSharedPreferences("albinvpn_store", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun loadProfiles(): List<VpnProfile> =
        prefs.getString("profiles", null)?.let { runCatching { json.decodeFromString<List<VpnProfile>>(it) }.getOrNull() } ?: emptyList()

    fun saveProfiles(items: List<VpnProfile>) { prefs.edit().putString("profiles", json.encodeToString(items)).apply() }

    fun loadSubscriptions(): List<Subscription> =
        prefs.getString("subscriptions", null)?.let { runCatching { json.decodeFromString<List<Subscription>>(it) }.getOrNull() } ?: emptyList()

    fun saveSubscriptions(items: List<Subscription>) { prefs.edit().putString("subscriptions", json.encodeToString(items)).apply() }

    fun loadSettings(): AppSettings =
        prefs.getString("settings", null)?.let { runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull() } ?: AppSettings()

    fun saveSettings(settings: AppSettings) { prefs.edit().putString("settings", json.encodeToString(settings)).apply() }

    fun loadSelectedProfileId(): String? = prefs.getString("selected_profile_id", null)
    fun saveSelectedProfileId(id: String?) { prefs.edit().putString("selected_profile_id", id).apply() }

    suspend fun fetchSubscription(sub: Subscription): Pair<Subscription, List<VpnProfile>> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(sub.url)
            .header("User-Agent", "Albin-vpn/2.0 (Happ; Hiddify)")
            .build()
        http.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            val userInfo = resp.header("subscription-userinfo").orEmpty()
            var upload = 0L; var download = 0L; var total = 0L; var expire = 0L
            userInfo.split(";").forEach { token ->
                val kv = token.trim().split("=")
                if (kv.size == 2) {
                    val v = kv[1].toLongOrNull() ?: 0L
                    when (kv[0].lowercase()) {
                        "upload" -> upload = v; "download" -> download = v
                        "total" -> total = v; "expire" -> expire = v
                    }
                }
            }
            val updatedSub = sub.copy(
                lastUpdated = System.currentTimeMillis(),
                usedBytes = upload + download,
                totalBytes = total,
                expireTimestamp = expire
            )
            updatedSub to UriParser.parseBatch(body, sub.id)
        }
    }

    suspend fun measureTcpPing(host: String, port: Int): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val start = System.currentTimeMillis()
            Socket().use { socket -> socket.connect(InetSocketAddress(host, port), 2000) }
            (System.currentTimeMillis() - start).toInt()
        }.getOrNull()
    }
}
