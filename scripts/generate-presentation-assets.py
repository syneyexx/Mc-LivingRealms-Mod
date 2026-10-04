#!/usr/bin/env python3
"""Generate original LivingRealms presentation textures (Pillow, 64x64 skins/units).

Creates distinct citizen skins, unit silhouettes, wildlife morphology + per-species
palette variants, heraldry banners, siege icons, and simple GUI panels/icons.
No third-party mod assets are copied.
"""
from __future__ import annotations

import colorsys
import hashlib
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ENTITY = ROOT / "src/main/resources/assets/livingrealms/textures/entity"
HERALDRY = ROOT / "src/main/resources/assets/livingrealms/textures/heraldry"
GUI = ROOT / "src/main/resources/assets/livingrealms/textures/gui"
SPECIES_DIR = ROOT / "src/main/resources/data/livingrealms/livingrealms/species"

RGBA = tuple[int, int, int, int]
RGB = tuple[int, int, int]


def clamp(v: float, lo: float = 0.0, hi: float = 1.0) -> float:
    return max(lo, min(hi, v))


def rgb(r: float, g: float, b: float, a: float = 255) -> RGBA:
    return (
        max(0, min(255, int(r))),
        max(0, min(255, int(g))),
        max(0, min(255, int(b))),
        max(0, min(255, int(a))),
    )


def shade(c: RGB | RGBA, factor: float, a: int | None = None) -> RGBA:
    rr, gg, bb = c[:3]
    alpha = a if a is not None else (c[3] if len(c) > 3 else 255)
    return rgb(rr * factor, gg * factor, bb * factor, alpha)


def mix(a: RGB | RGBA, b: RGB | RGBA, t: float) -> RGBA:
    return rgb(
        a[0] + (b[0] - a[0]) * t,
        a[1] + (b[1] - a[1]) * t,
        a[2] + (b[2] - a[2]) * t,
        (a[3] if len(a) > 3 else 255) + ((b[3] if len(b) > 3 else 255) - (a[3] if len(a) > 3 else 255)) * t,
    )


def hsl(h: float, s: float, l: float, a: int = 255) -> RGBA:
    r, g, b = colorsys.hls_to_rgb(h % 1.0, clamp(l), clamp(s))
    return rgb(r * 255, g * 255, b * 255, a)


def seed_hash(*parts: object) -> int:
    h = hashlib.md5("|".join(str(p) for p in parts).encode()).hexdigest()
    return int(h[:8], 16)


def rng(seed: int) -> callable:
    state = seed & 0xFFFFFFFF

    def next_float() -> float:
        nonlocal state
        state = (1664525 * state + 1013904223) & 0xFFFFFFFF
        return state / 0x100000000

    return next_float


def new_img(w: int = 64, h: int = 64) -> Image.Image:
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def fill_rect(img: Image.Image, box: tuple[int, int, int, int], color: RGBA) -> None:
    x0, y0, x1, y1 = box
    px = img.load()
    for y in range(y0, y1):
        for x in range(x0, x1):
            if 0 <= x < img.width and 0 <= y < img.height:
                px[x, y] = color


def fill_rect_shade(img: Image.Image, box: tuple[int, int, int, int], color: RGBA, edge: float = 0.82) -> None:
    x0, y0, x1, y1 = box
    px = img.load()
    for y in range(y0, y1):
        for x in range(x0, x1):
            if not (0 <= x < img.width and 0 <= y < img.height):
                continue
            border = x == x0 or y == y0 or x == x1 - 1 or y == y1 - 1
            px[x, y] = shade(color, edge if border else 1.0)


def draw_pixel(img: Image.Image, x: int, y: int, color: RGBA) -> None:
    if 0 <= x < img.width and 0 <= y < img.height:
        img.putpixel((x, y), color)


def save_png(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, format="PNG", optimize=True)


# ---------------------------------------------------------------------------
# Minecraft-style classic 64x64 humanoid skin layout helpers
# ---------------------------------------------------------------------------

# Regions: (x, y, w, h)
HEAD = {
    "top": (8, 0, 8, 8),
    "bottom": (16, 0, 8, 8),
    "right": (0, 8, 8, 8),
    "front": (8, 8, 8, 8),
    "left": (16, 8, 8, 8),
    "back": (24, 8, 8, 8),
}
BODY = {
    "top": (20, 16, 8, 4),
    "bottom": (28, 16, 8, 4),
    "right": (16, 20, 4, 12),
    "front": (20, 20, 8, 12),
    "left": (28, 20, 4, 12),
    "back": (32, 20, 8, 12),
}
RARM = {
    "top": (44, 16, 4, 4),
    "bottom": (48, 16, 4, 4),
    "right": (40, 20, 4, 12),
    "front": (44, 20, 4, 12),
    "left": (48, 20, 4, 12),
    "back": (52, 20, 4, 12),
}
LARM = {
    "top": (36, 48, 4, 4),
    "bottom": (40, 48, 4, 4),
    "right": (32, 52, 4, 12),
    "front": (36, 52, 4, 12),
    "left": (40, 52, 4, 12),
    "back": (44, 52, 4, 12),
}
RLEG = {
    "top": (4, 16, 4, 4),
    "bottom": (8, 16, 4, 4),
    "right": (0, 20, 4, 12),
    "front": (4, 20, 4, 12),
    "left": (8, 20, 4, 12),
    "back": (12, 20, 4, 12),
}
LLEG = {
    "top": (20, 48, 4, 4),
    "bottom": (24, 48, 4, 4),
    "right": (16, 52, 4, 12),
    "front": (20, 52, 4, 12),
    "left": (24, 52, 4, 12),
    "back": (28, 52, 4, 12),
}


def paint_region(img: Image.Image, region: dict[str, tuple[int, int, int, int]], color: RGBA, dark: float = 0.78) -> None:
    for name, (x, y, w, h) in region.items():
        factor = 1.0
        if name in ("right", "bottom"):
            factor = dark
        elif name in ("left", "top"):
            factor = 1.08
        elif name == "back":
            factor = 0.9
        fill_rect_shade(img, (x, y, x + w, y + h), shade(color, factor), edge=dark * 0.95)


def paint_band(img: Image.Image, region: dict[str, tuple[int, int, int, int]], y0: int, y1: int, color: RGBA) -> None:
    """Paint a horizontal band across front/side/back faces of a limb/body region."""
    for name in ("right", "front", "left", "back"):
        x, y, w, h = region[name]
        yy0 = y + max(0, y0)
        yy1 = y + min(h, y1)
        if yy1 > yy0:
            fill_rect(img, (x, yy0, x + w, yy1), shade(color, 0.92 if name != "front" else 1.0))


def paint_face(img: Image.Image, skin: RGBA, eye: RGBA, brow: RGBA, mouth: RGBA) -> None:
    x, y, _, _ = HEAD["front"]
    # eyes
    draw_pixel(img, x + 2, y + 3, eye)
    draw_pixel(img, x + 5, y + 3, eye)
    draw_pixel(img, x + 2, y + 2, brow)
    draw_pixel(img, x + 5, y + 2, brow)
    # nose hint
    draw_pixel(img, x + 3, y + 4, shade(skin, 0.88))
    draw_pixel(img, x + 4, y + 4, shade(skin, 0.88))
    # mouth
    draw_pixel(img, x + 3, y + 6, mouth)
    draw_pixel(img, x + 4, y + 6, mouth)


