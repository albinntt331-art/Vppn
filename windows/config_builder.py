"""Dual-core config builder for Albin-vpn Windows: Strict TUN and Per-App Proxy Apps modes."""
import json


def choose_core(profile: dict, preferred: str) -> str:
    if preferred in ("xray", "sing-box"):
        return preferred
    if profile.get("protocol") in ("hysteria2", "wireguard", "tuic"):
        return "sing-box"
    return "xray"


def build_xray_config(profile: dict, settings: dict) -> str:
    p = profile.get("params", {})
    proto = profile.get("protocol", "vless")
    mode = settings.get("vpn_mode", "tun")  # "tun" or "proxy_apps"
    policy = settings.get("per_app_policy", "only_selected")
    apps = [a.strip() for a in settings.get("selected_apps", []) if a.strip()]

    sniffing = {"enabled": settings.get("sniffing", True), "destOverride": ["http", "tls", "quic"]}

    outbound = {"tag": "proxy", "protocol": proto}
    if proto == "vless":
        user = {"id": p.get("user", ""), "encryption": p.get("encryption", "none")}
        if p.get("flow"):
            user["flow"] = p["flow"]
        outbound["settings"] = {"vnext": [{"address": profile["address"], "port": profile["port"], "users": [user]}]}
    elif proto == "trojan":
        outbound["settings"] = {"servers": [{"address": profile["address"], "port": profile["port"], "password": p.get("user", "")}]}
    elif proto == "vmess":
        outbound["settings"] = {
            "vnext": [{
                "address": profile["address"],
                "port": profile["port"],
                "users": [{"id": p.get("id", ""), "security": p.get("scy", "auto")}],
            }]
        }
    else:
        outbound["protocol"] = "shadowsocks"
        outbound["settings"] = {
            "servers": [{
                "address": profile["address"],
                "port": profile["port"],
                "method": p.get("method", "aes-256-gcm"),
                "password": p.get("password", ""),
            }]
        }

    net = p.get("type") or p.get("net") or "tcp"
    if net == "raw":
        net = "tcp"
    sec = p.get("security") or p.get("tls") or "none"
    stream = {"network": net, "security": sec}
    if sec == "reality":
        stream["realitySettings"] = {
            "serverName": p.get("sni", ""),
            "fingerprint": p.get("fp", "chrome"),
            "publicKey": p.get("pbk", ""),
            "shortId": p.get("sid", ""),
            "spiderX": p.get("spx", "/"),
        }
    elif sec == "tls":
        stream["tlsSettings"] = {"serverName": p.get("sni", profile["address"]), "fingerprint": p.get("fp", "chrome")}
    if net == "ws":
        stream["wsSettings"] = {"path": p.get("path", "/"), "headers": {"Host": p.get("host", profile["address"])}}
    elif net == "grpc":
        stream["grpcSettings"] = {"serviceName": p.get("serviceName", "")}
    if settings.get("fragment", False):
        stream["sockopt"] = {"dialerProxy": "fragment"}
    outbound["streamSettings"] = stream

    if settings.get("mux", False) and not p.get("flow"):
        outbound["mux"] = {"enabled": True, "concurrency": 8}

    outbounds = [outbound, {"tag": "direct", "protocol": "freedom"}, {"tag": "block", "protocol": "blackhole"}]
    if settings.get("fragment", False):
        outbounds.append({
            "tag": "fragment",
            "protocol": "freedom",
            "settings": {"fragment": {"packets": "tlshello", "length": "10-30", "interval": "10-20"}},
        })

    inbounds = [
        {"tag": "socks-in", "port": 10808, "listen": "127.0.0.1", "protocol": "socks", "sniffing": sniffing, "settings": {"udp": True}},
        {"tag": "http-in", "port": 10809, "listen": "127.0.0.1", "protocol": "http", "sniffing": sniffing},
    ]
    if mode == "tun":
        inbounds.insert(0, {
            "tag": "tun-in",
            "port": 0,
            "protocol": "tun",
            "settings": {"name": "AlbinTUN", "desc": "Wintun", "MTU": 1500},
            "sniffing": sniffing,
        })

    rules = []
    if mode == "proxy_apps" and apps:
        if policy == "only_selected":
            rules.append({"type": "field", "process": apps, "outboundTag": "proxy"})
            rules.append({"type": "field", "network": "tcp,udp", "outboundTag": "direct"})
        else:
            rules.append({"type": "field", "process": apps, "outboundTag": "direct"})
            rules.append({"type": "field", "network": "tcp,udp", "outboundTag": "proxy"})
    else:
        # Strict TUN: everything goes into VPN
        rules.append({"type": "field", "network": "tcp,udp", "outboundTag": "proxy"})

    cfg = {
        "log": {"loglevel": "warning"},
        "dns": {
            "servers": [settings.get("remote_dns", "1.1.1.1"), "8.8.8.8"],
            "queryStrategy": "UseIP" if settings.get("ipv6", False) else "UseIPv4",
        },
        "inbounds": inbounds,
        "outbounds": outbounds,
        "routing": {"domainStrategy": "AsIs", "rules": rules},
    }
    return json.dumps(cfg, indent=2, ensure_ascii=False)


