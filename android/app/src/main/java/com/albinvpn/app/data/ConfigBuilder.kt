package com.albinvpn.app.data

import org.json.JSONArray
import org.json.JSONObject

object ConfigBuilder {

    fun buildXrayConfig(profile: VpnProfile, settings: AppSettings): String {
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))

        // Reliable DoH + IP DNS through Xray internal resolver (prevents DNS stalls on LTE/Wi-Fi)
        root.put("dns", JSONObject().apply {
            put("servers", JSONArray().apply {
                put("https+local://1.1.1.1/dns-query")
                put("1.1.1.1")
                put("8.8.8.8")
            })
            put("queryStrategy", if (settings.ipv6Enabled) "UseIP" else "UseIPv4")
        })

        val sniffing = JSONObject().apply {
            put("enabled", settings.sniffingEnabled)
            put("destOverride", JSONArray().put("http").put("tls").put("quic"))
            put("routeOnly", false)
        }

        val inbounds = JSONArray().apply {
            put(JSONObject().apply {
                put("tag", "tun-in")
                put("port", 0)
                put("protocol", "tun")
                put("settings", JSONObject().apply {
                    put("name", "xray0")
                    put("MTU", settings.tunMtu.coerceIn(1280, 1500))
                })
                put("sniffing", sniffing)
            })
            put(JSONObject().apply {
                put("tag", "socks-in")
                put("port", 10808)
                put("listen", "127.0.0.1")
                put("protocol", "socks")
                put("sniffing", sniffing)
                put("settings", JSONObject().put("udp", true))
            })
        }
        root.put("inbounds", inbounds)

        val outbounds = JSONArray().apply {
            put(buildXrayOutbound(profile, settings))
            put(JSONObject().put("tag", "direct").put("protocol", "freedom").apply {
                put("settings", JSONObject().put("domainStrategy", "UseIPv4"))
            })
            put(JSONObject().put("tag", "dns-out").put("protocol", "dns"))
            put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
            if (settings.fragmentEnabled) {
                put(JSONObject().apply {
                    put("tag", "fragment")
                    put("protocol", "freedom")
                    put("settings", JSONObject().put("fragment", JSONObject().apply {
                        put("packets", "tlshello")
                        put("length", settings.fragmentLength)
                        put("interval", settings.fragmentInterval)
                    }))
                })
            }
        }
        root.put("outbounds", outbounds)

        // 1) Hijack DNS port 53 to dns-out (resolves reliably over VPN)
        // 2) Keep private LAN direct so local Wi-Fi doesn't break
        // 3) Strictly force ALL other TCP/UDP traffic into proxy outbound
        val rules = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "field")
                put("inboundTag", JSONArray().put("tun-in"))
                put("port", 53)
                put("outboundTag", "dns-out")
            })
            put(JSONObject().apply {
                put("type", "field")
                put("ip", JSONArray().put("127.0.0.0/8").put("192.168.0.0/16").put("10.0.0.0/8").put("172.16.0.0/12"))
                put("outboundTag", "direct")
            })
            put(JSONObject().apply {
                put("type", "field")
                put("network", "tcp,udp")
                put("outboundTag", "proxy")
            })
        }
        root.put("routing", JSONObject().apply {
            put("domainStrategy", "AsIs")
            put("rules", rules)
        })

        return root.toString(2)
    }

    private fun buildXrayOutbound(profile: VpnProfile, settings: AppSettings): JSONObject {
        val p = profile.params
        val outbound = JSONObject().put("tag", "proxy")
        when (profile.protocol.lowercase()) {
            "vless" -> {
                outbound.put("protocol", "vless")
                val user = JSONObject().apply {
                    put("id", p["user"] ?: "")
                    put("encryption", p["encryption"] ?: "none")
                    p["flow"]?.takeIf { it.isNotBlank() }?.let { put("flow", it) }
                }
                outbound.put("settings", JSONObject().put("vnext", JSONArray().put(JSONObject().apply {
                    put("address", profile.address); put("port", profile.port); put("users", JSONArray().put(user))
                })))
            }
            "trojan" -> {
                outbound.put("protocol", "trojan")
                outbound.put("settings", JSONObject().put("servers", JSONArray().put(JSONObject().apply {
                    put("address", profile.address); put("port", profile.port); put("password", p["user"] ?: "")
                })))
            }
            "vmess" -> {
                outbound.put("protocol", "vmess")
                outbound.put("settings", JSONObject().put("vnext", JSONArray().put(JSONObject().apply {
                    put("address", profile.address); put("port", profile.port)
                    put("users", JSONArray().put(JSONObject().apply {
                        put("id", p["id"] ?: ""); put("security", p["scy"] ?: "auto")
                        put("alterId", p["aid"]?.toIntOrNull() ?: 0)
                    }))
                })))
            }
            else -> {
                outbound.put("protocol", "shadowsocks")
                outbound.put("settings", JSONObject().put("servers", JSONArray().put(JSONObject().apply {
                    put("address", profile.address); put("port", profile.port)
                    put("method", p["method"] ?: "aes-256-gcm"); put("password", p["password"] ?: "")
                })))
            }
        }

        val network = (p["type"] ?: p["net"] ?: "tcp").let { if (it == "raw") "tcp" else it }
        val security = p["security"] ?: p["tls"] ?: "none"
        val stream = JSONObject().put("network", network).put("security", security)
        if (security == "reality") {
            stream.put("realitySettings", JSONObject().apply {
                put("serverName", p["sni"] ?: ""); put("fingerprint", p["fp"] ?: "chrome")
                put("publicKey", p["pbk"] ?: ""); put("shortId", p["sid"] ?: ""); put("spiderX", p["spx"] ?: "/")
            })
        } else if (security == "tls") {
            stream.put("tlsSettings", JSONObject().apply {
                put("serverName", p["sni"] ?: profile.address); put("fingerprint", p["fp"] ?: "chrome")
                put("allowInsecure", p["allowInsecure"] == "1" || p["insecure"] == "1")
            })
        }
        when (network) {
            "ws" -> stream.put("wsSettings", JSONObject().apply {
                put("path", p["path"] ?: "/")
                p["host"]?.takeIf { it.isNotBlank() }?.let { put("headers", JSONObject().put("Host", it)) }
            })
            "grpc" -> stream.put("grpcSettings", JSONObject().apply {
                put("serviceName", p["serviceName"] ?: p["path"] ?: "")
                if (p["mode"] == "multi") put("multiMode", true)
            })
            "xhttp", "splithttp" -> stream.put("xhttpSettings", JSONObject().apply {
                put("path", p["path"] ?: "/")
                p["host"]?.takeIf { it.isNotBlank() }?.let { put("host", it) }
            })
        }
        val sockopt = JSONObject().put("tcpKeepAliveIdle", 30)
        if (settings.fragmentEnabled) sockopt.put("dialerProxy", "fragment")
        stream.put("sockopt", sockopt)
        outbound.put("streamSettings", stream)

        if (settings.muxEnabled && p["flow"].isNullOrBlank()) {
            outbound.put("mux", JSONObject().put("enabled", true).put("concurrency", settings.muxConcurrency))
        }
        return outbound
    }
}
