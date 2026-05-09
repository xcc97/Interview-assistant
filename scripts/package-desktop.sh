#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_NAME="nod"
APP_DISPLAY_NAME="nod"
APP_VERSION="1.0.0"
MAIN_JAR="remote-interview-assistant-1.0.0.jar"
MAIN_CLASS="com.interviewassistant.Main"
INPUT_DIR="$ROOT_DIR/target/installer/input"
OUTPUT_DIR="$ROOT_DIR/release/desktop"
MAC_RESOURCE_DIR="$ROOT_DIR/target/installer/macos-resources"

cd "$ROOT_DIR"

if ! command -v jpackage >/dev/null 2>&1; then
  echo "jpackage 未找到。请使用 JDK 21，并确认 JAVA_HOME/bin 在 PATH 中。" >&2
  exit 1
fi

mvn -q -DskipTests package
mkdir -p "$OUTPUT_DIR"

if [[ -n "${REGENERATE_ICON:-}" && -f "$ROOT_DIR/packaging/macos/make-icon.sh" ]]; then
  bash "$ROOT_DIR/packaging/macos/make-icon.sh"
elif [[ ! -f "$ROOT_DIR/packaging/macos/nod.icns" ]]; then
  echo "缺少 packaging/macos/nod.icns，请先设置 REGENERATE_ICON=1 生成一次图标。" >&2
  exit 1
fi

if [[ -f "$ROOT_DIR/packaging/macos/nod.icns" ]]; then
  mkdir -p "$MAC_RESOURCE_DIR"
  cp "$ROOT_DIR/packaging/macos/nod.icns" "$MAC_RESOURCE_DIR/$APP_NAME.icns"
fi

if [[ -x "$ROOT_DIR/native/macos/system-audio-capture/build.sh" ]]; then
  if [[ -n "${SKIP_NATIVE_BUILD:-}" && -f "$ROOT_DIR/native/macos/system-audio-capture/system-audio-capture" ]]; then
    echo "跳过 macOS 音频采集组件构建，复用现有二进制。"
  else
    (cd "$ROOT_DIR/native/macos/system-audio-capture" && bash build.sh)
  fi
fi

mkdir -p "$INPUT_DIR/native/macos" "$INPUT_DIR/native/windows"
if [[ -f "$ROOT_DIR/native/macos/system-audio-capture/system-audio-capture" ]]; then
  mkdir -p "$INPUT_DIR/native/macos/system-audio-capture"
  cp "$ROOT_DIR/native/macos/system-audio-capture/system-audio-capture" "$INPUT_DIR/native/macos/system-audio-capture/system-audio-capture"
  chmod +x "$INPUT_DIR/native/macos/system-audio-capture/system-audio-capture"
fi
if [[ -f "$ROOT_DIR/native/windows/wasapi-loopback-capture.exe" ]]; then
  cp "$ROOT_DIR/native/windows/wasapi-loopback-capture.exe" "$INPUT_DIR/native/windows/wasapi-loopback-capture.exe"
fi

OS_NAME="$(uname -s)"
case "$OS_NAME" in
  Darwin)
    PACKAGE_TYPE="${MAC_PACKAGE_TYPE:-dmg}"
    ;;
  MINGW*|MSYS*|CYGWIN*)
    PACKAGE_TYPE="msi"
    ;;
  Linux)
    PACKAGE_TYPE="deb"
    ;;
  *)
    echo "不支持的系统：$OS_NAME" >&2
    exit 1
    ;;
esac

COMMON_ARGS=(
  --type "$PACKAGE_TYPE"
  --name "$APP_NAME"
  --app-version "$APP_VERSION"
  --vendor "nod"
  --description "$APP_DISPLAY_NAME"
  --input "$INPUT_DIR"
  --main-jar "$MAIN_JAR"
  --main-class "$MAIN_CLASS"
  --dest "$OUTPUT_DIR"
  --java-options "-Dfile.encoding=UTF-8"
)

if [[ -f "$ROOT_DIR/packaging/macos/nod.icns" ]]; then
  COMMON_ARGS+=(--icon "$ROOT_DIR/packaging/macos/nod.icns")
fi

if [[ "$OS_NAME" == "Darwin" ]]; then
  COMMON_ARGS+=(--mac-package-name "$APP_NAME")
  if [[ -d "$MAC_RESOURCE_DIR" ]]; then
    COMMON_ARGS+=(--resource-dir "$MAC_RESOURCE_DIR")
  fi
  COMMON_ARGS+=(--java-options "-Dapple.awt.application.name=$APP_NAME")
fi

if [[ "$OS_NAME" == "Darwin" && "$PACKAGE_TYPE" == "dmg" && -z "${NO_DMG_FALLBACK:-}" ]]; then
  if ! jpackage "${COMMON_ARGS[@]}"; then
    echo "DMG 生成失败，自动回退生成 app-image。" >&2
    PACKAGE_TYPE="app-image"
    COMMON_ARGS[1]="$PACKAGE_TYPE"
    jpackage "${COMMON_ARGS[@]}"
  fi
else
  jpackage "${COMMON_ARGS[@]}"
fi

if [[ "$OS_NAME" == "Darwin" && "$PACKAGE_TYPE" == "dmg" ]]; then
  FOUND_FILE="$(find "$OUTPUT_DIR" -maxdepth 1 -name "*.dmg" -print -quit)"
  if [[ -n "$FOUND_FILE" ]]; then
    cp "$FOUND_FILE" "$ROOT_DIR/web/public/downloads/nod.dmg"
  fi
fi

echo "安装包已生成到：$OUTPUT_DIR"
