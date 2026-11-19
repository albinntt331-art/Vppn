package com.albinvpn.app.data

import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.net.URLDecoder

object UriParser {

    fun parseBatch(rawContent: String, subscriptionId: String? = null): List<VpnProfile> {
        val trimmed = rawContent.trim()
        val decoded = if (
            !trimmed.startsWith("vless://") &&
            !trimmed.startsWith("vmess://") &&
            !trimmed.startsWith("trojan://") &&
            !trimmed.startsWith("ss://") &&
            !trimmed.startsWith("hysteria2://") &&
            !trimmed.startsWith("hy2://") &&
            !trimmed.startsWith("wireguard://")
        ) {
            runCatching {
                val normalized = trimmed.replace('-', '+').replace('_', '/')
                String(Base64.decode(normalized, Base64.DEFAULT))
            }.getOrDefault(trimmed)
        } else {
            trimmed
        }

        return decoded.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains("://") }
            .mapNotNull { parseSingle(it, subscriptionId) }
    }

    fun parseSingle(uriStr: String, subscriptionId: String? = null): VpnProfile? {
        return when {
            uriStr.startsWith("vless://", ignoreCase = true) -> parseStandardUri("vless", uriStr, subscriptionId)
            uriStr.startsWith("trojan://", ignoreCase = true) -> parseStandardUri("trojan", uriStr, subscriptionId)
            uriStr.startsWith("hysteria2://", ignoreCase = true) || uriStr.startsWith("hy2://", ignoreCase = true) ->
                parseStandardUri("hysteria2", uriStr, subscriptionId)
            uriStr.startsWith("wireguard://", ignoreCase = true) -> parseStandardUri("wireguard", uriStr, subscriptionId)
            uriStr.startsWith("vmess://", ignoreCase = true) -> parseVmess(uriStr, subscriptionId)
            uriStr.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(uriStr, subscriptionId)
            else -> null
        }
    }

    private fun parseStandardUri(protocol: String, uriStr: String, subId: String?): VpnProfile? {
        return runCatching {
            val uri = Uri.parse(uriStr)
            val host = uri.host ?: return null
            val port = if (uri.port > 0) uri.port else 443
            val name = uri.fragment?.let { URLDecoder.decode(it, "UTF-8") }?.takeIf { it.isNotBlank() }
                ?: "$protocol-$host"
            val params = mutableMapOf<String, String>()
            uri.userInfo?.let { params["user"] = it }
            uri.queryParameterNames.forEach { key ->
                uri.getQueryParameter(key)?.let { params[key] = it }
            }
            VpnProfile(
                name = name,
                protocol = protocol,
                address = host,
                port = port,
                rawUri = uriStr,
                subscriptionId = subId,
                params = params
            )
        }.getOrNull()
    }

    private fun parseVmess(uriStr: String, subId: String?): VpnProfile? {
        return runCatching {
            val payload = uriStr.removePrefix("vmess://").trim()
            val jsonStr = String(Base64.decode(payload, Base64.DEFAULT))
            val obj = JSONObject(jsonStr)
            val host = obj.optString("add")
            val port = obj.optString("port", "443").toIntOrNull() ?: 443
            val name = obj.optString("ps").ifBlank { "vmess-$host" }
            val params = mutableMapOf<String, String>()
            obj.keys().forEach { k -> params[k] = obj.optString(k) }
            VpnProfile(name = name, protocol = "vmess", address = host, port = port, rawUri = uriStr, subscriptionId = subId, params = params)
        }.getOrNull()
    }

    private fun parseShadowsocks(uriStr: String, subId: String?): VpnProfile? {
        return runCatching {
            val uri = Uri.parse(uriStr)
            val name = uri.fragment?.let { URLDecoder.decode(it, "UTF-8") }?.takeIf { it.isNotBlank() } ?: "Shadowsocks"
            val host = uri.host ?: return null
            val port = if (uri.port > 0) uri.port else 8388
            val userInfo = uri.userInfo ?: ""
            val decodedAuth = runCatching {
                String(Base64.decode(userInfo, Base64.URL_SAFE or Base64.NO_WRAP))
            }.getOrDefault(userInfo)
            val parts = decodedAuth.split(":", limit = 2)
            val params = mutableMapOf(
                "method" to (parts.getOrNull(0) ?: "aes-256-gcm"),
                "password" to (parts.getOrNull(1) ?: "")
            )
            VpnProfile(name = name, protocol = "ss", address = host, port = port, rawUri = uriStr, subscriptionId = subId, params = params)
        }.getOrNull()
    }
}
