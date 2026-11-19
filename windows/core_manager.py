"""Process manager for xray.exe / sing-box.exe and Windows System Proxy."""
import os
import socket
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path


def _resource_dir() -> Path:
    if hasattr(sys, "_MEIPASS"):
        return Path(sys._MEIPASS)
    return Path(__file__).resolve().parent


def set_windows_proxy(enable: bool, proxy_addr: str = "127.0.0.1:10809") -> None:
    if sys.platform != "win32":
        return
    try:
        import ctypes
        import winreg

        key_path = r"Software\Microsoft\Windows\CurrentVersion\Internet Settings"
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, key_path, 0, winreg.KEY_SET_VALUE) as key:
            winreg.SetValueEx(key, "ProxyEnable", 0, winreg.REG_DWORD, 1 if enable else 0)
            if enable:
                winreg.SetValueEx(key, "ProxyServer", 0, winreg.REG_SZ, proxy_addr)
                winreg.SetValueEx(key, "ProxyOverride", 0, winreg.REG_SZ, "localhost;127.*;10.*;192.168.*;<local>")
        ctypes.windll.Wininet.InternetSetOptionW(0, 39, 0, 0)
        ctypes.windll.Wininet.InternetSetOptionW(0, 37, 0, 0)
    except Exception:
        pass


def measure_ping(host: str, port: int, timeout: float = 2.0) -> int | None:
    try:
        t0 = time.perf_counter()
        with socket.create_connection((host, port), timeout=timeout):
            return int((time.perf_counter() - t0) * 1000)
    except Exception:
        return None


class CoreProcessManager:
    def __init__(self, on_log):
        self.on_log = on_log
        self.proc: subprocess.Popen | None = None
        self.cfg_file: Path | None = None

    def find_binary(self, core: str) -> Path | None:
        exe = "xray.exe" if core == "xray" else "sing-box.exe"
        candidates = [_resource_dir() / "cores" / exe, Path.cwd() / "cores" / exe]
        for c in candidates:
            if c.exists():
                return c
        return None

    def start(self, core: str, config_json: str, use_system_proxy: bool = True) -> bool:
        self.stop()
        binary = self.find_binary(core)
        if not binary:
            self.on_log(f"[ERROR] Не найден файл cores/{'xray.exe' if core == 'xray' else 'sing-box.exe'}. Запустите download_cores.ps1")
            return False

        fd, path = tempfile.mkstemp(prefix=f"albinvpn_{core}_", suffix=".json")
        os.close(fd)
        self.cfg_file = Path(path)
        self.cfg_file.write_text(config_json, encoding="utf-8")

        cmd = [str(binary), "run", "-c", str(self.cfg_file)]
        creation_flags = subprocess.CREATE_NO_WINDOW if sys.platform == "win32" else 0
        try:
            self.proc = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                encoding="utf-8",
                errors="replace",
                creationflags=creation_flags,
            )
            threading.Thread(target=self._read_logs, daemon=True).start()
            if use_system_proxy:
                set_windows_proxy(True, "127.0.0.1:10809")
                self.on_log("[Proxy] Системный прокси Windows включён (127.0.0.1:10809).")
            self.on_log(f"[{core}] Ядро запущено: {binary.name}")
            return True
        except Exception as exc:
            self.on_log(f"[ERROR] Ошибка запуска {core}: {exc}")
            return False

    def _read_logs(self) -> None:
        if not self.proc or not self.proc.stdout:
            return
        for line in self.proc.stdout:
            self.on_log(line.rstrip())

    def stop(self) -> None:
        set_windows_proxy(False)
        if self.proc and self.proc.poll() is None:
            self.proc.terminate()
            try:
                self.proc.wait(timeout=3)
            except subprocess.TimeoutExpired:
                self.proc.kill()
        self.proc = None
        if self.cfg_file and self.cfg_file.exists():
            try:
                self.cfg_file.unlink()
            except Exception:
                pass
        self.cfg_file = None
