# Built-in radar base map

`build_basemap.py` renders the lower-48 map behind the radar card and slices
it into `app/src/main/assets/basemap/{x}_{y}.webp`: 336 zoom-7 tiles at 2x
(512 px), ~2.0 MB. The tiles are committed; rerun this only to change the map.

Each tile packs three masks (R ink, G label halo, B water) that the app
colors from the sky palette (`MapColors` in `RadarCard.kt`).

## Sources (all public domain)

- US Census Bureau: 2024 cartographic boundary files (states, counties, 1:5M),
  2024 Gazetteer places (city coordinates), Vintage 2024 city population
  estimates (top 200 lower-48 cities, ~170 labeled; then ~600 fill towns of
  10k+ people, only where they have clear space around them)
- Natural Earth 1:10M: land, lakes, rivers, roads (expressways), country and
  Canadian/Mexican province boundaries

Labels use the app's own Space Grotesk (OFL, see `licenses/`).

## Rebuild

```sh
cd tools/basemap
python3 -m venv --system-site-packages .venv   # needs system pycairo + Pillow (WebP)
.venv/bin/pip install pyshp
# download + unzip the sources into .cache/ (URLs in build_basemap.py's docstring)
.venv/bin/python build_basemap.py              # also writes .cache/preview.png
```
