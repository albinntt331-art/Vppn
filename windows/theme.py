"""Material 3 Expressive / Google Pixel theme engine for Albin-vpn Desktop (Purple default)."""

ACCENT_PALETTES = {
    "purple": {
        "name": "Фиолетовый (База)",
        "swatch": "#7C4DFF",
        "dark": {
            "primary": "#D5B8FF",
            "glow": "#9D6BFF",
            "on_primary": "#2A0A66",
            "primary_container": "#4B2A94",
            "bg": "#0D0A14",
            "surface": "#161224",
            "surface_high": "#221C35",
            "text": "#EFEAF8",
            "text_muted": "#B8AECB",
            "border": "#362C52",
        },
        "light": {
            "primary": "#6B3FD1",
            "glow": "#8B5CF6",
            "on_primary": "#FFFFFF",
            "primary_container": "#E9DDFF",
            "bg": "#FCF8FF",
            "surface": "#F2EBFA",
            "surface_high": "#E8DEF7",
            "text": "#1C1628",
            "text_muted": "#514766",
            "border": "#D4C6E8",
        },
    },
    "blue": {
        "name": "Синий Pixel",
        "swatch": "#2563EB",
        "dark": {
            "primary": "#9ECAFF",
            "glow": "#3B82F6",
            "on_primary": "#003258",
            "primary_container": "#00497D",
            "bg": "#0B0F17",
            "surface": "#141A26",
            "surface_high": "#1E2738",
            "text": "#E6ECF7",
            "text_muted": "#A9B6CC",
            "border": "#2E3A52",
        },
        "light": {
            "primary": "#0061A4",
            "glow": "#3B82F6",
            "on_primary": "#FFFFFF",
            "primary_container": "#D1E4FF",
            "bg": "#F8FAFF",
            "surface": "#ECF1FA",
            "surface_high": "#E0E8F5",
            "text": "#161B24",
            "text_muted": "#455066",
            "border": "#C5D1E5",
        },
    },
    "emerald": {
        "name": "Изумрудный",
        "swatch": "#0D9488",
        "dark": {
            "primary": "#5CE6D3",
            "glow": "#14B8A6",
            "on_primary": "#003731",
            "primary_container": "#005048",
            "bg": "#091211",
            "surface": "#111E1D",
            "surface_high": "#1A2C2A",
            "text": "#E1F2EF",
            "text_muted": "#9EC2BC",
            "border": "#28423F",
        },
        "light": {
            "primary": "#006A60",
            "glow": "#14B8A6",
            "on_primary": "#FFFFFF",
            "primary_container": "#74F8E5",
            "bg": "#F4FCFA",
            "surface": "#E6F3F0",
            "surface_high": "#D8EBE6",
            "text": "#13201E",
            "text_muted": "#3F5451",
            "border": "#B8D4CE",
        },
    },
    "rose": {
        "name": "Розовый",
        "swatch": "#E11D48",
        "dark": {
            "primary": "#FFB2BD",
            "glow": "#F43F5E",
            "on_primary": "#5F1124",
            "primary_container": "#83223A",
            "bg": "#150C10",
            "surface": "#21141A",
            "surface_high": "#2F1D25",
            "text": "#F5E4E8",
            "text_muted": "#CAAEB5",
            "border": "#4A2D39",
        },
        "light": {
            "primary": "#B53B55",
            "glow": "#F43F5E",
            "on_primary": "#FFFFFF",
            "primary_container": "#FFD9DE",
            "bg": "#FFF8F8",
            "surface": "#FCEBEF",
            "surface_high": "#F5DCE3",
            "text": "#26161B",
            "text_muted": "#5C434B",
            "border": "#E3C1CA",
        },
    },
    "amber": {
        "name": "Янтарный",
        "swatch": "#D97706",
        "dark": {
            "primary": "#FFC064",
            "glow": "#F59E0B",
            "on_primary": "#472A00",
            "primary_container": "#663E00",
            "bg": "#140F09",
            "surface": "#20180F",
            "surface_high": "#2E2316",
            "text": "#F3E8DC",
            "text_muted": "#CBB9A5",
            "border": "#4A3926",
        },
        "light": {
            "primary": "#965A00",
            "glow": "#F59E0B",
            "on_primary": "#FFFFFF",
            "primary_container": "#FFDDB3",
            "bg": "#FFF9F4",
            "surface": "#F7ECE0",
            "surface_high": "#EFE0CE",
            "text": "#241B12",
            "text_muted": "#594A3B",
            "border": "#DFC9B4",
        },
    },
}


def get_palette(accent_key: str = "purple", dark: bool = True) -> dict:
    return ACCENT_PALETTES.get(accent_key, ACCENT_PALETTES["purple"])["dark" if dark else "light"]


def build_stylesheet(accent_key: str = "purple", dark: bool = True) -> str:
    pal = get_palette(accent_key, dark)
    return f"""
    QMainWindow, QWidget {{
        background-color: {pal['bg']};
        color: {pal['text']};
        font-family: "Segoe UI Variable", "Segoe UI", "Inter", sans-serif;
        font-size: 13px;
    }}
    QFrame#NavRail {{
        background-color: {pal['surface']};
        border-right: 1px solid {pal['border']};
    }}
    QPushButton.NavBtn {{
        background-color: transparent;
        color: {pal['text_muted']};
        border: 1px solid transparent;
        border-radius: 18px;
        padding: 11px 16px;
        text-align: left;
        font-weight: 600;
    }}
    QPushButton.NavBtn:hover {{
        background-color: {pal['surface_high']};
        color: {pal['text']};
    }}
    QPushButton.NavBtn[active="true"] {{
        background-color: {pal['primary_container']};
        border: 1px solid {pal['primary']};
        color: {pal['text']};
    }}
    QFrame.Card {{
        background-color: {pal['surface']};
        border: 1px solid {pal['border']};
        border-radius: 24px;
    }}
    QPushButton.PrimaryBtn {{
        background-color: {pal['primary']};
        color: {pal['on_primary']};
        border: none;
        border-radius: 16px;
        padding: 9px 18px;
        font-weight: 700;
    }}
    QPushButton.PrimaryBtn:hover {{
        background-color: {pal['glow']};
        color: #FFFFFF;
    }}
    QPushButton.TonalBtn {{
        background-color: {pal['surface_high']};
        color: {pal['text']};
        border: 1px solid {pal['border']};
        border-radius: 16px;
        padding: 8px 15px;
        font-weight: 600;
    }}
    QPushButton.TonalBtn:hover {{
        border: 1px solid {pal['primary']};
    }}
    QLineEdit, QComboBox, QPlainTextEdit, QListWidget {{
        background-color: {pal['surface_high']};
        color: {pal['text']};
        border: 1px solid {pal['border']};
        border-radius: 16px;
        padding: 9px 12px;
    }}
    QListWidget::item {{
        padding: 12px;
        border-radius: 14px;
        margin: 3px 0px;
    }}
    QListWidget::item:selected {{
        background-color: {pal['primary_container']};
        border: 1px solid {pal['primary']};
        color: {pal['text']};
    }}
    QCheckBox {{
        spacing: 9px;
        font-weight: 500;
    }}
    QLabel.Muted {{
        color: {pal['text_muted']};
    }}
    """
