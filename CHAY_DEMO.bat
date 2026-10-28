@echo off
setlocal DisableDelayedExpansion
chcp 65001 >nul
cd /d "%~dp0"
if not exist "%~dp0scripts\Start-Demo.ps1" (
  echo Khong tim thay scripts\Start-Demo.ps1. Hay giai nen day du goi cap nhat vao thu muc du an.
  pause
  exit /b 1
)
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -ExecutionPolicy Bypass -STA -File "%~dp0scripts\Start-Demo.ps1" %*
set "SEMINAR_DEMO_EXIT=%ERRORLEVEL%"
if not "%SEMINAR_DEMO_EXIT%"=="0" (
  echo.
  echo Chua khoi dong duoc ung dung. Xem thong bao o tren de xu ly.
  pause
)
exit /b %SEMINAR_DEMO_EXIT%
