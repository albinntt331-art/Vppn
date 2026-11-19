@echo off
chcp 65001 >nul
echo === Сборка Albin-vpn.exe ===
python -m venv .venv
call .venv\Scripts\activate.bat
pip install --upgrade pip
pip install -r requirements.txt
powershell -ExecutionPolicy Bypass -File download_cores.ps1
pyinstaller --noconfirm --onefile --windowed --name "Albin-vpn" ^
  --icon "albin.ico" ^
  --add-data "albin_icon.png;." ^
  --add-data "cores;cores" ^
  main.py
echo.
echo Готово! Исполняемый файл находится в: dist\Albin-vpn.exe
pause
