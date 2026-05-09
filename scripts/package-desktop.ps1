$ErrorActionPreference = "Stop"

$RootDir = Resolve-Path (Join-Path $PSScriptRoot "..")
$AppName = "nod"
$AppDisplayName = "nod"
$AppVersion = "1.0.0"
$MainJar = "remote-interview-assistant-1.0.0.jar"
$MainClass = "com.interviewassistant.Main"
$InputDir = Join-Path $RootDir "target\installer\input"
$OutputDir = Join-Path $RootDir "release\desktop"

Set-Location $RootDir

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
  throw "jpackage was not found. Please install JDK 21 and make sure JAVA_HOME\bin is in PATH."
}

if (-not (Get-Command candle.exe -ErrorAction SilentlyContinue) -or -not (Get-Command light.exe -ErrorAction SilentlyContinue)) {
  throw "WiX Toolset was not found. Install WiX Toolset v3.x, then add its bin directory, such as C:\Program Files (x86)\WiX Toolset v3.14\bin, to PATH. jpackage needs candle.exe and light.exe to generate MSI installers."
}

mvn -q -DskipTests package
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$WindowsNativeProject = Join-Path $RootDir "native\windows\wasapi-loopback-capture"
$WindowsNativeExe = Join-Path $WindowsNativeProject "build\Release\wasapi-loopback-capture.exe"
if (Test-Path (Join-Path $WindowsNativeProject "build.ps1")) {
  Push-Location $WindowsNativeProject
  .\build.ps1
  Pop-Location
}

New-Item -ItemType Directory -Force -Path (Join-Path $InputDir "native\windows") | Out-Null
if (Test-Path $WindowsNativeExe) {
  Copy-Item -Force $WindowsNativeExe (Join-Path $InputDir "native\windows\wasapi-loopback-capture.exe")
}

jpackage `
  --type msi `
  --name $AppName `
  --app-version $AppVersion `
  --vendor "nod" `
  --description $AppDisplayName `
  --input $InputDir `
  --main-jar $MainJar `
  --main-class $MainClass `
  --dest $OutputDir `
  --win-menu `
  --win-shortcut `
  --java-options "-Dfile.encoding=UTF-8"

$BuiltMsi = Get-ChildItem -Path $OutputDir -Filter "*.msi" | Select-Object -First 1
if ($BuiltMsi) {
  Copy-Item -Force $BuiltMsi.FullName (Join-Path $RootDir "web\public\downloads\nod-windows-latest.msi")
}

Write-Host "Installer generated at: $OutputDir"
