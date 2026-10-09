#!/usr/bin/env python3
"""Render Weather Balloon's built-in radar base map.

Draws the lower-48 map once, from public-domain data, at the one zoom the
radar card uses (7), at 2x resolution, and slices it into tiles under
app/src/main/assets/basemap/. Each tile packs three masks into one image:

    R  ink    state/county/country lines, interstates, city dots + labels
    G  halo   a knock-out around labels, so they read over radar colors
    B  water  ocean, lakes, major rivers

The app colors each mask from the current sky palette (see RadarCard.kt), so
one set of tiles serves every theme. Rendering the whole mosaic before
slicing keeps labels from being cut at tile edges.

Sources (all public domain):
  US Census Bureau  cartographic boundaries 2024 (states, counties),
                    2024 Gazetteer places + Vintage 2024 city population
  Natural Earth     10m land, lakes, rivers, roads, admin boundaries

Download into .cache/ and unzip each archive into a same-named folder:
  https://www2.census.gov/geo/tiger/GENZ2024/shp/cb_2024_us_state_5m.zip
  https://www2.census.gov/geo/tiger/GENZ2024/shp/cb_2024_us_county_5m.zip
  https://www2.census.gov/geo/docs/maps-data/data/gazetteer/2024_Gazetteer/2024_Gaz_place_national.zip
  https://www2.census.gov/programs-surveys/popest/datasets/2020-2024/cities/totals/sub-est2024.csv
  https://naciscdn.org/naturalearth/10m/physical/ne_10m_{land,lakes,rivers_lake_centerlines}.zip
  https://naciscdn.org/naturalearth/10m/cultural/ne_10m_{roads,admin_0_boundary_lines_land,admin_1_states_provinces_lines}.zip

Usage:  .venv/bin/python build_basemap.py   (see README.md for setup)
"""
import csv
import math
import re
import shutil
from pathlib import Path

import cairo
import shapefile
from PIL import Image, ImageChops, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
CACHE = HERE / ".cache"
REPO = HERE.parent.parent
OUT = REPO / "app/src/main/assets/basemap"
FONT = REPO / "app/src/main/res/font/space_grotesk.ttf"

ZOOM = 7
SCALE = 2                  # 2x ("retina") tiles: 512 px per zoom-7 tile
TILE = 256 * SCALE
# Tile range covering IemRadar.covers() (lat 24..50, lon -126..-66) plus the
# card viewport's half-size, so every in-coverage view is fully mapped.
X0, X1, Y0, Y1 = 18, 41, 42, 55
W, H = (X1 - X0 + 1) * TILE, (Y1 - Y0 + 1) * TILE

CITY_COUNT = 200
# Fill: smaller towns, placed after every major city and only where they
# have FILL_CLEARANCE px of empty map around them, so rural areas gain names
# without crowding metros.
FILL_MIN_POP = 10_000
FILL_CLEARANCE = 44
CONUS_EXCLUDE = {"02", "15", "72"}   # AK, HI, PR state FIPS

# Mask intensities (0..1) and widths (mosaic px). The app scales ink by its
# own alpha, so these set the relative weight of each kind of line.
STYLE = {
    "county":   (0.16, 0.9),
    "province": (0.30, 1.1),
    "road":     (0.30, 1.3),
    "coast":    (0.30, 1.0),
    "state":    (0.55, 1.6),
    "country":  (0.75, 2.2),
}
# (font px, ink level, dot radius, clearance) per tier.
MAJOR = (21, 235, 3.2, 0)
FILL = (18, 180, 2.4, FILL_CLEARANCE)


# --- projection -------------------------------------------------------------

def project(lon, lat):
    """Lon/lat -> mosaic px (Web Mercator at ZOOM, SCALE)."""
    lat = max(min(lat, 85.0), -85.0)
    world = 256 * (1 << ZOOM) * SCALE
    x = (lon + 180.0) / 360.0 * world
    r = math.radians(lat)
    y = (1 - math.log(math.tan(r) + 1 / math.cos(r)) / math.pi) / 2 * world
    return x - X0 * TILE, y - Y0 * TILE


def lonlat_bounds():
    """Mosaic bounds as lon/lat, for skipping far-away shapes."""
    world = 256 * (1 << ZOOM) * SCALE

    def lon(px):
        return px / world * 360.0 - 180.0

    def lat(py):
        return math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * py / world))))

    return lon(X0 * TILE), lat((Y1 + 1) * TILE), lon((X1 + 1) * TILE), lat(Y0 * TILE)


BOUNDS = lonlat_bounds()


def overlaps(bbox, pad=1.0):
    minx, miny, maxx, maxy = bbox
    return not (maxx < BOUNDS[0] - pad or minx > BOUNDS[2] + pad or
                maxy < BOUNDS[1] - pad or miny > BOUNDS[3] + pad)