def paint_hair(img: Image.Image, hair: RGBA, style: int) -> None:
    top = HEAD["top"]
    front = HEAD["front"]
    back = HEAD["back"]
    left = HEAD["left"]
    right = HEAD["right"]
    fill_rect(img, (top[0], top[1], top[0] + top[2], top[1] + top[3]), hair)
    # bangs / fringe
    if style % 4 != 3:
        fill_rect(img, (front[0], front[1], front[0] + front[2], front[1] + 2), hair)
    if style % 3 == 0:
        fill_rect(img, (back[0], back[1], back[0] + back[2], back[1] + 4), hair)
    if style % 3 == 1:
        fill_rect(img, (left[0] + 2, left[1], left[0] + left[2], left[1] + 5), hair)
        fill_rect(img, (right[0], right[1], right[0] + 2, right[1] + 5), hair)
    if style % 5 == 2:  # short crop with side shading
        fill_rect(img, (top[0], top[1] + 2, top[0] + top[2], top[1] + 4), shade(hair, 0.85))


SKIN_TONES = [
    (255, 224, 189),
    (241, 194, 150),
    (224, 172, 128),
    (198, 134, 98),
    (160, 104, 72),
    (120, 74, 48),
    (90, 56, 38),
    (232, 190, 150),
    (210, 160, 120),
    (175, 120, 85),
]

HAIR_COLORS = [
    (35, 28, 24),
    (70, 45, 30),
    (120, 80, 40),
    (180, 140, 70),
    (40, 40, 45),
    (90, 90, 95),
    (200, 180, 140),
    (30, 55, 40),
    (90, 30, 30),
    (20, 20, 22),
]


PROFESSION_GROUPS = [
    # 0-11 farmers/workers earth tones
    ("farmer", list(range(0, 12)), [(110, 90, 55), (90, 110, 60), (130, 100, 70), (85, 75, 50), (140, 120, 80), (100, 85, 55), (120, 95, 60), (95, 105, 70), (150, 115, 75), (80, 70, 45), (125, 100, 65), (105, 90, 50)]),
    # 12-17 merchants richer colors
    ("merchant", list(range(12, 18)), [(160, 50, 55), (45, 90, 140), (150, 110, 40), (100, 45, 120), (40, 120, 100), (170, 80, 40)]),
    # 18-23 guards darker armor-like
    ("guard", list(range(18, 24)), [(55, 60, 70), (70, 70, 75), (45, 50, 55), (80, 75, 70), (60, 55, 50), (50, 55, 65)]),
    # 24-29 clergy robes
    ("clergy", list(range(24, 30)), [(70, 55, 110), (90, 70, 40), (200, 200, 205), (40, 45, 70), (120, 40, 50), (50, 70, 55)]),
    # 30-35 scholars
    ("scholar", list(range(30, 36)), [(50, 70, 100), (140, 130, 110), (70, 90, 80), (100, 80, 60), (80, 60, 90), (45, 75, 95)]),
    # 36-41 rulers/court richer gold accents
    ("ruler", list(range(36, 42)), [(90, 30, 45), (30, 45, 90), (120, 90, 40), (70, 35, 80), (40, 70, 70), (100, 40, 55)]),
    # 42-47 refugees worn muted
    ("refugee", list(range(42, 48)), [(95, 90, 80), (85, 85, 75), (100, 95, 85), (75, 80, 70), (90, 85, 70), (80, 75, 65)]),
]


def citizen_palette(index: int) -> dict[str, RGBA]:
    group_name, indices, clothes = next((g for g in PROFESSION_GROUPS if index in g[1]), PROFESSION_GROUPS[0])
    local = indices.index(index)
    cloth = clothes[local % len(clothes)]
    rnd = rng(seed_hash("citizen", index, group_name))
    skin = SKIN_TONES[int(rnd() * len(SKIN_TONES))]
    hair = HAIR_COLORS[int(rnd() * len(HAIR_COLORS))]
    accent = {
        "farmer": (140, 110, 60),
        "merchant": (200, 160, 50),
        "guard": (160, 160, 170),
        "clergy": (210, 180, 70),
        "scholar": (180, 160, 100),
        "ruler": (220, 180, 60),
        "refugee": (110, 100, 85),
    }[group_name]
    pants = shade(cloth, 0.72)[:3]
    if group_name == "guard":
        pants = (50, 52, 58)
    if group_name == "ruler":
        pants = (40, 35, 50)
    boots = (40, 30, 22) if group_name != "refugee" else (70, 60, 50)
    return {
        "skin": rgb(*skin),
        "hair": rgb(*hair),
        "cloth": rgb(*cloth),
        "pants": rgb(*pants),
        "boots": rgb(*boots),
        "accent": rgb(*accent),
        "eye": rgb(30, 40, 55) if rnd() > 0.35 else rgb(70, 50, 30),
        "mouth": shade(skin, 0.75),
        "group": group_name,  # type: ignore[dict-item]
        "style": int(rnd() * 8),
    }


def generate_citizen(index: int) -> Image.Image:
    p = citizen_palette(index)
    img = new_img()
    # base skin everywhere first
    for region in (HEAD, BODY, RARM, LARM, RLEG, LLEG):
        paint_region(img, region, p["skin"])
    paint_hair(img, p["hair"], int(p["style"]))
    paint_face(img, p["skin"], p["eye"], shade(p["hair"], 0.9), p["mouth"])

    group = p["group"]
    # torso clothing
    paint_band(img, BODY, 0, 12, p["cloth"])
    paint_region(img, {"top": BODY["top"], "bottom": BODY["bottom"]}, p["cloth"], dark=0.85)
    # sleeves
    if group in ("farmer", "merchant", "clergy", "scholar", "ruler", "refugee"):
        paint_band(img, RARM, 0, 8 if group != "farmer" else 6, p["cloth"])
        paint_band(img, LARM, 0, 8 if group != "farmer" else 6, p["cloth"])
    if group == "guard":
        paint_band(img, RARM, 0, 10, p["cloth"])
        paint_band(img, LARM, 0, 10, p["cloth"])
        # chest plate highlight
        x, y, w, h = BODY["front"]
        fill_rect(img, (x + 2, y + 2, x + w - 2, y + 7), shade(p["accent"], 0.95))
        fill_rect(img, (x + 3, y + 3, x + w - 3, y + 6), shade(p["cloth"], 1.15))
    if group == "merchant":
        x, y, w, h = BODY["front"]
        fill_rect(img, (x + 1, y + 4, x + w - 1, y + 6), p["accent"])
        fill_rect(img, (x + 3, y + 7, x + 5, y + 10), shade(p["accent"], 0.85))
    if group == "clergy":
        paint_band(img, BODY, 0, 12, p["cloth"])
        x, y, w, h = BODY["front"]
        fill_rect(img, (x + 3, y + 2, x + 5, y + 8), p["accent"])
        fill_rect(img, (x + 2, y + 4, x + 6, y + 5), p["accent"])
    if group == "scholar":
        x, y, w, h = BODY["front"]
        fill_rect(img, (x + 1, y + 8, x + w - 1, y + 11), shade(p["pants"], 1.05))
        # collar
        fill_rect(img, (x + 2, y, x + w - 2, y + 2), shade(p["accent"], 1.0))
    if group == "ruler":
        x, y, w, h = BODY["front"]
        fill_rect(img, (x + 1, y + 1, x + w - 1, y + 3), p["accent"])
        fill_rect(img, (x + 3, y + 3, x + 5, y + 9), shade(p["accent"], 0.9))
        # gold trim on shoulders
        fill_rect(img, (BODY["right"][0], BODY["right"][1], BODY["right"][0] + BODY["right"][2], BODY["right"][1] + 2), p["accent"])
        fill_rect(img, (BODY["left"][0], BODY["left"][1], BODY["left"][0] + BODY["left"][2], BODY["left"][1] + 2), p["accent"])
        # simple crown on head top
        tx, ty, tw, th = HEAD["top"]
        fill_rect(img, (tx + 1, ty + 5, tx + tw - 1, ty + th), p["accent"])
        for dx in (1, 3, 5, 6):
            draw_pixel(img, tx + dx, ty + 4, p["accent"])
    if group == "refugee":
        # worn patches
        x, y, w, h = BODY["front"]
        fill_rect(img, (x + 1, y + 3, x + 3, y + 6), shade(p["cloth"], 0.75))
        fill_rect(img, (x + 5, y + 7, x + 7, y + 10), shade(p["cloth"], 1.12))
    if group == "farmer":
        x, y, w, h = BODY["front"]
        fill_rect(img, (x + 2, y + 5, x + 6, y + 7), shade(p["accent"], 0.9))

    # pants / legs
    paint_band(img, RLEG, 0, 9, p["pants"])
    paint_band(img, LLEG, 0, 9, p["pants"])
    paint_band(img, RLEG, 9, 12, p["boots"])
    paint_band(img, LLEG, 9, 12, p["boots"])
    paint_region(img, {"top": RLEG["top"], "bottom": RLEG["bottom"]}, p["pants"], dark=0.85)
    paint_region(img, {"top": LLEG["top"], "bottom": LLEG["bottom"]}, p["pants"], dark=0.85)

    # hands remain skin
    paint_band(img, RARM, 10, 12, p["skin"])
    paint_band(img, LARM, 10, 12, p["skin"])
    return img


