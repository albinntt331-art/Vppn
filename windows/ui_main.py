"""Albin-vpn v3 Desktop UI — Google Pixel Expressive, Live Ping, Running Apps Scanner, Exit IP Check."""
import json
import math
import subprocess
import sys
import threading
import time
import urllib.request
from pathlib import Path
from PySide6.QtCore import Qt, QTimer, Signal, QObject, QPointF
from PySide6.QtGui import QColor, QConicalGradient, QFont, QIcon, QLinearGradient, QPainter, QPainterPath, QPen, QRadialGradient
from PySide6.QtWidgets import (
    QApplication, QCheckBox, QComboBox, QFileDialog, QFrame, QHBoxLayout, QInputDialog,
    QLabel, QLineEdit, QListWidget, QListWidgetItem, QMainWindow, QMessageBox,
    QPlainTextEdit, QPushButton, QStackedWidget, QVBoxLayout, QWidget,
)
from config_builder import build_singbox_config, build_xray_config, choose_core
from core_manager import CoreProcessManager, measure_ping
from theme import ACCENT_PALETTES, build_stylesheet, get_palette
from uri_parser import fetch_subscription, parse_batch

STATE_FILE = Path.home() / ".albinvpn_desktop.json"
AUTO_ID = "__AUTO_BEST__"

I18N = {
    "ru": {
        "nav": ["Главная", "Серверы", "Программы", "Журнал", "Настройки"],
        "mode_tun": "TUN (Строгий — весь трафик)",
        "mode_proxy": "Proxy Apps (По программам)",
        "connect": "Подключить", "connected": "Защищено", "disconnected": "Отключено",
        "auto_title": "★ Авто (Лучший сервер)",
        "add_sub": "+ Подписка", "add_key": "+ Ключ", "ping": "Пинг всех", "prio": "★ Приоритет", "del": "Удалить",
        "apps_title": "Маршрутизация по программам (.exe)",
        "add_exe": "+ Добавить .exe", "scan_exe": "Сканировать запущенные",
        "lang": "Язык интерфейса", "dark": "Тёмная тема", "accent": "Оттенок системы (Фиолетовый по базе)",
        "live": "Активность и пинг (Live)", "check_ip": "Проверить IP",
    },
    "en": {
        "nav": ["Home", "Servers", "Apps", "Logs", "Settings"],
        "mode_tun": "TUN (Strict — all traffic)",
        "mode_proxy": "Proxy Apps (Per-app routing)",
        "connect": "Connect", "connected": "Protected", "disconnected": "Disconnected",
        "auto_title": "★ Auto (Best Server)",
        "add_sub": "+ Subscription", "add_key": "+ Key", "ping": "Ping all", "prio": "★ Priority", "del": "Delete",
        "apps_title": "Per-Application Routing (.exe)",
        "add_exe": "+ Add .exe", "scan_exe": "Scan running apps",
        "lang": "App Language", "dark": "Dark theme", "accent": "System accent (Purple default)",
        "live": "Tunnel Activity & Ping (Live)", "check_ip": "Check Exit IP",
    },
}


