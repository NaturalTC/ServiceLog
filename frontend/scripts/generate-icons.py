"""Generates ServiceLog's app icons into public/icons/.

Run from frontend/:  python3 scripts/generate-icons.py   (needs Pillow)

The icon is a white clipboard with a check mark on the app's blue. "Maskable" versions keep
the mark inside the centre 80% so Android's circle/squircle masks never crop it.
"""
from pathlib import Path

from PIL import Image, ImageDraw

BLUE = (11, 87, 208)  # --accent in index.css
WHITE = (255, 255, 255)
OUT = Path(__file__).resolve().parent.parent / "public" / "icons"


def draw_mark(draw: ImageDraw.ImageDraw, size: int, scale: float) -> None:
    """A clipboard with a check mark: 'service log'. Drawn on a 100x100 grid, then scaled."""
    inset = size * (1 - scale) / 2
    unit = size * scale / 100

    def p(x: float, y: float) -> tuple[float, float]:
        return inset + x * unit, inset + y * unit

    # Clipboard body: wider than a battery, with square-ish corners
    draw.rounded_rectangle([p(18, 18), p(82, 90)], radius=6 * unit, outline=WHITE, width=round(6 * unit))
    # Clip straddling the top edge, with a hole like a real clipboard clip
    draw.rounded_rectangle([p(34, 9), p(66, 27)], radius=5 * unit, fill=WHITE)
    hole = 4 * unit
    cx, cy = p(50, 16)
    draw.ellipse([cx - hole, cy - hole, cx + hole, cy + hole], fill=BLUE)
    # Check mark
    draw.line([p(33, 57), p(45, 69), p(67, 44)], fill=WHITE, width=round(8 * unit), joint="curve")


def icon(size: int, maskable: bool) -> Image.Image:
    # Draw at 4x and scale down for smooth edges
    big = size * 4
    image = Image.new("RGB", (big, big), BLUE)
    draw = ImageDraw.Draw(image)
    draw_mark(draw, big, scale=0.6 if maskable else 0.8)
    return image.resize((size, size), Image.LANCZOS)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    targets = {
        "icon-192.png": (192, False),
        "icon-512.png": (512, False),
        "icon-maskable-512.png": (512, True),
        "apple-touch-icon.png": (180, True),  # iOS rounds the corners itself and adds no padding
        "favicon-32.png": (32, False),
    }
    for name, (size, maskable) in targets.items():
        icon(size, maskable).save(OUT / name, optimize=True)
        print(f"wrote {OUT / name}")


if __name__ == "__main__":
    main()
