$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BuildDir = Join-Path $ScriptDir "build"
$OutputExe = Join-Path $ScriptDir "..\wasapi-loopback-capture.exe"

cmake -S $ScriptDir -B $BuildDir -A x64
cmake --build $BuildDir --config Release

$BuiltExe = Join-Path $BuildDir "Release\wasapi-loopback-capture.exe"
if (!(Test-Path $BuiltExe)) {
    throw "Build succeeded but executable not found: $BuiltExe"
}

Get-Process -Name "wasapi-loopback-capture" -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Host "Stopping running helper process PID=$($_.Id)"
    Stop-Process -Id $_.Id -Force
}
Start-Sleep -Milliseconds 500

try {
    Copy-Item $BuiltExe $OutputExe -Force
} catch {
    throw "Failed to copy $BuiltExe to $OutputExe. Please stop the Java app and any running wasapi-loopback-capture.exe process, then run this script again. Original error: $($_.Exception.Message)"
}
Write-Host "Built and copied to $OutputExe"