class GlowingPowerButton(QWidget):
    clicked = Signal()

    def __init__(self):
        super().__init__()
        self.setFixedSize(250, 250)
        self.setCursor(Qt.PointingHandCursor)
        self.connected = False
        self.phase = 0.0
        self.angle1 = 0.0
        self.angle2 = 360.0
        self.ripple = 0.0
        self.label_connect = "Подключить"
        self.label_connected = "Защищено"
        self.pal = get_palette("purple", True)
        self._anim = QTimer(self)
        self._anim.timeout.connect(self._step)
        self._anim.start(28)

    def set_theme(self, pal: dict) -> None:
        self.pal = pal; self.update()

    def set_labels(self, connect_txt: str, connected_txt: str) -> None:
        self.label_connect = connect_txt; self.label_connected = connected_txt; self.update()

    def set_connected(self, state: bool) -> None:
        self.connected = state; self.update()

    def _step(self) -> None:
        self.phase = (self.phase + 0.07) % (2 * math.pi)
        self.angle1 = (self.angle1 + (2.8 if self.connected else 0.8)) % 360.0
        self.angle2 = (self.angle2 - (1.7 if self.connected else 0.4)) % 360.0
        self.ripple = (self.ripple + 0.014) % 1.0
        self.update()

    def mousePressEvent(self, event) -> None:
        if event.button() == Qt.LeftButton:
            self.clicked.emit()

    def paintEvent(self, event) -> None:
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        cx, cy = self.width() / 2, self.height() / 2
        glow_col = QColor(self.pal["glow"])
        prim_col = QColor(self.pal["primary"])

        # Sonar ripple rings when connected
        if self.connected:
            for offset in (0.0, 0.5):
                rp = (self.ripple + offset) % 1.0
                r = 78 + rp * 42
                rc = QColor(prim_col); rc.setAlpha(int((1.0 - rp) * 120))
                p.setBrush(Qt.NoBrush); p.setPen(QPen(rc, 2.5))
                p.drawEllipse(QPointF(cx, cy), r, r)

        # Ambient radial aura
        aura_r = 115 + 8 * math.sin(self.phase)
        grad = QRadialGradient(cx, cy, aura_r)
        c1 = QColor(glow_col); c1.setAlpha(145 if self.connected else 50)
        c2 = QColor(glow_col); c2.setAlpha(0)
        grad.setColorAt(0.2, c1); grad.setColorAt(1.0, c2)
        p.setBrush(grad); p.setPen(Qt.NoPen)
        p.drawEllipse(QPointF(cx, cy), aura_r, aura_r)

        # Counter-rotating luminous orbital rings
        for ring_sz, ang, w in ((170, -self.angle1, 3.2), (186, -self.angle2, 1.8)):
            con = QConicalGradient(cx, cy, ang)
            cd = QColor(prim_col); cd.setAlpha(20)
            con.setColorAt(0.0, prim_col); con.setColorAt(0.5, cd); con.setColorAt(1.0, prim_col)
            p.setBrush(Qt.NoBrush); p.setPen(QPen(con, w if self.connected else w * 0.6))
            rad = 85 if self.connected else 48
            p.drawRoundedRect(int(cx - ring_sz / 2), int(cy - ring_sz / 2), ring_sz, ring_sz, rad, rad)

        # Expressive morphing core
        size = 152
        rx, ry = cx - size / 2, cy - size / 2
        core_rad = 76 if self.connected else 42
        if self.connected:
            cg = QLinearGradient(rx, ry, rx + size, ry + size)
            cg.setColorAt(0.0, prim_col); cg.setColorAt(1.0, glow_col)
            p.setBrush(cg); p.setPen(Qt.NoPen)
        else:
            p.setBrush(QColor(self.pal["surface_high"])); p.setPen(Qt.NoPen)
        p.drawRoundedRect(int(rx), int(ry), size, size, core_rad, core_rad)

        fg = QColor(self.pal["on_primary"]) if self.connected else prim_col
        p.setPen(QPen(fg, 4.0, Qt.SolidLine, Qt.RoundCap))
        p.drawArc(int(cx - 20), int(cy - 32), 40, 40, 50 * 16, 260 * 16)
        p.drawLine(int(cx), int(cy - 38), int(cx), int(cy - 14))
        p.setFont(QFont("Segoe UI", 12, QFont.Bold))
        p.drawText(int(rx), int(cy + 16), size, 32, Qt.AlignCenter, self.label_connected if self.connected else self.label_connect)


