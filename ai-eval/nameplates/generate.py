#!/usr/bin/env python3
"""Generates the synthetic nameplate photos used to measure "Read nameplate" (S36).

Run from ai-eval/nameplates/:  python3 generate.py   (needs Pillow; macOS system fonts)

Each plate is drawn like a real equipment data plate (brand, labelled model and serial,
electrical and refrigerant ratings), then photographed badly on purpose: at an angle, on a
basement wall, with noise, blur and glare. Two are unreadable on purpose (a blurred plate,
and a wall with no plate) to check the AI says so instead of guessing.

The model and serial numbers are made up. truth.jsonl holds what each photo should read as.
Real nameplates are messier (stamped metal, worn stickers), so these results are a floor
check, not a guarantee: see docs/AI-EVALUATION.md.
"""
import json
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = Path(__file__).parent / "plates"
FONTS = "/System/Library/Fonts/Supplemental/"

PLATES = [
    # file, brand, model, serial, manufactured, readable, style
    ("p01", "Carrier", "24ACC636A003", "2419E21345", "2019-05", True, dict(angle=-4)),
    ("p02", "Trane", "4TTR4036L1000AA", "21342ABCDF", None, True, dict(angle=6, glare=True)),
    ("p03", "Rheem", "XE50T10H45U0", "Q051912345", "2019-05", True, dict(angle=-8, noise=18)),
    ("p04", "Weil-McLain", "WGO-4", "CP8811227", "2016-11", True, dict(angle=3, font="Courier")),
    ("p05", "Navien", "NCB-240E", "7A1B2C3D4E", "2021-02", True, dict(angle=-2, blur=0.8)),
    ("p06", "Mitsubishi", "MXZ-3C24NA", "54A01234", None, True, dict(angle=9, glare=True, noise=10)),
    ("p07", "Burnham", "V8H5", "62731429", "2012-09", True, dict(angle=-6, font="Arial Narrow")),
    ("p08", "Goodman", "GSX140361", "1909123456", "2019-09", True, dict(angle=2, dark=True)),
    ("p09", "Daikin", "RX12AXVJU", "E002345", None, True, dict(angle=-5, noise=22)),
    ("p10", "Lennox", "ML180UH070P36A", "5919K12345", "2019-04", True, dict(angle=7, blur=1.0)),
    ("p11", "Rheem", "RA1636AJ1NA", "W291912345", None, False, dict(angle=4, blur=16)),
    ("p12", None, None, None, None, False, dict(wall_only=True)),
]


def font(name, size):
    for candidate in [f"{FONTS}{name}.ttf", f"{FONTS}{name} Bold.ttf", f"/System/Library/Fonts/{name}.ttc"]:
        try:
            return ImageFont.truetype(candidate, size)
        except OSError:
            continue
    return ImageFont.load_default(size)


def draw_plate(brand, model, serial, made, style):
    w, h = 1100, 700
    plate = Image.new("RGB", (w, h), (196, 199, 204))
    d = ImageDraw.Draw(plate)
    for y in range(h):  # brushed-metal gradient
        shade = 186 + int(18 * (y / h)) + random.randint(-3, 3)
        d.line([(0, y), (w, y)], fill=(shade, shade + 2, shade + 6))
    d.rectangle([8, 8, w - 9, h - 9], outline=(60, 60, 60), width=4)
    name = style.get("font", "Arial")
    d.text((40, 30), brand.upper(), font=font("Arial Bold", 74), fill=(20, 30, 60))
    label, value = font(name, 34), font(name if name != "Arial" else "Arial Bold", 44)
    rows = [("MODEL NO.", model), ("SERIAL NO.", serial)]
    rows += [("VOLTS / PH / HZ", "208/230 / 1 / 60"), ("REFRIGERANT", "R-410A  5 LB 12 OZ")]
    if made:
        rows.append(("MFG DATE", made))
    y = 150
    for lab, val in rows:
        d.text((40, y + 8), lab, font=label, fill=(50, 50, 50))
        d.text((420, y), val, font=value, fill=(10, 10, 10))
        y += 96
    for x in (30, w - 50):  # rivets
        for yy in (30, h - 50):
            d.ellipse([x, yy, x + 20, yy + 20], fill=(150, 150, 150), outline=(90, 90, 90))
    return plate


def photograph(plate, style):
    scene = Image.new("RGB", (1600, 1200), (112, 104, 96) if not style.get("dark") else (52, 48, 44))
    d = ImageDraw.Draw(scene)
    for _ in range(4000):  # concrete-wall speckle
        x, y = random.randrange(1600), random.randrange(1200)
        c = random.randint(70, 140)
        d.point((x, y), fill=(c, c - 6, c - 12))
    if plate is not None:
        rotated = plate.rotate(style.get("angle", 0), expand=True, fillcolor=(0, 0, 0))
        mask = plate.convert("L").point(lambda _: 255).rotate(style.get("angle", 0), expand=True)
        scene.paste(rotated, ((1600 - rotated.width) // 2, (1200 - rotated.height) // 2), mask)
    if style.get("glare"):
        glare = Image.new("L", scene.size, 0)
        ImageDraw.Draw(glare).ellipse([900, 250, 1300, 520], fill=120)
        glare = glare.filter(ImageFilter.GaussianBlur(60))
        scene = Image.composite(Image.new("RGB", scene.size, (255, 255, 255)), scene, glare)
    if style.get("dark"):
        scene = scene.point(lambda v: int(v * 0.7))
    if style.get("noise"):
        n = style["noise"]
        px = scene.load()
        for _ in range(200_000):
            x, y = random.randrange(1600), random.randrange(1200)
            r, g, b = px[x, y]
            k = random.randint(-n, n)
            px[x, y] = (max(0, min(255, r + k)), max(0, min(255, g + k)), max(0, min(255, b + k)))
    if style.get("blur"):
        scene = scene.filter(ImageFilter.GaussianBlur(style["blur"]))
    return scene


def main():
    random.seed(7)
    OUT.mkdir(exist_ok=True)
    truth = []
    for file, brand, model, serial, made, readable, style in PLATES:
        plate = None if style.get("wall_only") else draw_plate(brand, model, serial, made, style)
        photograph(plate, style).save(OUT / f"{file}.jpg", quality=82)
        truth.append({"file": f"{file}.jpg", "readable": readable, "brand": brand if readable else None,
                      "model": model if readable else None, "serial": serial if readable else None,
                      "manufactured": made if readable else None})
    (Path(__file__).parent / "truth.jsonl").write_text("".join(json.dumps(t) + "\n" for t in truth))
    print(f"wrote {len(truth)} plates to {OUT}")


if __name__ == "__main__":
    main()