def generate_role_overlay(index: int) -> Image.Image:
    """Optional translucent accent strip overlay; keeps base skin readable."""
    p = citizen_palette(index)
    img = new_img()
    accent = (*p["accent"][:3], 140)
    x, y, w, h = BODY["front"]
    fill_rect(img, (x + 2, y + 1, x + w - 2, y + 3), accent)
    return img


# ---------------------------------------------------------------------------
# Unit / vehicle silhouettes (still 64x64 skin-atlas friendly blocks)
# ---------------------------------------------------------------------------

def paint_humanoid_unit(primary: RGB, secondary: RGB, accent: RGB, helmet: bool = True, cape: bool = False) -> Image.Image:
    img = new_img()
    skin = rgb(210, 170, 130)
    metal = rgb(*primary)
    cloth = rgb(*secondary)
    trim = rgb(*accent)
    for region in (HEAD, BODY, RARM, LARM, RLEG, LLEG):
        paint_region(img, region, skin)
    paint_band(img, BODY, 0, 12, cloth)
    paint_band(img, RARM, 0, 9, metal if helmet else cloth)
    paint_band(img, LARM, 0, 9, metal if helmet else cloth)
    paint_band(img, RLEG, 0, 9, shade(cloth, 0.85)[:3] + (255,))
    paint_band(img, LLEG, 0, 9, shade(cloth, 0.85)[:3] + (255,))
    paint_band(img, RLEG, 9, 12, rgb(40, 35, 30))
    paint_band(img, LLEG, 9, 12, rgb(40, 35, 30))
    if helmet:
        paint_region(img, HEAD, metal, dark=0.75)
        fx, fy, _, _ = HEAD["front"]
        fill_rect(img, (fx + 1, fy + 3, fx + 7, fy + 5), shade(metal, 0.55))
        draw_pixel(img, fx + 2, fy + 3, rgb(20, 20, 25))
        draw_pixel(img, fx + 5, fy + 3, rgb(20, 20, 25))
        fill_rect(img, (HEAD["top"][0] + 1, HEAD["top"][1] + 1, HEAD["top"][0] + 7, HEAD["top"][1] + 3), trim)
    else:
        paint_hair(img, rgb(40, 30, 25), 1)
        paint_face(img, skin, rgb(25, 30, 40), rgb(40, 30, 25), shade(skin, 0.7))
    # chest emblem
    bx, by, bw, bh = BODY["front"]
    fill_rect(img, (bx + 3, by + 3, bx + 5, by + 7), trim)
    if cape:
        bx2, by2, bw2, bh2 = BODY["back"]
        fill_rect(img, (bx2, by2, bx2 + bw2, by2 + bh2), shade(trim, 0.85))
        fill_rect(img, (bx2 + 1, by2 + 1, bx2 + bw2 - 1, by2 + bh2 - 1), trim)
    return img


def generate_trade_caravan() -> Image.Image:
    img = new_img()
    wood = rgb(120, 80, 45)
    dark = rgb(80, 55, 30)
    cloth = rgb(160, 120, 70)
    animal = rgb(140, 110, 80)
    # pack animal body (left)
    fill_rect_shade(img, (8, 28, 28, 44), animal, 0.75)
    fill_rect_shade(img, (10, 20, 22, 30), animal, 0.8)  # neck/head
    fill_rect(img, (18, 16, 24, 22), shade(animal, 0.7))  # ears/head top
    draw_pixel(img, 21, 18, rgb(20, 20, 20))
    # legs
    for x in (10, 16, 20, 24):
        fill_rect(img, (x, 44, x + 3, 56), shade(animal, 0.65))
    # wagon (right)
    fill_rect_shade(img, (30, 30, 56, 46), wood, 0.72)
    fill_rect(img, (32, 22, 54, 32), cloth)  # canopy
    fill_rect(img, (32, 22, 54, 24), shade(cloth, 0.8))
    # wheels
    for cx in (34, 50):
        fill_rect(img, (cx, 44, cx + 6, 54), dark)
        fill_rect(img, (cx + 2, 46, cx + 4, 52), shade(dark, 1.3))
    # cargo crates
    fill_rect(img, (36, 34, 44, 42), rgb(150, 100, 50))
    fill_rect(img, (45, 36, 52, 42), rgb(100, 70, 40))
    return img


def generate_ship(kind: str = "ship") -> Image.Image:
    img = new_img()
    hull_colors = {
        "ship": (90, 70, 45),
        "ship_cargo": (110, 85, 50),
        "ship_patrol": (55, 70, 85),
        "ship_war": (50, 45, 55),
        "ship_landing": (100, 90, 60),
    }
    sail_colors = {
        "ship": (220, 210, 190),
        "ship_cargo": (180, 150, 90),
        "ship_patrol": (180, 200, 210),
        "ship_war": (140, 40, 45),
        "ship_landing": (200, 190, 160),
    }
    hull = rgb(*hull_colors[kind])
    sail = rgb(*sail_colors[kind])
    trim = rgb(200, 170, 70) if "war" in kind else rgb(160, 140, 100)
    # hull silhouette
    fill_rect_shade(img, (8, 34, 56, 50), hull, 0.75)
    for i, w in enumerate((10, 8, 6, 4)):
        fill_rect(img, (8 + i, 50 + i, 56 - i, 51 + i), shade(hull, 0.7))
    # deck
    fill_rect(img, (12, 32, 52, 36), shade(hull, 1.15))
    # mast + sail
    fill_rect(img, (30, 8, 34, 34), rgb(70, 50, 30))
    fill_rect_shade(img, (18, 10, 46, 28), sail, 0.85)
    # kind markers
    if kind == "ship_cargo":
        fill_rect(img, (14, 36, 24, 44), rgb(140, 100, 55))
        fill_rect(img, (40, 36, 50, 44), rgb(130, 95, 50))
    elif kind == "ship_patrol":
        fill_rect(img, (28, 6, 36, 10), trim)
        fill_rect(img, (22, 18, 42, 20), rgb(40, 60, 80))
    elif kind == "ship_war":
        fill_rect(img, (20, 30, 24, 36), rgb(40, 40, 45))  # prow ram
        fill_rect(img, (12, 12, 18, 18), rgb(160, 40, 40))  # banner
        fill_rect(img, (46, 36, 52, 42), rgb(70, 70, 75))  # gunwales
    elif kind == "ship_landing":
        fill_rect(img, (48, 40, 58, 48), shade(hull, 1.1))  # ramp
        fill_rect(img, (50, 42, 60, 46), shade(hull, 0.9))
    else:
        fill_rect(img, (24, 14, 40, 16), trim)
    # waterline darker
    fill_rect(img, (10, 46, 54, 50), shade(hull, 0.55))
    return img