class TrafficSparklineWidget(QWidget):
    def __init__(self):
        super().__init__()
        self.setMinimumHeight(130)
        self.values = [0.2] * 28
        self.pal = get_palette("purple", True)

    def set_theme(self, pal: dict) -> None:
        self.pal = pal; self.update()

    def push_value(self, val: float) -> None:
        self.values = self.values[1:] + [max(0.1, val)]
        self.update()

    def paintEvent(self, event) -> None:
        p = QPainter(self); p.setRenderHint(QPainter.Antialiasing)
        w, h = self.width(), self.height()
        max_v = max(max(self.values), 1.0)
        step_x = w / max(len(self.values) - 1, 1)
        line, area = QPainterPath(), QPainterPath()
        for i, v in enumerate(self.values):
            x = i * step_x
            y = h - (v / max_v) * (h * 0.78) - 8
            if i == 0:
                line.moveTo(x, y); area.moveTo(x, h); area.lineTo(x, y)
            else:
                px = (i - 1) * step_x
                py = h - (self.values[i - 1] / max_v) * (h * 0.78) - 8
                cx = (px + x) / 2
                line.cubicTo(cx, py, cx, y, x, y); area.cubicTo(cx, py, cx, y, x, y)
        area.lineTo(w, h); area.closeSubpath()
        fg = QLinearGradient(0, 0, 0, h)
        c1 = QColor(self.pal["glow"]); c1.setAlpha(130)
        c2 = QColor(self.pal["glow"]); c2.setAlpha(0)
        fg.setColorAt(0.0, c1); fg.setColorAt(1.0, c2)
        p.fillPath(area, fg)
        p.setPen(QPen(QColor(self.pal["primary"]), 3.0, Qt.SolidLine, Qt.RoundCap))
        p.drawPath(line)


class LogBridge(QObject):
    new_line = Signal(str)
    ping_done = Signal(str, object)
    live_ping = Signal(object)
    ip_done = Signal(str)


class AlbinVpnWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Albin-vpn v3 — Google Pixel Style")
        _base = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent))
        _ico = _base / "albin_icon.png"
        if _ico.exists():
            self.setWindowIcon(QIcon(str(_ico)))
        self.resize(1060, 680)

        self.data = self._load_state()
        self.connected = False
        self.active_profile = None
        self.connect_ts = 0.0

        self.bridge = LogBridge()
        self.bridge.new_line.connect(self._append_log)
        self.bridge.ping_done.connect(self._on_ping_result)
        self.bridge.live_ping.connect(self._on_live_ping)
        self.bridge.ip_done.connect(lambda txt: self.ip_btn.setText(txt))
        self.core_mgr = CoreProcessManager(lambda msg: self.bridge.new_line.emit(msg))

        self._build_ui()
        self._apply_theme()
        self._apply_language()
        self._refresh_lists()

        self.timer = QTimer(self); self.timer.timeout.connect(self._tick_timer); self.timer.start(1000)
        self.ping_timer = QTimer(self); self.ping_timer.timeout.connect(self._run_live_ping); self.ping_timer.start(3500)

    def _tr(self, key: str):
        return I18N.get(self.data["settings"].get("language", "ru"), I18N["ru"])[key]

    def _load_state(self) -> dict:
        default = {
            "settings": {
                "language": "ru", "dark": True, "accent": "purple", "core": "auto",
                "vpn_mode": "tun", "per_app_policy": "only_selected",
                "selected_apps": ["chrome.exe", "Telegram.exe", "Discord.exe"],
                "sniffing": True, "fragment": False, "mux": False, "ipv6": False, "remote_dns": "1.1.1.1",
            },
            "profiles": [], "selected_id": AUTO_ID,
        }
        if STATE_FILE.exists():
            try:
                loaded = json.loads(STATE_FILE.read_text(encoding="utf-8"))
                default["settings"].update(loaded.get("settings", {}))
                default["profiles"] = loaded.get("profiles", [])
                default["selected_id"] = loaded.get("selected_id", AUTO_ID)
            except Exception:
                pass
        return default

    def _save_state(self) -> None:
        try:
            STATE_FILE.write_text(json.dumps(self.data, indent=2, ensure_ascii=False), encoding="utf-8")
        except Exception:
            pass

    def _apply_theme(self) -> None:
        s = self.data["settings"]
        acc, dark = s.get("accent", "purple"), s.get("dark", True)
        self.setStyleSheet(build_stylesheet(acc, dark))
        pal = get_palette(acc, dark)
        self.power_orb.set_theme(pal); self.sparkline.set_theme(pal)

    def _apply_language(self) -> None:
        for b, txt in zip(self.nav_buttons, self._tr("nav")):
            b.setText(txt)
        self.mode_combo.blockSignals(True)
        cur_m = self.mode_combo.currentIndex()
        self.mode_combo.clear(); self.mode_combo.addItems([self._tr("mode_tun"), self._tr("mode_proxy")])
        self.mode_combo.setCurrentIndex(cur_m if cur_m >= 0 else 0)
        self.mode_combo.blockSignals(False)
        self.power_orb.set_labels(self._tr("connect"), self._tr("connected"))
        if not self.connected:
            self.status_lbl.setText(self._tr("disconnected"))
        self.live_lbl.setText(self._tr("live"))
        self.ip_btn.setText(self._tr("check_ip"))
        self.btn_sub.setText(self._tr("add_sub")); self.btn_key.setText(self._tr("add_key"))
        self.btn_ping.setText(self._tr("ping")); self.btn_prio.setText(self._tr("prio")); self.btn_del.setText(self._tr("del"))
        self.apps_title_lbl.setText(self._tr("apps_title"))
        self.btn_add_exe.setText(self._tr("add_exe")); self.btn_scan_exe.setText(self._tr("scan_exe"))
        self.dark_cb.setText(self._tr("dark")); self.lang_lbl.setText(self._tr("lang")); self.accent_lbl.setText(self._tr("accent"))
        self._refresh_lists()

    def _build_ui(self) -> None:
        root = QWidget(); self.setCentralWidget(root)
        main_layout = QHBoxLayout(root); main_layout.setContentsMargins(0, 0, 0, 0); main_layout.setSpacing(0)

        nav = QFrame(); nav.setObjectName("NavRail"); nav.setFixedWidth(215)
        nav_l = QVBoxLayout(nav); nav_l.setContentsMargins(16, 24, 16, 24); nav_l.setSpacing(8)
        brand = QLabel("Albin-vpn"); brand.setStyleSheet("font-size: 22px; font-weight: 800; padding-left: 8px;")
        nav_l.addWidget(brand)
        sub = QLabel("Google Pixel • Dual Core"); sub.setProperty("class", "Muted"); sub.setStyleSheet("font-size: 11px; padding-left: 8px; margin-bottom: 16px;")
        nav_l.addWidget(sub)

        self.nav_buttons = []
        for idx in range(5):
            btn = QPushButton(""); btn.setProperty("class", "NavBtn"); btn.setProperty("active", idx == 0)
            btn.clicked.connect(lambda _, i=idx: self._switch_page(i))
            nav_l.addWidget(btn); self.nav_buttons.append(btn)
        nav_l.addStretch()

        self.stack = QStackedWidget()
        self.stack.addWidget(self._build_home_page())
        self.stack.addWidget(self._build_profiles_page())
        self.stack.addWidget(self._build_apps_page())
        self.stack.addWidget(self._build_logs_page())
        self.stack.addWidget(self._build_settings_page())
        main_layout.addWidget(nav); main_layout.addWidget(self.stack, 1)

    def _switch_page(self, idx: int) -> None:
        self.stack.setCurrentIndex(idx)
        for i, b in enumerate(self.nav_buttons):
            b.setProperty("active", i == idx); b.style().unpolish(b); b.style().polish(b)

    def _build_home_page(self) -> QWidget:
        page = QWidget(); l = QVBoxLayout(page); l.setContentsMargins(28, 24, 28, 24); l.setSpacing(18)
        top = QHBoxLayout()
        self.mode_combo = QComboBox(); self.mode_combo.addItems(["TUN", "Proxy Apps"])
        self.mode_combo.setCurrentIndex(0 if self.data["settings"].get("vpn_mode", "tun") == "tun" else 1)
        self.mode_combo.currentIndexChanged.connect(lambda i: self._set_setting("vpn_mode", "tun" if i == 0 else "proxy_apps"))
        self.live_ping_lbl = QLabel("● Ping: —")
        self.live_ping_lbl.setStyleSheet("font-weight: 700; padding: 6px 12px;")
        self.ip_btn = QPushButton("Check IP"); self.ip_btn.setProperty("class", "TonalBtn"); self.ip_btn.clicked.connect(self._check_ip)
        top.addWidget(self.mode_combo); top.addStretch(); top.addWidget(self.live_ping_lbl); top.addWidget(self.ip_btn)
        l.addLayout(top)

        mid = QHBoxLayout(); mid.setSpacing(18)
        hero = QFrame(); hero.setProperty("class", "Card")
        hl = QVBoxLayout(hero); hl.setAlignment(Qt.AlignCenter); hl.setContentsMargins(20, 22, 20, 22)
        self.power_orb = GlowingPowerButton(); self.power_orb.clicked.connect(self._toggle_vpn)
        hl.addWidget(self.power_orb, alignment=Qt.AlignCenter)
        self.status_lbl = QLabel(""); self.status_lbl.setStyleSheet("font-size: 16px; font-weight: 700;")
        hl.addWidget(self.status_lbl, alignment=Qt.AlignCenter)
        self.active_srv_lbl = QLabel(""); self.active_srv_lbl.setProperty("class", "Muted")
        hl.addWidget(self.active_srv_lbl, alignment=Qt.AlignCenter)
        mid.addWidget(hero, 1)

        chart = QFrame(); chart.setProperty("class", "Card")
        cl = QVBoxLayout(chart); cl.setContentsMargins(20, 20, 20, 20)
        self.live_lbl = QLabel(""); cl.addWidget(self.live_lbl)
        self.sparkline = TrafficSparklineWidget(); cl.addWidget(self.sparkline, 1)
        mid.addWidget(chart, 1)
        l.addLayout(mid, 1)
        return page

    def _build_profiles_page(self) -> QWidget:
        page = QWidget(); l = QVBoxLayout(page); l.setContentsMargins(24, 24, 24, 24); l.setSpacing(12)
        btns = QHBoxLayout()
        self.btn_sub = QPushButton(); self.btn_sub.setProperty("class", "PrimaryBtn"); self.btn_sub.clicked.connect(self._add_subscription)
        self.btn_key = QPushButton(); self.btn_key.setProperty("class", "TonalBtn"); self.btn_key.clicked.connect(self._add_key)
        self.btn_ping = QPushButton(); self.btn_ping.setProperty("class", "TonalBtn"); self.btn_ping.clicked.connect(self._ping_all)
        self.btn_prio = QPushButton(); self.btn_prio.setProperty("class", "TonalBtn"); self.btn_prio.clicked.connect(self._toggle_priority)
        self.btn_del = QPushButton(); self.btn_del.setProperty("class", "TonalBtn"); self.btn_del.clicked.connect(self._delete_selected_profile)
        for b in (self.btn_sub, self.btn_key, self.btn_ping, self.btn_prio, self.btn_del):
            btns.addWidget(b)
        btns.addStretch(); l.addLayout(btns)
        self.profiles_list = QListWidget(); self.profiles_list.currentRowChanged.connect(self._on_profile_selected)
        l.addWidget(self.profiles_list, 1)
        return page

    def _build_apps_page(self) -> QWidget:
        page = QWidget(); l = QVBoxLayout(page); l.setContentsMargins(24, 24, 24, 24); l.setSpacing(12)
        self.apps_title_lbl = QLabel(""); self.apps_title_lbl.setStyleSheet("font-size: 16px; font-weight: 700;")
        l.addWidget(self.apps_title_lbl)
        self.policy_combo = QComboBox(); self.policy_combo.addItems(["Only selected apps -> VPN", "Exclude selected apps"])
        self.policy_combo.setCurrentIndex(0 if self.data["settings"].get("per_app_policy") == "only_selected" else 1)
        self.policy_combo.currentIndexChanged.connect(lambda i: self._set_setting("per_app_policy", "only_selected" if i == 0 else "exclude_selected"))
        l.addWidget(self.policy_combo)

        row = QHBoxLayout()
        self.app_input = QLineEdit(); self.app_input.setPlaceholderText("chrome.exe, Telegram.exe, Discord.exe...")
        self.btn_add_exe = QPushButton("+ .exe"); self.btn_add_exe.setProperty("class", "PrimaryBtn"); self.btn_add_exe.clicked.connect(self._add_app_exe)
        self.btn_scan_exe = QPushButton("Scan"); self.btn_scan_exe.setProperty("class", "TonalBtn"); self.btn_scan_exe.clicked.connect(self._scan_running_apps)
        btn_browse = QPushButton("..."); btn_browse.setProperty("class", "TonalBtn"); btn_browse.clicked.connect(self._browse_exe)
        btn_rm = QPushButton("×"); btn_rm.setProperty("class", "TonalBtn"); btn_rm.clicked.connect(self._remove_app_exe)
        row.addWidget(self.app_input, 1); row.addWidget(self.btn_add_exe); row.addWidget(self.btn_scan_exe); row.addWidget(btn_browse); row.addWidget(btn_rm)
        l.addLayout(row)
        self.apps_list = QListWidget(); l.addWidget(self.apps_list, 1)
        return page

    def _build_logs_page(self) -> QWidget:
        page = QWidget(); l = QVBoxLayout(page); l.setContentsMargins(24, 24, 24, 24)
        self.log_box = QPlainTextEdit(); self.log_box.setReadOnly(True); self.log_box.setPlainText("Albin-vpn v3 ready.\n")
        l.addWidget(self.log_box)
        return page

    def _build_settings_page(self) -> QWidget:
        page = QWidget(); l = QVBoxLayout(page); l.setContentsMargins(24, 24, 24, 24); l.setSpacing(14)
        card = QFrame(); card.setProperty("class", "Card"); cl = QVBoxLayout(card); cl.setContentsMargins(18, 18, 18, 18); cl.setSpacing(10)
        lang_row = QHBoxLayout(); self.lang_lbl = QLabel(""); lang_row.addWidget(self.lang_lbl)
        self.lang_combo = QComboBox(); self.lang_combo.addItems(["Русский", "English"])
        self.lang_combo.setCurrentIndex(0 if self.data["settings"].get("language", "ru") == "ru" else 1)
        self.lang_combo.currentIndexChanged.connect(self._on_lang_changed)
        lang_row.addWidget(self.lang_combo); lang_row.addStretch(); cl.addLayout(lang_row)

        self.dark_cb = QCheckBox(""); self.dark_cb.setChecked(self.data["settings"].get("dark", True)); self.dark_cb.toggled.connect(self._on_theme_toggle)
        cl.addWidget(self.dark_cb)

        acc_row = QHBoxLayout(); self.accent_lbl = QLabel(""); acc_row.addWidget(self.accent_lbl)
        self.accent_combo = QComboBox(); self._accent_keys = list(ACCENT_PALETTES.keys())
        self.accent_combo.addItems([ACCENT_PALETTES[k]["name"] for k in self._accent_keys])
        cur = self.data["settings"].get("accent", "purple")
        if cur in self._accent_keys:
            self.accent_combo.setCurrentIndex(self._accent_keys.index(cur))
        self.accent_combo.currentIndexChanged.connect(self._on_accent_changed)
        acc_row.addWidget(self.accent_combo); acc_row.addStretch(); cl.addLayout(acc_row)
        l.addWidget(card)

        net = QFrame(); net.setProperty("class", "Card"); nl = QVBoxLayout(net); nl.setContentsMargins(18, 18, 18, 18); nl.setSpacing(8)
        for key, lbl in [("fragment", "TLS Fragment (DPI bypass)"), ("mux", "Multiplexing (Mux)"), ("sniffing", "Traffic Sniffing"), ("ipv6", "IPv6 Tunnel")]:
            cb = QCheckBox(lbl); cb.setChecked(self.data["settings"].get(key, False))
            cb.toggled.connect(lambda v, k=key: self._set_setting(k, v)); nl.addWidget(cb)
        l.addWidget(net); l.addStretch()
        return page

    def _set_setting(self, key: str, value) -> None:
        self.data["settings"][key] = value; self._save_state()

    def _on_lang_changed(self, idx: int) -> None:
        self._set_setting("language", "ru" if idx == 0 else "en"); self._apply_language()

    def _on_theme_toggle(self, dark: bool) -> None:
        self._set_setting("dark", dark); self._apply_theme()

    def _on_accent_changed(self, idx: int) -> None:
        self._set_setting("accent", self._accent_keys[idx]); self._apply_theme()

    def _refresh_lists(self) -> None:
        self.profiles_list.blockSignals(True); self.profiles_list.clear()
        self.profiles_list.addItem(QListWidgetItem(self._tr("auto_title")))
        sel_id = self.data.get("selected_id", AUTO_ID); sel_row = 0
        for i, p in enumerate(self.data["profiles"], start=1):
            star = "★ " if p.get("is_priority") else ""
            ping = f"  [{p['ping_ms']} ms]" if p.get("ping_ms") is not None else ""
            self.profiles_list.addItem(QListWidgetItem(f"{star}{p['name']} • {p['protocol'].upper()} ({p['address']}:{p['port']}){ping}"))
            if p["id"] == sel_id:
                sel_row = i
        self.profiles_list.setCurrentRow(sel_row); self.profiles_list.blockSignals(False)
        if sel_row == 0:
            self.active_srv_lbl.setText(self._tr("auto_title"))
        else:
            p = self.data["profiles"][sel_row - 1]; self.active_srv_lbl.setText(f"{p['name']} • {p['protocol'].upper()}")
        self.apps_list.clear()
        for app in self.data["settings"].get("selected_apps", []):
            self.apps_list.addItem(app)

    def _on_profile_selected(self, row: int) -> None:
        if row == 0:
            self.data["selected_id"] = AUTO_ID; self.active_srv_lbl.setText(self._tr("auto_title"))
        elif 1 <= row <= len(self.data["profiles"]):
            p = self.data["profiles"][row - 1]; self.data["selected_id"] = p["id"]; self.active_srv_lbl.setText(f"{p['name']} • {p['protocol'].upper()}")
        self._save_state()

    def _toggle_priority(self) -> None:
        row = self.profiles_list.currentRow()
        if 1 <= row <= len(self.data["profiles"]):
            p = self.data["profiles"][row - 1]; p["is_priority"] = not p.get("is_priority", False)
            self._save_state(); self._refresh_lists()

    def _scan_running_apps(self) -> None:
        try:
            out = subprocess.check_output(["tasklist", "/FO", "CSV", "/NH"], text=True, errors="ignore")
            exes = sorted({line.split(",")[0].strip('"') for line in out.splitlines() if ".exe" in line.lower()})
            choice, ok = QInputDialog.getItem(self, "Running Apps", "Select running .exe:", exes, 0, False)
            if ok and choice:
                self.app_input.setText(choice); self._add_app_exe()
        except Exception as exc:
            QMessageBox.warning(self, "Scan", str(exc))

    def _add_app_exe(self) -> None:
        name = self.app_input.text().strip()
        if name:
            apps = self.data["settings"].setdefault("selected_apps", [])
            if name not in apps:
                apps.append(name)
            self.app_input.clear(); self._save_state(); self._refresh_lists()

    def _browse_exe(self) -> None:
        fpath, _ = QFileDialog.getOpenFileName(self, "Select .exe", "", "Executables (*.exe)")
        if fpath:
            self.app_input.setText(Path(fpath).name); self._add_app_exe()

    def _remove_app_exe(self) -> None:
        row = self.apps_list.currentRow(); apps = self.data["settings"].get("selected_apps", [])
        if 0 <= row < len(apps):
            apps.pop(row); self._save_state(); self._refresh_lists()

    def _add_subscription(self) -> None:
        url, ok = QInputDialog.getText(self, self._tr("add_sub"), "URL (https://...):")
        if not ok or not url.strip():
            return
        try:
            profiles, _ = fetch_subscription(url.strip(), sub_id=url.strip())
            self.data["profiles"].extend(profiles); self._save_state(); self._refresh_lists()
        except Exception as exc:
            QMessageBox.warning(self, "Error", str(exc))

    def _add_key(self) -> None:
        clip = QApplication.clipboard().text()
        text, ok = QInputDialog.getMultiLineText(self, self._tr("add_key"), "vless://, vmess://, trojan://, ss://, hy2://:", clip)
        if ok and text.strip():
            parsed = parse_batch(text)
            if parsed:
                self.data["profiles"].extend(parsed); self._save_state(); self._refresh_lists()

    def _delete_selected_profile(self) -> None:
        row = self.profiles_list.currentRow()
        if 1 <= row <= len(self.data["profiles"]):
            self.data["profiles"].pop(row - 1); self.data["selected_id"] = AUTO_ID
            self._save_state(); self._refresh_lists()

    def _ping_all(self) -> None:
        def worker():
            for p in list(self.data["profiles"]):
                self.bridge.ping_done.emit(p["id"], measure_ping(p["address"], p["port"]))
        threading.Thread(target=worker, daemon=True).start()

    def _on_ping_result(self, pid: str, ms) -> None:
        for p in self.data["profiles"]:
            if p["id"] == pid:
                p["ping_ms"] = ms
        self._save_state(); self._refresh_lists()

    def _run_live_ping(self) -> None:
        if not self.connected or not self.active_profile:
            return
        prof = self.active_profile
        def worker():
            ms = measure_ping(prof["address"], prof["port"], timeout=2.0)
            self.bridge.live_ping.emit(ms)
        threading.Thread(target=worker, daemon=True).start()

    def _on_live_ping(self, ms) -> None:
        if ms is None:
            self.live_ping_lbl.setText("● Ping: timeout")
            self.live_ping_lbl.setStyleSheet("color: #FF5252; font-weight: 700; padding: 6px 12px;")
        else:
            col = "#69F0AE" if ms < 100 else ("#FFD54F" if ms < 220 else "#FF5252")
            self.live_ping_lbl.setText(f"● Ping: {ms} ms")
            self.live_ping_lbl.setStyleSheet(f"color: {col}; font-weight: 700; padding: 6px 12px;")

    def _check_ip(self) -> None:
        self.ip_btn.setText("...")
        def worker():
            try:
                with urllib.request.urlopen("https://ipwho.is/", timeout=4) as r:
                    obj = json.loads(r.read().decode("utf-8"))
                    self.bridge.ip_done.emit(f"{obj.get('ip', '?')} • {obj.get('country', '')}")
            except Exception:
                self.bridge.ip_done.emit("Error")
        threading.Thread(target=worker, daemon=True).start()

    def _pick_auto_best(self) -> dict | None:
        profs = self.data["profiles"]
        if not profs:
            return None
        prios = [p for p in profs if p.get("is_priority")]
        pool = prios if prios else profs
        best, best_ms = None, 10**9
        for p in pool:
            ms = measure_ping(p["address"], p["port"], timeout=1.5)
            p["ping_ms"] = ms
            if ms is not None and ms < best_ms:
                best, best_ms = p, ms
        return best or profs[0]

    def _toggle_vpn(self) -> None:
        if self.connected:
            self.core_mgr.stop(); self.connected = False; self.active_profile = None
            self.power_orb.set_connected(False); self.status_lbl.setText(self._tr("disconnected"))
            self.live_ping_lbl.setText("● Ping: —")
            return
        sel_id = self.data.get("selected_id", AUTO_ID)
        sel = self._pick_auto_best() if sel_id == AUTO_ID else next((p for p in self.data["profiles"] if p["id"] == sel_id), None)
        if not sel:
            QMessageBox.information(self, "Albin-vpn", "Add a subscription or key first."); return
        s = self.data["settings"]
        core = choose_core(sel, s.get("core", "auto"))
        cfg = build_singbox_config(sel, s) if core == "sing-box" else build_xray_config(sel, s)
        use_sys_proxy = s.get("vpn_mode") == "proxy_apps"
        if self.core_mgr.start(core, cfg, use_system_proxy=use_sys_proxy):
            self.connected = True; self.active_profile = sel; self.connect_ts = time.time()
            self.power_orb.set_connected(True)
            self.active_srv_lbl.setText(f"{'Авто → ' if sel_id == AUTO_ID else ''}{sel['name']}")
            self.status_lbl.setText(f"{self._tr('connected')} ({core})")
            self._run_live_ping()

    def _tick_timer(self) -> None:
        if self.connected:
            sec = int(time.time() - self.connect_ts)
            h, m, s = sec // 3600, (sec % 3600) // 60, sec % 60
            self.status_lbl.setText(f"{self._tr('connected')} • {h:02d}:{m:02d}:{s:02d}")
            self.sparkline.push_value(1.5 + abs(math.sin(sec * 0.7)) * 4.0)
        else:
            self.sparkline.push_value(0.15)

    def _append_log(self, msg: str) -> None:
        self.log_box.appendPlainText(msg)

    def closeEvent(self, event) -> None:
        self.core_mgr.stop(); super().closeEvent(event)
