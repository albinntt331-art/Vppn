"""URI & Subscription parser for VLESS, VMess, Trojan, Shadowsocks, Hysteria2, WireGuard."""
import base64
import json
import uuid
from urllib.parse import parse_qs, unquote, urlparse
import requests


def _b64_decode(data: str) -> str:
    s = data.strip().replace("-", "+").replace("_", "/")
    pad = (-len(s)) % 4
    return base64.b64decode(s + ("=" * pad)).decode("utf-8", errors="ignore")


def parse_batch(raw_text: str, sub_id: str | None = None) -> list[dict]:
    text = raw_text.strip()
    schemes = ("vless://", "vmess://", "trojan://", "ss://", "hysteria2://", "hy2://", "wireguard://")
    if not text.lower().startswith(schemes):
        try:
            text = _b64_decode(text)
        except Exception:
            pass
    profiles = []
    for line in text.splitlines():
        line = line.strip()
        if "://" in line:
            p = parse_single(line, sub_id=sub_id)
            if p:
                profiles.append(p)
    return profiles


def parse_single(uri: str, sub_id: str | None = None) -> dict | None:
    low = uri.lower()
    if low.startswith("vmess://"):
        try:
            obj = json.loads(_b64_decode(uri[8:]))
            return {
                "id": str(uuid.uuid4()),
                "name": obj.get("ps") or f"vmess-{obj.get('add', '')}",
                "protocol": "vmess",
                "address": obj.get("add", ""),
                "port": int(obj.get("port", 443)),
                "raw_uri": uri,
                "subscription_id": sub_id,
                "ping_ms": None,
                "params": {k: str(v) for k, v in obj.items()},
            }
        except Exception:
            return None

    for prefix, proto in [
        ("vless://", "vless"),
        ("trojan://", "trojan"),
        ("hysteria2://", "hysteria2"),
        ("hy2://", "hysteria2"),
        ("wireguard://", "wireguard"),
        ("ss://", "ss"),
    ]:
        if low.startswith(prefix):
            try:
                parsed = urlparse(uri)
                host = parsed.hostname or ""
                port = parsed.port or 443
                name = unquote(parsed.fragment) if parsed.fragment else f"{proto}-{host}"
                params = {k: v[0] for k, v in parse_qs(parsed.query).items()}
                if parsed.username:
                    params["user"] = unquote(parsed.username)
                if proto == "ss" and parsed.username and ":" not in parsed.username:
                    try:
                        decoded = _b64_decode(parsed.username)
                        if ":" in decoded:
                            m, pw = decoded.split(":", 1)
                            params["method"] = m
                            params["password"] = pw
                    except Exception:
                        pass
                return {
                    "id": str(uuid.uuid4()),
                    "name": name,
                    "protocol": proto,
                    "address": host,
                    "port": port,
                    "raw_uri": uri,
                    "subscription_id": sub_id,
                    "ping_ms": None,
                    "params": params,
                }
            except Exception:
                return None
    return None


def fetch_subscription(url: str, sub_id: str) -> tuple[list[dict], dict]:
    resp = requests.get(
        url,
        headers={"User-Agent": "Albin-vpn/1.0 (Happ; Hiddify)"},
        timeout=15,
    )
    resp.raise_for_status()
    info = {}
    hdr = resp.headers.get("subscription-userinfo", "")
    for part in hdr.split(";"):
        if "=" in part:
            k, v = part.strip().split("=", 1)
            try:
                info[k.lower()] = int(v)
            except ValueError:
                pass
    return parse_batch(resp.text, sub_id=sub_id), info
