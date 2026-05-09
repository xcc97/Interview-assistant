#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ICONSET_DIR="$ROOT_DIR/packaging/macos/nod.iconset"
TARGET_ICNS="$ROOT_DIR/packaging/macos/nod.icns"
TARGET_PNG="$ROOT_DIR/src/main/resources/icons/nod.png"
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

def rounded_rect_alpha(px, py, size):
    radius = size * 0.24
    aa = max(1.0, size / 256.0)
    cx = min(max(px, radius), size - radius)
    cy = min(max(py, radius), size - radius)
    distance = ((px - cx) ** 2 + (py - cy) ** 2) ** 0.5 - radius
    if distance <= -aa:
        return 1.0
    if distance >= aa:
        return 0.0
    return (aa - distance) / (2 * aa)

def rgba_for_sample(px, py, size):
    alpha = rounded_rect_alpha(px, py, size)
    if alpha <= 0:
        return (0, 0, 0, 0)

    t = (px + py) / max(1.0, size * 2.0)
    color = (lerp(67, 106, t), lerp(83, 65, t), lerp(255, 239, t), int(round(255 * alpha)))

    # White circular badge.
    cx = size * 0.50
    cy = size * 0.46
    radius = size * 0.285
    aa = max(1.0, size / 256.0)
    distance = ((px - cx) ** 2 + (py - cy) ** 2) ** 0.5 - radius
    if distance <= aa:
        badge_alpha = 1.0 if distance <= -aa else (aa - distance) / (2 * aa)
        if badge_alpha > 0:
            color = blend((255, 255, 255, int(round(255 * badge_alpha))), color)

    # Blue block N inside the badge.
    if size * 0.35 <= py <= size * 0.58:
        left = size * 0.36 <= px <= size * 0.45
        right = size * 0.57 <= px <= size * 0.66
        diag_center = size * 0.405 + (py - size * 0.35) * 0.86
        diag = abs(px - diag_center) <= size * 0.048
        if left or right or diag:
            color = blend((67, 83, 255, 255), color)

    return color

def blend(src, dst):
    sr, sg, sb, sa = src
    dr, dg, db, da = dst
    sa_f = sa / 255.0
    da_f = da / 255.0
    out_a = sa_f + da_f * (1.0 - sa_f)
    if out_a <= 0:
        return (0, 0, 0, 0)
    out_r = (sr * sa_f + dr * da_f * (1.0 - sa_f)) / out_a
    out_g = (sg * sa_f + dg * da_f * (1.0 - sa_f)) / out_a
    out_b = (sb * sa_f + db * da_f * (1.0 - sa_f)) / out_a
    return (int(round(out_r)), int(round(out_g)), int(round(out_b)), int(round(out_a * 255)))

def rgba_for_pixel(x, y, size):
    supersample = 4 if size <= 256 else 3
    totals = [0, 0, 0, 0]
    for sy in range(supersample):
        for sx in range(supersample):
            px = x + (sx + 0.5) / supersample
            py = y + (sy + 0.5) / supersample
            sample = rgba_for_sample(px, py, size)
            for i in range(4):
                totals[i] += sample[i]
    count = supersample * supersample
    return tuple(int(round(value / count)) for value in totals)

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
    png += png_chunk(b'sRGB', b'\x00')
    png += png_chunk(b'IDAT', zlib.compress(raw, 9))
    png += png_chunk(b'IEND', b'')
    path.write_bytes(png)

for filename, pixels in [
    ('icon_16x16.png', 16),
    ('icon_16x16@2x.png', 32),
    ('icon_32x32.png', 32),
    ('icon_32x32@2x.png', 64),
    ('icon_128x128.png', 128),
    ('icon_128x128@2x.png', 256),
    ('icon_256x256.png', 256),
    ('icon_256x256@2x.png', 512),
    ('icon_512x512.png', 512),
    ('icon_512x512@2x.png', 1024),
]:
    write_png(out_dir / filename, pixels)
PY

rm -f "$TARGET_ICNS"
"$PYTHON_BIN" - <<'PY' "$ICONSET_DIR" "$TARGET_ICNS"
import sys
from pathlib import Path

iconset_dir = Path(sys.argv[1])
target_icns = Path(sys.argv[2])
icon_files = [
    ('icon_32x32@2x.png', 'ic12'),
    ('icon_128x128.png', 'ic07'),
    ('icon_128x128@2x.png', 'ic13'),
    ('icon_256x256.png', 'ic08'),
    ('icon_256x256@2x.png', 'ic14'),
    ('icon_512x512.png', 'ic09'),
    ('icon_512x512@2x.png', 'ic10'),
    ('icon_16x16@2x.png', 'ic11'),
]
icons = [(icon_type.encode('ascii'), (iconset_dir / filename).read_bytes()) for filename, icon_type in icon_files]
total_length = 8 + sum(8 + len(data) for _, data in icons)
content = bytearray(b'icns' + total_length.to_bytes(4, 'big'))
for icon_type, data in icons:
    content += icon_type + (8 + len(data)).to_bytes(4, 'big') + data
target_icns.write_bytes(content)
PY

mkdir -p "$(dirname "$TARGET_PNG")"
cp "$ICONSET_DIR/icon_256x256.png" "$TARGET_PNG"

echo "图标已生成：$TARGET_ICNS"