def generate_aircraft(kind: str = "aircraft") -> Image.Image:
    img = new_img()
    body = {
        "aircraft": (170, 175, 180),
        "aircraft_patrol": (70, 95, 80),
        "aircraft_transport": (150, 140, 110),
    }[kind]
    accent = {
        "aircraft": (60, 90, 140),
        "aircraft_patrol": (200, 180, 60),
        "aircraft_transport": (90, 70, 50),
    }[kind]
    c = rgb(*body)
    a = rgb(*accent)
    # fuselage
    fill_rect_shade(img, (18, 28, 46, 40), c, 0.8)
    fill_rect(img, (46, 30, 56, 38), shade(c, 0.9))  # nose
    fill_rect(img, (12, 30, 18, 38), shade(c, 0.85))  # tail boom
    # wings
    fill_rect_shade(img, (8, 32, 56, 36), shade(c, 0.95), 0.85)
    fill_rect(img, (10, 30, 22, 38), shade(c, 0.9))
    fill_rect(img, (42, 30, 54, 38), shade(c, 0.9))
    # cockpit
    fill_rect(img, (40, 28, 48, 34), rgb(40, 70, 100))
    # markings
    fill_rect(img, (24, 33, 36, 35), a)
    if kind == "aircraft_patrol":
        fill_rect(img, (14, 26, 20, 30), a)
        fill_rect(img, (28, 24, 32, 28), shade(a, 0.8))
    if kind == "aircraft_transport":
        fill_rect(img, (20, 36, 40, 42), shade(c, 0.75))
        fill_rect(img, (22, 38, 38, 44), shade(accent + (255,), 0.9) if False else rgb(*accent))
    # prop hint
    fill_rect(img, (54, 32, 60, 36), rgb(40, 40, 45))
    return img


def generate_bounty_hunter() -> Image.Image:
    img = paint_humanoid_unit((55, 45, 40), (70, 55, 45), (140, 100, 40), helmet=False, cape=True)
    # wide hat
    hx, hy, hw, hh = HEAD["top"]
    fill_rect(img, (hx - 1, hy + 5, hx + hw + 1, hy + hh), rgb(35, 28, 22))
    fill_rect(img, (HEAD["front"][0] - 1, HEAD["front"][1], HEAD["front"][0] + HEAD["front"][2] + 1, HEAD["front"][1] + 2), rgb(35, 28, 22))
    fill_rect(img, (HEAD["front"][0] + 1, HEAD["front"][1] + 2, HEAD["front"][0] + 7, HEAD["front"][1] + 3), rgb(25, 20, 16))
    # cloak over shoulders
    paint_band(img, BODY, 0, 4, rgb(45, 35, 30))
    bx, by, bw, bh = BODY["back"]
    fill_rect(img, (bx, by, bx + bw, by + bh), rgb(50, 38, 30))
    fill_rect(img, (bx + 1, by + 2, bx + bw - 1, by + bh), rgb(90, 60, 35))
    # scarf
    fx, fy, _, _ = HEAD["front"]
    fill_rect(img, (fx + 1, fy + 6, fx + 7, fy + 8), rgb(120, 40, 35))
    return img


# ---------------------------------------------------------------------------
# Wildlife morphology textures + species variants
# ---------------------------------------------------------------------------

MORPH_BASE_COLORS: dict[str, RGB] = {
    "bear": (90, 60, 35),
    "bird": (70, 110, 150),
    "canid": (160, 130, 90),
    "cetacean": (70, 90, 110),
    "crocodilian": (70, 100, 55),
    "fish": (80, 140, 160),
    "large_mammal": (110, 110, 115),
    "primate": (120, 90, 60),
    "raptor": (140, 100, 50),
    "ungulate": (150, 120, 80),
    "pinniped": (90, 95, 100),
    "predator_quadruped": (170, 120, 60),
    "small_quadruped": (180, 150, 120),
}


def morph_family_key(morphology: str, species_id: str = "", mass: float = 0.0) -> str:
    m = morphology.upper()
    sid = species_id.lower()
    if m == "URSID":
        return "bear"
    if m == "BIRD":
        return "bird"
    if m == "RAPTOR_BIRD":
        return "raptor"
    if m == "CANID":
        return "canid"
    if m == "FELID":
        return "predator_quadruped"
    if m == "CETACEAN":
        return "cetacean"
    if m == "CROCODILIAN":
        return "crocodilian"
    if m in ("FISH", "SHARK"):
        return "fish"
    if m == "UNGULATE" or m == "SUID":
        return "ungulate"
    if m in ("PROBOSCIDEAN", "HIPPOPOTAMID"):
        return "large_mammal"
    if m == "PINNIPED":
        return "pinniped"
    if m in ("RODENT", "LAGOMORPH"):
        return "small_quadruped"
    if m in ("OTHER_REPTILE", "AMPHIBIAN"):
        return "crocodilian"
    if m == "INVERTEBRATE":
        return "fish"
    if m == "GENERIC_QUADRUPED":
        if any(x in sid for x in ("chimp", "gorilla", "orang", "baboon", "monkey", "ape", "gibbon", "macaque", "lemur")):
            return "primate"
        if mass > 900:
            return "large_mammal"
        return "ungulate"
    return "ungulate"


def draw_quad_body(img: Image.Image, body: RGBA, accent: RGBA, snout: bool = True, ears: bool = True, horns: bool = False, mane: bool = False) -> None:
    # torso
    fill_rect_shade(img, (18, 28, 46, 44), body, 0.78)
    # head
    fill_rect_shade(img, (40, 22, 54, 34), body, 0.8)
    if snout:
        fill_rect(img, (50, 28, 58, 34), shade(body, 0.9))
        draw_pixel(img, 56, 30, rgb(20, 20, 20))
    if ears:
        fill_rect(img, (42, 18, 46, 24), shade(body, 0.75))
        fill_rect(img, (48, 18, 52, 24), shade(body, 0.75))
    if horns:
        fill_rect(img, (44, 14, 46, 22), accent)
        fill_rect(img, (50, 14, 52, 22), accent)
    if mane:
        fill_rect(img, (36, 24, 46, 32), accent)
    # legs
    for x in (20, 28, 34, 42):
        fill_rect(img, (x, 44, x + 4, 56), shade(body, 0.65))
    # tail
    fill_rect(img, (12, 32, 18, 38), shade(body, 0.7))
    # belly
    fill_rect(img, (22, 36, 42, 42), shade(body, 1.15))
    # atlas-style second copy hint in upper half for UV variety
    fill_rect(img, (8, 8, 24, 18), shade(body, 0.85))
    fill_rect(img, (26, 8, 40, 16), accent)


def draw_bear(img: Image.Image, body: RGBA, accent: RGBA) -> None:
    fill_rect_shade(img, (16, 26, 48, 46), body, 0.78)
    fill_rect_shade(img, (40, 20, 56, 36), body, 0.8)
    fill_rect(img, (50, 28, 58, 34), shade(body, 0.9))
    fill_rect(img, (42, 16, 48, 24), shade(body, 0.7))
    fill_rect(img, (48, 16, 54, 24), shade(body, 0.7))
    for x in (18, 26, 34, 42):
        fill_rect(img, (x, 46, x + 5, 58), shade(body, 0.62))
    fill_rect(img, (22, 38, 42, 44), shade(body, 1.2))
    draw_pixel(img, 54, 26, rgb(20, 20, 20))
    draw_pixel(img, 50, 26, rgb(20, 20, 20))
    fill_rect(img, (8, 8, 28, 20), shade(body, 0.85))
    fill_rect(img, (30, 10, 44, 18), accent)


