#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ICONSET_DIR="$ROOT_DIR/packaging/macos/nod.iconset"
TARGET_ICNS="$ROOT_DIR/packaging/macos/nod.icns"
PYTHON_BIN="${PYTHON_BIN:-python3}"

rm -rf "$ICONSET_DIR"
mkdir -p "$ICONSET_DIR"

"$PYTHON_BIN" - <<'PY' "$ICONSET_DIR"
import struct
import sys
import zlib
from pathlib import Path

out_dir = Path(sys.argv[1])

def png_chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)

def lerp(a, b, t):
    return int(round(a + (b - a) * t))

def rgba_for_pixel(x, y, size):
    # Transparent rounded app-square background.
    radius = size * 0.22
    px = x + 0.5
    py = y + 0.5
    cx = min(max(px, radius), size - radius)
    cy = min(max(py, radius), size - radius)
    if (px - cx) ** 2 + (py - cy) ** 2 > radius ** 2:
        return (0, 0, 0, 0)

    # Blue-purple diagonal gradient, matching web favicon.svg.
    t = (x + y) / max(1, (size - 1) * 2)
    bg1 = (49, 95, 244)
    bg2 = (123, 63, 242)
    r = lerp(bg1[0], bg2[0], t)
    g = lerp(bg1[1], bg2[1], t)
    b = lerp(bg1[2], bg2[2], t)
    a = 255

    # White speech bubble ellipse.
    nx = (px - size * 0.50) / (size * 0.36)
    ny = (py - size * 0.45) / (size * 0.30)
    in_bubble = nx * nx + ny * ny <= 1.0
    # Bubble tail.
    tail = (px > size * 0.58 and px < size * 0.80 and py > size * 0.62 and py < size * 0.83 and
            py > size * 1.33 - px * 0.70 and py < size * 0.42 + px * 0.45)
    if in_bubble or tail:
        r, g, b, a = (255, 255, 255, 245)

    # Blue stylized N inside bubble.
    if size * 0.30 <= py <= size * 0.58:
        left = size * 0.31 <= px <= size * 0.41
        right = size * 0.60 <= px <= size * 0.70
        diag_center = size * 0.36 + (py - size * 0.30) * 1.05
        diag = abs(px - diag_center) <= size * 0.055
        if left or right or diag:
            r, g, b, a = (49, 95, 244, 255)

    # Purple smile arc approximation.
    sx = (px - size * 0.50) / (size * 0.27)
    sy = (py - size * 0.66) / (size * 0.13)
    arc = 0.76 <= sx * sx + sy * sy <= 1.18 and py >= size * 0.62 and size * 0.32 <= px <= size * 0.68
    if arc:
        r, g, b, a = (123, 63, 242, 255)

    return (r, g, b, a)

def write_png(path, size):
    rows = []
    for y in range(size):
        row = bytearray([0])
        for x in range(size):
            row.extend(rgba_for_pixel(x, y, size))
        rows.append(bytes(row))
    raw = b''.join(rows)
    png = b'\x89PNG\r\n\x1a\n'
    png += png_chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
    png += png_chunk(b'IDAT', zlib.compress(raw, 9))
    png += png_chunk(b'IEND', b'')
    path.write_bytes(png)

for size, scale in [(16,1),(16,2),(32,1),(32,2),(128,1),(128,2),(256,1),(256,2),(512,1),(512,2)]:
    pixels = size * scale
    suffix = '@2x' if scale == 2 else ''
    write_png(out_dir / f'icon_{size}x{size}{suffix}.png', pixels)
PY

rm -f "$ICONSET_DIR/icon_512x512@2x.png"
rm -f "$TARGET_ICNS"
iconutil -c icns "$ICONSET_DIR" -o "$TARGET_ICNS"

echo "图标已生成：$TARGET_ICNS"