def build_singbox_config(profile: dict, settings: dict) -> str:
    p = profile.get("params", {})
    proto = profile.get("protocol", "vless")
    mode = settings.get("vpn_mode", "tun")
    policy = settings.get("per_app_policy", "only_selected")
    apps = [a.strip() for a in settings.get("selected_apps", []) if a.strip()]

    inbounds = [
        {"type": "mixed", "tag": "mixed-in", "listen": "127.0.0.1", "listen_port": 10809, "sniff": settings.get("sniffing", True)}
    ]
    if mode == "tun" or (mode == "proxy_apps" and apps):
        inbounds.insert(0, {
            "type": "tun",
            "tag": "tun-in",
            "inet4_address": "172.19.0.1/30",
            "mtu": 1500,
            "auto_route": True,
            "strict_route": True,
            "sniff": settings.get("sniffing", True),
        })

    proxy = {"tag": "proxy", "server": profile["address"], "server_port": profile["port"]}
    if proto == "vless":
        proxy["type"] = "vless"
        proxy["uuid"] = p.get("user", "")
        if p.get("flow"):
            proxy["flow"] = p["flow"]
        if p.get("security") in ("reality", "tls"):
            tls = {
                "enabled": True,
                "server_name": p.get("sni", profile["address"]),
                "utls": {"enabled": True, "fingerprint": p.get("fp", "chrome")},
            }
            if p.get("security") == "reality":
                tls["reality"] = {"enabled": True, "public_key": p.get("pbk", ""), "short_id": p.get("sid", "")}
            proxy["tls"] = tls
    elif proto == "hysteria2":
        proxy["type"] = "hysteria2"
        proxy["password"] = p.get("user", "")
        proxy["tls"] = {"enabled": True, "server_name": p.get("sni", profile["address"]), "insecure": p.get("insecure") == "1"}
    elif proto == "trojan":
        proxy["type"] = "trojan"
        proxy["password"] = p.get("user", "")
        proxy["tls"] = {"enabled": True, "server_name": p.get("sni", profile["address"])}
    elif proto == "wireguard":
        proxy["type"] = "wireguard"
        proxy["private_key"] = p.get("user", "")
        proxy["peer_public_key"] = p.get("publickey", "")
        proxy["local_address"] = [p.get("address", "10.0.0.2/32")]
    else:
        proxy["type"] = "shadowsocks"
        proxy["method"] = p.get("method", "aes-256-gcm")
        proxy["password"] = p.get("password", "")

    route_rules = [{"protocol": "dns", "outbound": "dns-out"}]
    final_out = "proxy"
    if mode == "proxy_apps" and apps:
        if policy == "only_selected":
            route_rules.append({"process_name": apps, "outbound": "proxy"})
            final_out = "direct"
        else:
            route_rules.append({"process_name": apps, "outbound": "direct"})
            final_out = "proxy"

    cfg = {
        "log": {"level": "warn", "timestamp": True},
        "dns": {"servers": [{"tag": "remote", "address": settings.get("remote_dns", "1.1.1.1"), "detour": "proxy"}]},
        "inbounds": inbounds,
        "outbounds": [
            proxy,
            {"type": "direct", "tag": "direct"},
            {"type": "block", "tag": "block"},
            {"type": "dns", "tag": "dns-out"},
        ],
        "route": {"auto_detect_interface": True, "final": final_out, "rules": route_rules},
    }
    return json.dumps(cfg, indent=2, ensure_ascii=False)