def draw_bird(img: Image.Image, body: RGBA, accent: RGBA, raptor: bool = False) -> None:
    # body
    fill_rect_shade(img, (24, 28, 42, 44), body, 0.8)
    # head
    fill_rect_shade(img, (38, 22, 50, 34), body, 0.85)
    beak = rgb(220, 180, 60) if not raptor else rgb(200, 140, 40)
    fill_rect(img, (48, 28, 56, 32), beak)
    if raptor:
        fill_rect(img, (48, 30, 58, 33), shade(beak, 0.85))
    # wings
    fill_rect_shade(img, (6, 30, 24, 40), shade(body, 0.9), 0.8)
    fill_rect_shade(img, (42, 30, 58, 40), shade(body, 0.9), 0.8)
    fill_rect(img, (8, 32, 22, 36), accent)
    fill_rect(img, (44, 32, 56, 36), accent)
    # legs/talons
    fill_rect(img, (28, 44, 32, 54), shade(body, 0.7))
    fill_rect(img, (34, 44, 38, 54), shade(body, 0.7))
    fill_rect(img, (26, 54, 40, 58), beak if raptor else shade(body, 0.6))
    # eye
    draw_pixel(img, 42, 26, rgb(20, 20, 20))
    fill_rect(img, (10, 8, 30, 18), shade(body, 0.85))
    fill_rect(img, (32, 10, 48, 16), accent)


def draw_fish(img: Image.Image, body: RGBA, accent: RGBA, shark: bool = False) -> None:
    fill_rect_shade(img, (14, 26, 48, 42), body, 0.8)
    # head/nose
    fill_rect(img, (44, 28, 56, 40), shade(body, 0.95))
    # tail
    fill_rect(img, (6, 24, 16, 32), accent)
    fill_rect(img, (6, 36, 16, 44), accent)
    # fin
    fill_rect(img, (28, 18, 36, 28), shade(body, 0.75))
    if shark:
        fill_rect(img, (30, 14, 34, 28), shade(body, 0.65))
        fill_rect(img, (48, 34, 58, 38), shade(body, 0.7))
    # stripe / belly
    fill_rect(img, (18, 34, 44, 40), shade(body, 1.2))
    fill_rect(img, (20, 30, 40, 32), accent)
    draw_pixel(img, 50, 30, rgb(15, 15, 20))
    fill_rect(img, (8, 8, 28, 18), shade(body, 0.85))
    fill_rect(img, (30, 10, 46, 16), accent)


def draw_cetacean(img: Image.Image, body: RGBA, accent: RGBA) -> None:
    fill_rect_shade(img, (10, 28, 50, 44), body, 0.8)
    fill_rect(img, (46, 30, 58, 42), shade(body, 0.9))
    fill_rect(img, (4, 24, 14, 34), accent)  # fluke
    fill_rect(img, (4, 38, 14, 48), accent)
    fill_rect(img, (28, 20, 36, 30), shade(body, 0.75))  # dorsal
    fill_rect(img, (16, 36, 44, 42), shade(body, 1.25))
    draw_pixel(img, 52, 34, rgb(20, 20, 25))
    fill_rect(img, (8, 8, 30, 18), shade(body, 0.85))
    fill_rect(img, (32, 10, 48, 16), accent)


def draw_croc(img: Image.Image, body: RGBA, accent: RGBA) -> None:
    fill_rect_shade(img, (12, 30, 40, 42), body, 0.78)
    fill_rect(img, (38, 32, 58, 40), shade(body, 0.9))  # long snout
    fill_rect(img, (8, 28, 14, 36), shade(body, 0.7))  # tail start
    fill_rect(img, (2, 30, 10, 38), accent)
    for x in (16, 24, 32, 36):
        fill_rect(img, (x, 42, x + 3, 52), shade(body, 0.65))
    # scute ridge
    for x in range(14, 38, 4):
        fill_rect(img, (x, 28, x + 2, 32), accent)
    draw_pixel(img, 42, 34, rgb(20, 40, 20))
    fill_rect(img, (8, 8, 28, 18), shade(body, 0.85))
    fill_rect(img, (30, 10, 46, 16), accent)


def draw_primate(img: Image.Image, body: RGBA, accent: RGBA) -> None:
    fill_rect_shade(img, (22, 26, 42, 46), body, 0.8)
    fill_rect_shade(img, (28, 14, 44, 28), body, 0.85)
    face = shade(body, 1.25)
    fill_rect(img, (32, 20, 42, 28), face)
    draw_pixel(img, 34, 22, rgb(20, 20, 20))
    draw_pixel(img, 38, 22, rgb(20, 20, 20))
    # arms
    fill_rect(img, (14, 28, 22, 48), shade(body, 0.75))
    fill_rect(img, (42, 28, 50, 48), shade(body, 0.75))
    # legs
    fill_rect(img, (24, 46, 30, 58), shade(body, 0.7))
    fill_rect(img, (34, 46, 40, 58), shade(body, 0.7))
    fill_rect(img, (30, 30, 38, 38), accent)
    fill_rect(img, (8, 8, 26, 18), shade(body, 0.85))
    fill_rect(img, (28, 10, 44, 16), accent)


def draw_wildlife(family: str, body: RGBA, accent: RGBA) -> Image.Image:
    img = new_img()
    if family == "bear":
        draw_bear(img, body, accent)
    elif family == "bird":
        draw_bird(img, body, accent, raptor=False)
    elif family == "raptor":
        draw_bird(img, body, accent, raptor=True)
    elif family == "fish":
        draw_fish(img, body, accent, shark=False)
    elif family == "cetacean":
        draw_cetacean(img, body, accent)
    elif family == "crocodilian":
        draw_croc(img, body, accent)
    elif family == "primate":
        draw_primate(img, body, accent)
    elif family == "canid":
        draw_quad_body(img, body, accent, snout=True, ears=True, mane=False)
    elif family == "predator_quadruped":
        draw_quad_body(img, body, accent, snout=True, ears=True, mane=True)
    elif family == "ungulate":
        draw_quad_body(img, body, accent, snout=True, ears=True, horns=True)
    elif family == "large_mammal":
        fill_rect_shade(img, (14, 24, 50, 46), body, 0.78)
        fill_rect_shade(img, (42, 20, 56, 36), body, 0.8)
        fill_rect(img, (50, 30, 60, 40), shade(body, 0.85))  # trunk/snout
        for x in (18, 26, 34, 42):
            fill_rect(img, (x, 46, x + 5, 58), shade(body, 0.65))
        fill_rect(img, (10, 28, 16, 40), accent)
        fill_rect(img, (8, 8, 28, 18), shade(body, 0.85))
        fill_rect(img, (30, 10, 46, 16), accent)
    elif family == "pinniped":
        fill_rect_shade(img, (14, 30, 48, 46), body, 0.8)
        fill_rect(img, (44, 28, 56, 40), shade(body, 0.9))
        fill_rect(img, (8, 34, 16, 44), accent)
        fill_rect(img, (20, 44, 28, 52), shade(body, 0.7))
        fill_rect(img, (32, 44, 40, 52), shade(body, 0.7))
        draw_pixel(img, 50, 32, rgb(20, 20, 25))
        fill_rect(img, (8, 8, 28, 18), shade(body, 0.85))
        fill_rect(img, (30, 10, 46, 16), accent)
    else:
        draw_quad_body(img, body, accent)
    # small_quadruped slightly smaller
    if family == "small_quadruped":
        img = new_img()
        fill_rect_shade(img, (22, 32, 42, 44), body, 0.8)
        fill_rect_shade(img, (38, 26, 50, 36), body, 0.85)
        fill_rect(img, (46, 30, 54, 34), shade(body, 0.9))
        fill_rect(img, (40, 22, 44, 28), shade(body, 0.75))
        fill_rect(img, (46, 22, 50, 28), shade(body, 0.75))
        for x in (24, 30, 34, 38):
            fill_rect(img, (x, 44, x + 3, 54), shade(body, 0.65))
        fill_rect(img, (16, 34, 22, 40), accent)
        fill_rect(img, (8, 8, 24, 16), shade(body, 0.85))
        fill_rect(img, (26, 10, 40, 14), accent)
    return img


