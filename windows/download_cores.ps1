# Загрузка Xray-core (с wintun.dll, geoip.dat, geosite.dat) и sing-box для Windows x64
$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path "cores" | Out-Null

Write-Host "[1/2] Загрузка Xray-core..." -ForegroundColor Magenta
$xrayRel = Invoke-RestMethod "https://api.github.com/repos/XTLS/Xray-core/releases/latest"
$xrayAsset = $xrayRel.assets | Where-Object { $_.name -eq "Xray-windows-64.zip" } | Select-Object -First 1
if ($xrayAsset) {
    Invoke-WebRequest -Uri $xrayAsset.browser_download_url -OutFile "cores/xray.zip"
    Expand-Archive -Path "cores/xray.zip" -DestinationPath "cores/xray_tmp" -Force
    foreach ($f in @("xray.exe", "wintun.dll", "geoip.dat", "geosite.dat")) {
        if (Test-Path "cores/xray_tmp/$f") { Copy-Item "cores/xray_tmp/$f" "cores/$f" -Force }
    }
    Remove-Item "cores/xray.zip", "cores/xray_tmp" -Recurse -Force
    Write-Host "OK: cores/xray.exe + wintun.dll готовы" -ForegroundColor Green
}

Write-Host "[2/2] Загрузка sing-box..." -ForegroundColor Magenta
$sbRel = Invoke-RestMethod "https://api.github.com/repos/SagerNet/sing-box/releases/latest"
$sbAsset = $sbRel.assets | Where-Object { $_.name -like "*windows-amd64.zip" } | Select-Object -First 1
if ($sbAsset) {
    Invoke-WebRequest -Uri $sbAsset.browser_download_url -OutFile "cores/singbox.zip"
    Expand-Archive -Path "cores/singbox.zip" -DestinationPath "cores/sb_tmp" -Force
    $sbExe = Get-ChildItem -Path "cores/sb_tmp" -Filter "sing-box.exe" -Recurse | Select-Object -First 1
    if ($sbExe) { Copy-Item $sbExe.FullName "cores/sing-box.exe" -Force }
    Remove-Item "cores/singbox.zip", "cores/sb_tmp" -Recurse -Force
    Write-Host "OK: cores/sing-box.exe готов" -ForegroundColor Green
}
