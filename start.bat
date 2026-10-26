@echo off
setlocal
cd /d "%~dp0"
if not exist .env (
  copy .env.example .env >nul
  echo Created .env. Fill Google settings following README.md, then run again.
  pause
  exit /b 1
)
where java >nul 2>nul
if errorlevel 1 (
  echo Java 17 or newer is required. See README.md.
  pause
  exit /b 1
)
if exist seminar-app.jar (
  java -jar seminar-app.jar
) else (
  if exist target\seminar-app.jar (
    java -jar target\seminar-app.jar
  ) else (
    echo Build first: mvnw.cmd clean package
  )
)
pause