def shift_palette(img: Image.Image, hue_shift: float, sat_mul: float, light_mul: float) -> Image.Image:
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, l, s = colorsys.rgb_to_hls(r / 255.0, g / 255.0, b / 255.0)
            h = (h + hue_shift) % 1.0
            s = clamp(s * sat_mul)
            l = clamp(l * light_mul)
            rr, gg, bb = colorsys.hls_to_rgb(h, l, s)
            px[x, y] = (int(rr * 255), int(gg * 255), int(bb * 255), a)
    return out


def species_tint_params(species_id: str, index: int) -> tuple[float, float, float, float, float]:
    rnd = rng(seed_hash("species", species_id, index))
    hue = (index * 0.047) + rnd() * 0.08
    sat = 0.75 + rnd() * 0.55
    light = 0.78 + rnd() * 0.45
    body_l = 0.25 + rnd() * 0.35
    accent_h = hue + 0.12 + rnd() * 0.2
    return hue, sat, light, body_l, accent_h


def generate_wildlife_family(family: str) -> Image.Image:
    base = MORPH_BASE_COLORS.get(family, (120, 100, 80))
    accent = shade(base, 1.35)[:3]
    # distinctive accents per family
    accents = {
        "bear": (60, 40, 25),
        "bird": (200, 180, 60),
        "canid": (90, 70, 50),
        "cetacean": (200, 200, 210),
        "crocodilian": (40, 70, 30),
        "fish": (240, 200, 80),
        "large_mammal": (80, 80, 85),
        "primate": (180, 140, 110),
        "raptor": (40, 40, 40),
        "ungulate": (230, 220, 200),
        "pinniped": (220, 220, 225),
        "predator_quadruped": (40, 30, 20),
        "small_quadruped": (240, 230, 210),
    }
    return draw_wildlife(family, rgb(*base), rgb(*accents.get(family, accent)))


def generate_species_texture(species: dict, index: int) -> Image.Image:
    family = morph_family_key(species.get("morphology", "UNGULATE"), species.get("id", ""), float(species.get("adultMassKg", 0) or 0))
    hue, sat, light, body_l, accent_h = species_tint_params(species["id"], index)
    body = hsl(hue, sat * 0.7, body_l)
    accent = hsl(accent_h, clamp(sat), clamp(light * 0.55))
    # diet/climate nudges for extra uniqueness
    diet = str(species.get("diet", "")).upper()
    if diet == "CARNIVORE":
        body = mix(body, rgb(140, 70, 50), 0.15)
    elif diet == "HERBIVORE":
        body = mix(body, rgb(90, 120, 70), 0.12)
    climates = species.get("climates") or []
    if "POLAR" in climates or "BOREAL" in climates:
        body = mix(body, rgb(220, 220, 225), 0.25)
        accent = mix(accent, rgb(200, 200, 210), 0.2)
    if "ARID" in climates:
        body = mix(body, rgb(180, 140, 80), 0.18)
    img = draw_wildlife(family, body, accent)
    # slight additional hue shift pass keyed by id
    return shift_palette(img, (seed_hash(species["id"]) % 1000) / 10000.0, 1.0, 1.0)


# ---------------------------------------------------------------------------
# Heraldry, siege, GUI
# ---------------------------------------------------------------------------

BANNER_PALETTES = [
    ((140, 30, 35), (220, 200, 160), "cross"),
    ((30, 60, 120), (220, 200, 80), "chevron"),
    ((30, 100, 70), (240, 230, 200), "circle"),
    ((90, 40, 110), (200, 180, 60), "star"),
    ((40, 90, 50), (230, 210, 140), "tree"),
    ((20, 70, 110), (180, 210, 230), "wave"),
    ((120, 80, 30), (40, 30, 20), "stripe"),
    ((50, 50, 55), (200, 60, 60), "diamond"),
    ((160, 90, 40), (240, 220, 160), "sun"),
    ((70, 30, 40), (180, 160, 120), "tower"),
    ((35, 80, 90), (220, 180, 70), "anchor"),
    ((100, 30, 60), (230, 210, 190), "crescent"),
    ((45, 55, 35), (200, 190, 120), "leaf"),
    ((80, 20, 20), (220, 200, 80), "sword"),
    ((25, 45, 80), (190, 200, 220), "ring"),
    ((110, 70, 40), (240, 220, 100), "crown"),
]


def draw_emblem(draw: ImageDraw.ImageDraw, kind: str, color: RGBA, cx: int = 32, cy: int = 34) -> None:
    if kind == "cross":
        draw.rectangle([cx - 2, cy - 12, cx + 2, cy + 12], fill=color)
        draw.rectangle([cx - 10, cy - 2, cx + 10, cy + 2], fill=color)
    elif kind == "chevron":
        draw.polygon([(cx - 12, cy + 6), (cx, cy - 10), (cx + 12, cy + 6), (cx + 8, cy + 6), (cx, cy - 4), (cx - 8, cy + 6)], fill=color)
    elif kind == "circle":
        draw.ellipse([cx - 10, cy - 10, cx + 10, cy + 10], outline=color, width=3)
        draw.ellipse([cx - 4, cy - 4, cx + 4, cy + 4], fill=color)
    elif kind == "star":
        pts = []
        for i in range(10):
            ang = -math.pi / 2 + i * math.pi / 5
            r = 11 if i % 2 == 0 else 5
            pts.append((cx + r * math.cos(ang), cy + r * math.sin(ang)))
        draw.polygon(pts, fill=color)
    elif kind == "tree":
        draw.polygon([(cx, cy - 12), (cx - 10, cy + 2), (cx + 10, cy + 2)], fill=color)
        draw.rectangle([cx - 2, cy + 2, cx + 2, cy + 12], fill=color)
    elif kind == "wave":
        for i in range(3):
            y = cy - 6 + i * 6
            draw.arc([cx - 12, y - 4, cx + 12, y + 8], 0, 180, fill=color, width=2)
    elif kind == "stripe":
        draw.rectangle([cx - 12, cy - 10, cx + 12, cy - 6], fill=color)
        draw.rectangle([cx - 12, cy - 2, cx + 12, cy + 2], fill=color)
        draw.rectangle([cx - 12, cy + 6, cx + 12, cy + 10], fill=color)
    elif kind == "diamond":
        draw.polygon([(cx, cy - 12), (cx + 10, cy), (cx, cy + 12), (cx - 10, cy)], fill=color)
    elif kind == "sun":
        draw.ellipse([cx - 7, cy - 7, cx + 7, cy + 7], fill=color)
        for i in range(8):
            ang = i * math.pi / 4
            draw.line([(cx + 9 * math.cos(ang), cy + 9 * math.sin(ang)), (cx + 13 * math.cos(ang), cy + 13 * math.sin(ang))], fill=color, width=2)
    elif kind == "tower":
        draw.rectangle([cx - 6, cy - 4, cx + 6, cy + 12], fill=color)
        draw.rectangle([cx - 8, cy - 10, cx + 8, cy - 4], fill=color)
        for dx in (-6, 0, 6):
            draw.rectangle([cx + dx - 1, cy - 14, cx + dx + 1, cy - 10], fill=color)
    elif kind == "anchor":
        draw.rectangle([cx - 1, cy - 10, cx + 1, cy + 8], fill=color)
        draw.ellipse([cx - 4, cy - 14, cx + 4, cy - 6], outline=color, width=2)
        draw.arc([cx - 10, cy, cx + 10, cy + 14], 0, 180, fill=color, width=3)
    elif kind == "crescent":
        draw.ellipse([cx - 10, cy - 10, cx + 8, cy + 10], fill=color)
        draw.ellipse([cx - 4, cy - 8, cx + 10, cy + 8], fill=(0, 0, 0, 0))
    elif kind == "leaf":
        draw.ellipse([cx - 8, cy - 10, cx + 8, cy + 8], fill=color)
        draw.line([(cx, cy - 10), (cx, cy + 10)], fill=shade(color, 0.7), width=1)
    elif kind == "sword":
        draw.rectangle([cx - 1, cy - 12, cx + 1, cy + 6], fill=color)
        draw.polygon([(cx - 3, cy - 12), (cx, cy - 16), (cx + 3, cy - 12)], fill=color)
        draw.rectangle([cx - 6, cy + 4, cx + 6, cy + 6], fill=color)
        draw.rectangle([cx - 1, cy + 6, cx + 1, cy + 12], fill=color)
    elif kind == "ring":
        draw.ellipse([cx - 11, cy - 11, cx + 11, cy + 11], outline=color, width=3)
    elif kind == "crown":
        draw.rectangle([cx - 10, cy, cx + 10, cy + 8], fill=color)
        draw.polygon([(cx - 10, cy), (cx - 6, cy - 10), (cx - 2, cy), (cx + 2, cy - 10), (cx + 6, cy), (cx + 10, cy - 10), (cx + 10, cy)], fill=color)
    else:
        draw.rectangle([cx - 8, cy - 8, cx + 8, cy + 8], outline=color, width=2)