def shapes(name, keep=lambda rec: True):
    reader = shapefile.Reader(str(CACHE / name / name))
    for sr in reader.iterShapeRecords():
        shp = sr.shape
        if not shp.points or not overlaps(shp.bbox) or not keep(sr.record):
            continue
        yield shp


def trace(ctx, shp):
    """Add a shape's parts to the cairo path (rings or polylines)."""
    parts = list(shp.parts) + [len(shp.points)]
    for a, b in zip(parts, parts[1:]):
        pts = shp.points[a:b]
        if len(pts) < 2:
            continue
        ctx.move_to(*project(*pts[0]))
        for p in pts[1:]:
            ctx.line_to(*project(*p))


def mask_surface():
    return cairo.ImageSurface(cairo.FORMAT_A8, W, H)


def context(surface):
    ctx = cairo.Context(surface)
    ctx.set_line_join(cairo.LINE_JOIN_ROUND)
    ctx.set_line_cap(cairo.LINE_CAP_ROUND)
    ctx.set_fill_rule(cairo.FILL_RULE_EVEN_ODD)
    return ctx


def stroke(ctx, shp_iter, kind):
    alpha, width = STYLE[kind]
    ctx.set_source_rgba(0, 0, 0, alpha)
    ctx.set_line_width(width)
    for shp in shp_iter:
        trace(ctx, shp)
        ctx.stroke()


def to_image(surface):
    """cairo A8 surface -> PIL 'L' image (A8 rows are 4-byte aligned)."""
    surface.flush()
    return Image.frombuffer("L", (W, H), bytes(surface.get_data()), "raw", "L",
                            surface.get_stride(), 1)


# --- layers -------------------------------------------------------------------

def render_water():
    surf = mask_surface()
    ctx = context(surf)
    ctx.set_source_rgba(0, 0, 0, 1)
    ctx.paint()                                  # all ocean...
    ctx.set_operator(cairo.OPERATOR_CLEAR)
    for shp in shapes("ne_10m_land"):            # ...minus land
        trace(ctx, shp)
        ctx.fill()
    ctx.set_operator(cairo.OPERATOR_OVER)
    ctx.set_source_rgba(0, 0, 0, 1)
    for shp in shapes("ne_10m_lakes", lambda r: r["scalerank"] <= 6):
        trace(ctx, shp)
        ctx.fill()
    ctx.set_line_width(1.4)
    for shp in shapes("ne_10m_rivers_lake_centerlines", lambda r: r["scalerank"] <= 6):
        trace(ctx, shp)
        ctx.stroke()
    return to_image(surf)


def render_lines():
    surf = mask_surface()
    ctx = context(surf)
    # Weakest first, so stronger lines win where they coincide.
    stroke(ctx, shapes("cb_2024_us_county_5m",
                       lambda r: r["STATEFP"] not in CONUS_EXCLUDE), "county")
    stroke(ctx, shapes("ne_10m_admin_1_states_provinces_lines",
                       lambda r: r["ADM0_A3"] in ("CAN", "MEX")), "province")
    stroke(ctx, shapes("ne_10m_roads",
                       lambda r: r["expressway"] == 1 and r["sov_a3"] in ("USA", "CAN", "MEX")), "road")
    stroke(ctx, shapes("ne_10m_land"), "coast")
    stroke(ctx, shapes("ne_10m_lakes", lambda r: r["scalerank"] <= 6), "coast")
    stroke(ctx, shapes("cb_2024_us_state_5m",
                       lambda r: r["STATEFP"] not in CONUS_EXCLUDE), "state")
    stroke(ctx, shapes("ne_10m_admin_0_boundary_lines_land"), "country")
    return to_image(surf)


# --- cities -------------------------------------------------------------------

SUFFIX = re.compile(r"\s+(city|town|village|municipality|borough|urban county|"
                    r"metropolitan government|metro government|unified government|"
                    r"consolidated government)\b.*$")


def clean_name(name):
    """'Nashville-Davidson metropolitan government (balance)' -> 'Nashville'."""
    name = name.replace(" (balance)", "")
    consolidated = SUFFIX.search(name) and not re.search(r"\s(city|town|village)$", name)
    name = SUFFIX.sub("", name)
    if consolidated:
        name = re.split(r"[-/]", name)[0]
    if name.endswith(" City") and name in ("Boise City",):
        name = name[: -len(" City")]
    # "San Buenaventura (Ventura)" -> the name people use.
    common = re.search(r"\(([^)]+)\)$", name)
    if common:
        name = common.group(1)
    # City-county consolidations: "Macon-Bibb County", "Hartsville/Trousdale County".
    if re.search(r"[-/].*County$", name) or name == "Butte-Silver Bow":
        name = re.split(r"[-/]", name)[0]
    name = re.sub(r" metro township$", "", name)
    # Massachusetts city-form towns: "Barnstable Town" -> "Barnstable".
    name = re.sub(r" Town$", "", name)
    return name.strip()


