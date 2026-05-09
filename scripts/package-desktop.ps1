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
  throw "jpackage 未找到。请安装 JDK 21，并确认 JAVA_HOME\bin 在 PATH 中。"
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

Write-Host "安装包已生成到：$OutputDir"