def generate_banner(index: int) -> Image.Image:
    field, emblem_rgb, kind = BANNER_PALETTES[index % len(BANNER_PALETTES)]
    img = new_img()
    # banner cloth hanging shape
    fill_rect_shade(img, (12, 8, 52, 56), rgb(*field), 0.82)
    # swallowtail tip
    draw = ImageDraw.Draw(img)
    draw.polygon([(12, 56), (32, 62), (52, 56), (52, 50), (12, 50)], fill=rgb(*field))
    # border
    draw.rectangle([12, 8, 51, 55], outline=shade(field, 0.55), width=2)
    # pole
    fill_rect(img, (10, 4, 54, 8), rgb(90, 70, 40))
    draw_emblem(draw, kind, rgb(*emblem_rgb))
    # fix crescent knockout by redrawing field under secondary ellipse manually
    if kind == "crescent":
        # redraw clean crescent using pixels
        img2 = new_img()
        fill_rect_shade(img2, (12, 8, 52, 56), rgb(*field), 0.82)
        d2 = ImageDraw.Draw(img2)
        d2.polygon([(12, 56), (32, 62), (52, 56), (52, 50), (12, 50)], fill=rgb(*field))
        d2.rectangle([12, 8, 51, 55], outline=shade(field, 0.55), width=2)
        fill_rect(img2, (10, 4, 54, 8), rgb(90, 70, 40))
        d2.ellipse([22, 24, 42, 46], fill=rgb(*emblem_rgb))
        d2.ellipse([28, 26, 46, 44], fill=rgb(*field))
        return img2
    return img


def generate_siege(kind: str) -> Image.Image:
    img = new_img()
    wood = rgb(120, 85, 50)
    dark = rgb(70, 50, 30)
    metal = rgb(90, 95, 100)
    if kind == "siege_ram":
        fill_rect_shade(img, (10, 24, 54, 40), wood, 0.75)
        fill_rect(img, (8, 28, 18, 36), metal)  # ram head
        fill_rect(img, (12, 18, 50, 26), shade(wood, 0.9))  # roof
        for x in (14, 28, 42):
            fill_rect(img, (x, 40, x + 4, 54), dark)
        fill_rect(img, (20, 30, 44, 36), shade(wood, 1.15))
    elif kind == "siege_ladder":
        fill_rect(img, (20, 8, 24, 56), wood)
        fill_rect(img, (40, 8, 44, 56), wood)
        for y in range(12, 56, 8):
            fill_rect(img, (20, y, 44, y + 3), shade(wood, 1.1))
        fill_rect(img, (18, 6, 46, 10), dark)
    elif kind == "siege_artillery":
        # frame
        fill_rect(img, (14, 36, 50, 44), wood)
        fill_rect(img, (18, 28, 28, 48), wood)
        fill_rect(img, (36, 28, 46, 48), wood)
        # barrel
        fill_rect_shade(img, (22, 20, 52, 30), metal, 0.8)
        fill_rect(img, (50, 22, 58, 28), shade(metal, 0.7))
        # wheels
        fill_rect(img, (16, 42, 26, 54), dark)
        fill_rect(img, (38, 42, 48, 54), dark)
        fill_rect(img, (28, 16, 34, 22), rgb(140, 50, 40))
    return img


def generate_dashboard_panel() -> Image.Image:
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    # dark wood base
    base = rgb(62, 42, 28)
    fill_rect(img, (0, 0, 256, 256), base)
    px = img.load()
    rnd = rng(0xD00D)
    for y in range(256):
        for x in range(256):
            n = (math.sin(x * 0.07 + y * 0.02) + math.sin(y * 0.11)) * 0.5
            grain = 1.0 + 0.08 * n + (rnd() - 0.5) * 0.04
            # parchment inset
            inset = 18 <= x < 238 and 18 <= y < 238
            if inset:
                pr, pg, pb = (210, 190, 150)
                stain = 1.0 + 0.05 * math.sin(x * 0.03) * math.cos(y * 0.04)
                px[x, y] = rgb(pr * stain * grain, pg * stain * grain, pb * stain * grain)
            else:
                px[x, y] = shade(base, grain)
    # metal corner brackets
    metal = rgb(120, 100, 70)
    for x0, y0 in ((4, 4), (228, 4), (4, 228), (228, 228)):
        fill_rect(img, (x0, y0, x0 + 24, y0 + 6), metal)
        fill_rect(img, (x0, y0, x0 + 6, y0 + 24), metal)
    # inner frame
    draw = ImageDraw.Draw(img)
    draw.rectangle([16, 16, 239, 239], outline=rgb(90, 70, 45), width=2)
    draw.rectangle([20, 20, 235, 235], outline=rgb(160, 130, 90), width=1)
    return img


def generate_map_frame() -> Image.Image:
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    wood = rgb(70, 48, 30)
    fill_rect(img, (0, 0, 256, 256), (*wood[:3], 0))
    # only frame border opaque
    fill_rect(img, (0, 0, 256, 18), wood)
    fill_rect(img, (0, 238, 256, 256), wood)
    fill_rect(img, (0, 0, 18, 256), wood)
    fill_rect(img, (238, 0, 256, 256), wood)
    draw = ImageDraw.Draw(img)
    draw.rectangle([2, 2, 253, 253], outline=rgb(160, 130, 80), width=2)
    draw.rectangle([16, 16, 239, 239], outline=rgb(40, 28, 18), width=2)
    # corner bosses
    for x, y in ((6, 6), (238, 6), (6, 238), (238, 238)):
        fill_rect(img, (x, y, x + 12, y + 12), rgb(140, 110, 60))
    return img


