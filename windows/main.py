"""Entry point for Albin-vpn Desktop."""
import sys
from PySide6.QtWidgets import QApplication
from ui_main import AlbinVpnWindow


def main() -> None:
    app = QApplication(sys.argv)
    app.setApplicationName("Albin-vpn")
    win = AlbinVpnWindow()
    win.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
