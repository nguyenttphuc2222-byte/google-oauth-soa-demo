#Requires -Version 5.1
[CmdletBinding()]
param([switch]$Configure, [switch]$NoBrowser)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)
try {
    if ($env:OS -ne 'Windows_NT') { throw 'CHAY_DEMO.bat dùng cho Windows. macOS/Linux tiếp tục chạy docker compose theo README.' }
    . (Join-Path $PSScriptRoot 'DemoConfig.ps1')
    . (Join-Path $PSScriptRoot 'DemoRuntime.ps1')
    . (Join-Path $PSScriptRoot 'DemoSetup.ps1')
    $projectDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    Start-SeminarDemo -ProjectDirectory $projectDirectory -Configure:$Configure -NoBrowser:$NoBrowser
    exit 0
} catch {
    Write-Host ''
    Write-Host ('Lỗi: ' + $_.Exception.Message) -ForegroundColor Red
    exit 1
}