def generate_icon(kind: str, size: int = 16) -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    s = size
    if kind == "icon_food":
        # apple-like
        d.ellipse([s * 0.2, s * 0.25, s * 0.8, s * 0.9], fill=rgb(180, 50, 45))
        d.rectangle([s * 0.45, s * 0.1, s * 0.55, s * 0.3], fill=rgb(90, 60, 30))
        d.ellipse([s * 0.55, s * 0.1, s * 0.8, s * 0.35], fill=rgb(50, 120, 50))
    elif kind == "icon_war":
        d.polygon([(s * 0.5, s * 0.1), (s * 0.65, s * 0.55), (s * 0.5, s * 0.45), (s * 0.35, s * 0.55)], fill=rgb(180, 180, 190))
        d.rectangle([s * 0.45, s * 0.5, s * 0.55, s * 0.85], fill=rgb(120, 80, 40))
        d.rectangle([s * 0.3, s * 0.55, s * 0.7, s * 0.65], fill=rgb(90, 70, 40))
    elif kind == "icon_trade":
        d.ellipse([s * 0.15, s * 0.2, s * 0.85, s * 0.85], fill=rgb(200, 160, 50))
        d.ellipse([s * 0.28, s * 0.32, s * 0.72, s * 0.72], outline=rgb(140, 100, 30), width=max(1, s // 16))
        d.rectangle([s * 0.45, s * 0.25, s * 0.55, s * 0.8], fill=rgb(140, 100, 30))
    elif kind == "icon_crown":
        d.polygon([
            (s * 0.15, s * 0.7), (s * 0.15, s * 0.35), (s * 0.3, s * 0.55),
            (s * 0.5, s * 0.2), (s * 0.7, s * 0.55), (s * 0.85, s * 0.35),
            (s * 0.85, s * 0.7),
        ], fill=rgb(220, 180, 60))
        d.rectangle([s * 0.15, s * 0.65, s * 0.85, s * 0.85], fill=rgb(200, 160, 50))
    return img


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def load_species() -> list[dict]:
    items = []
    for path in sorted(SPECIES_DIR.glob("*.json")):
        data = json.loads(path.read_text())
        if "id" in data:
            items.append(data)
    return items


def verify_png(path: Path) -> bool:
    data = path.read_bytes()
    return len(data) >= 8 and data[:8] == b"\x89PNG\r\n\x1a\n"


def main() -> None:
    ENTITY.mkdir(parents=True, exist_ok=True)
    HERALDRY.mkdir(parents=True, exist_ok=True)
    GUI.mkdir(parents=True, exist_ok=True)

    created: list[Path] = []

    # 1) Citizens 0-47 + base alias
    for i in range(48):
        img = generate_citizen(i)
        path = ENTITY / f"faction_citizen_{i}.png"
        save_png(img, path)
        created.append(path)
        # optional role overlay
        overlay = generate_role_overlay(i)
        op = ENTITY / f"faction_citizen_{i}_overlay.png"
        save_png(overlay, op)
        created.append(op)
    save_png(generate_citizen(0), ENTITY / "faction_citizen.png")
    created.append(ENTITY / "faction_citizen.png")

    # 3) Units
    unit_specs = {
        "trade_caravan.png": generate_trade_caravan,
        "military_unit.png": lambda: paint_humanoid_unit((70, 75, 85), (55, 65, 50), (180, 160, 60), True, False),
        "military_officer.png": lambda: paint_humanoid_unit((50, 55, 70), (40, 50, 90), (220, 180, 60), True, True),
        "military_guard.png": lambda: paint_humanoid_unit((90, 90, 95), (70, 70, 75), (160, 160, 170), True, False),
        "military_elite.png": lambda: paint_humanoid_unit((40, 40, 48), (90, 30, 35), (220, 190, 70), True, True),
        "ship.png": lambda: generate_ship("ship"),
        "ship_cargo.png": lambda: generate_ship("ship_cargo"),
        "ship_patrol.png": lambda: generate_ship("ship_patrol"),
        "ship_war.png": lambda: generate_ship("ship_war"),
        "ship_landing.png": lambda: generate_ship("ship_landing"),
        "aircraft.png": lambda: generate_aircraft("aircraft"),
        "aircraft_patrol.png": lambda: generate_aircraft("aircraft_patrol"),
        "aircraft_transport.png": lambda: generate_aircraft("aircraft_transport"),
        "bounty_hunter.png": generate_bounty_hunter,
    }
    for name, fn in unit_specs.items():
        path = ENTITY / name
        save_png(fn(), path)
        created.append(path)

    # 4) Wildlife morphology families
    families = [
        "bear", "bird", "canid", "cetacean", "crocodilian", "fish",
        "large_mammal", "primate", "raptor", "ungulate",
        "pinniped", "predator_quadruped", "small_quadruped",
    ]
    for fam in families:
        path = ENTITY / f"wildlife_{fam}.png"
        save_png(generate_wildlife_family(fam), path)
        created.append(path)
    save_png(generate_wildlife_family("ungulate"), ENTITY / "wildlife.png")
    created.append(ENTITY / "wildlife.png")

    species = load_species()
    for idx, sp in enumerate(species):
        path = ENTITY / f"wildlife_species_{sp['id']}.png"
        save_png(generate_species_texture(sp, idx), path)
        created.append(path)

    # 5) Heraldry
    for i in range(16):
        path = HERALDRY / f"faction_banner_{i}.png"
        save_png(generate_banner(i), path)
        created.append(path)

    # 6) Siege
    for kind in ("siege_ram", "siege_ladder", "siege_artillery"):
        path = ENTITY / f"{kind}.png"
        save_png(generate_siege(kind), path)
        created.append(path)

    # 7) UI
    gui_files = {
        "dashboard_panel.png": generate_dashboard_panel,
        "map_frame.png": generate_map_frame,
        "icon_food.png": lambda: generate_icon("icon_food", 16),
        "icon_war.png": lambda: generate_icon("icon_war", 16),
        "icon_trade.png": lambda: generate_icon("icon_trade", 16),
        "icon_crown.png": lambda: generate_icon("icon_crown", 16),
    }
    for name, fn in gui_files.items():
        path = GUI / name
        save_png(fn(), path)
        created.append(path)

    # Verification summary
    citizens = sorted(ENTITY.glob("faction_citizen_*.png"))
    citizen_base = [p for p in citizens if "_overlay" not in p.name and p.name.replace("faction_citizen_", "").replace(".png", "").isdigit()]
    species_tex = sorted(ENTITY.glob("wildlife_species_*.png"))
    banners = sorted(HERALDRY.glob("faction_banner_*.png"))
    bad = [p for p in created if not verify_png(p)]

    print(f"created={len(created)}")
    print(f"citizens={len(citizen_base)}")
    print(f"citizen_overlays={len(list(ENTITY.glob('faction_citizen_*_overlay.png')))}")
    print(f"species_textures={len(species_tex)}")
    print(f"heraldry={len(banners)}")
    print(f"png_header_ok={len(created) - len(bad)}/{len(created)}")
    if bad:
        print("BAD_PNG:", ", ".join(str(p) for p in bad[:10]))
        raise SystemExit(1)
    if len(citizen_base) != 48:
        raise SystemExit(f"expected 48 citizens, got {len(citizen_base)}")
    if len(species_tex) < 130:
        raise SystemExit(f"expected ~134 species textures, got {len(species_tex)}")
    if len(banners) != 16:
        raise SystemExit(f"expected 16 banners, got {len(banners)}")
    print("OK")


if __name__ == "__main__":
    main()