def top_cities():
    coords = {}
    gaz = next((CACHE / "2024_Gaz_place_national").glob("*.txt"))
    with open(gaz, encoding="latin-1") as f:
        for row in csv.DictReader(f, delimiter="\t"):
            row = {k.strip(): v.strip() for k, v in row.items()}
            coords[row["GEOID"]] = (float(row["INTPTLONG"]), float(row["INTPTLAT"]))
    cities = []
    with open(CACHE / "sub-est2024.csv", encoding="latin-1") as f:
        for row in csv.DictReader(f):
            if row["SUMLEV"] != "162" or row["STATE"] in CONUS_EXCLUDE:
                continue
            geoid = row["STATE"] + row["PLACE"]
            if geoid in coords:
                cities.append((int(row["POPESTIMATE2024"]), clean_name(row["NAME"]), coords[geoid]))
    cities.sort(reverse=True)
    return cities[:CITY_COUNT], [c for c in cities[CITY_COUNT:] if c[0] >= FILL_MIN_POP]


def render_labels(tiers):
    """Place labels tier by tier, biggest city first within each tier."""
    ink = Image.new("L", (W, H), 0)
    halo = Image.new("L", (W, H), 0)
    di, dh = ImageDraw.Draw(ink), ImageDraw.Draw(halo)
    placed = []     # boxes already taken (labels and dots), padded

    def free(box, clear=0):
        x0, y0, x1, y1 = box
        if x0 < 2 or y0 < 2 or x1 > W - 2 or y1 > H - 2:
            return False
        x0, y0, x1, y1 = x0 - clear, y0 - clear, x1 + clear, y1 + clear
        return all(x1 < a or x0 > c or y1 < b or y0 > d for a, b, c, d in placed)

    shown = []
    for cities, (px, level, dot_r, clear) in tiers:
        font = ImageFont.truetype(str(FONT), px)
        try:
            font.set_variation_by_axes([500])   # Space Grotesk: medium weight
        except Exception:
            pass
        placed_in_tier = []
        for pop, name, (lon, lat) in cities:
            x, y = project(lon, lat)
            dot = (x - dot_r - 3, y - dot_r - 3, x + dot_r + 3, y + dot_r + 3)
            if not free(dot, clear):
                continue
            l, t, r, b = font.getbbox(name)
            tw, th = r - l, b - t
            gap = dot_r + 5
            spots = [
                (x + gap, y - th / 2 - t),                 # right
                (x - gap - tw, y - th / 2 - t),            # left
                (x - tw / 2, y - gap - th - t),            # above
                (x - tw / 2, y + gap - t),                 # below
            ]
            for tx, ty in spots:
                box = (tx - 4, ty + t - 4, tx + tw + 4, ty + b + 4)
                if free(box, clear):
                    placed += [dot, box]
                    dh.text((tx, ty), name, font=font, fill=255, stroke_width=4, stroke_fill=255)
                    dh.ellipse((x - dot_r - 2.5, y - dot_r - 2.5, x + dot_r + 2.5, y + dot_r + 2.5), fill=255)
                    di.text((tx, ty), name, font=font, fill=level)
                    di.ellipse((x - dot_r, y - dot_r, x + dot_r, y + dot_r), fill=level)
                    placed_in_tier.append(name)
                    break
        shown.append(placed_in_tier)
    return ink, halo, shown


# --- output -------------------------------------------------------------------

def main():
    print(f"mosaic {W}x{H} px, tiles x{X0}..{X1} y{Y0}..{Y1}")
    water = render_water()
    lines = render_lines()
    major, fill = top_cities()
    label_ink, halo, (shown, filled) = render_labels([(major, MAJOR), (fill, FILL)])
    ink = ImageChops.lighter(lines, label_ink)
    # Lines stop at label halos so text never sits on a border.
    ink = ImageChops.composite(label_ink, ink, halo)
    # 16 levels per mask (rounded, not truncated, so hairlines keep their
    # weight): invisible at these line widths, and it halves the tiles.
    levels = [round(v / 17) * 17 for v in range(256)]
    mosaic = Image.merge("RGB", (ink, halo, water)).point(levels * 3)

    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    total = 0
    for ty in range(Y0, Y1 + 1):
        for tx in range(X0, X1 + 1):
            left, top = (tx - X0) * TILE, (ty - Y0) * TILE
            tile = mosaic.crop((left, top, left + TILE, top + TILE))
            path = OUT / f"{tx}_{ty}.webp"
            tile.save(path, "WEBP", lossless=True, method=6)
            total += path.stat().st_size
    n = (X1 - X0 + 1) * (Y1 - Y0 + 1)
    print(f"{n} tiles, {total / 1e6:.2f} MB")
    print(f"labeled {len(shown)}/{len(major)} major cities; unplaced: "
          f"{[c[1] for c in major if c[1] not in shown]}")
    print(f"fill: {len(filled)} of {len(fill)} towns >= {FILL_MIN_POP:,}")
    mosaic.resize((W // 8, H // 8), Image.LANCZOS).save(CACHE / "preview.png")


if __name__ == "__main__":
    main()
