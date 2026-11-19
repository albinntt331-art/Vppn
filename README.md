# Albin-vpn (Android APK + Windows EXE)

Единый репозиторий для автоматической сборки **Albin-vpn** сразу под Android (`.apk`) и Windows (`.exe`).

## Структура репозитория
- `.github/workflows/build-all.yml` — единый workflow, собирающий **APK** и **EXE** параллельно
- `android/` — приложение для Google Pixel (Kotlin + Jetpack Compose + Material 3 Expressive)
- `windows/` — десктопное приложение для Windows (Python + PySide6 + Xray-core / sing-box)

## Как собрать за 1 шаг
1. Загрузите все файлы из этого архива (`.github`, `android`, `windows`, `README.md`) в корень нового приватного репозитория на GitHub.
2. Откройте вкладку **Actions -> Build Albin-vpn (APK + EXE)**.
3. После завершения сборки скачайте оба готовых файла в разделе **Artifacts**:
   - **Albin-vpn-APK** (для Android / Pixel)
   - **Albin-vpn-Windows-EXE** (для Windows)
