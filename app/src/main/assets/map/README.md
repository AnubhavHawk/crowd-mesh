# Offline map assets

`offline_style.json` is a **placeholder** MapLibre style: a flat background
with no basemap tile source, so the app has something valid to load with
zero network access and zero bundled tile data. `MapScreen`/`MapViewModel`
add the crowd heatmap `GeoJSON` source and layers to this style at runtime
(see `presentation/map/HeatmapLayerBuilder.kt`) — that part works fully
offline out of the box.

## Adding a real offline basemap

A real basemap needs actual map tiles bundled on-device (there is no
internet call, ever). Typical city/region extracts are 50MB-500MB+, far too
large to generate as source text here. To wire one up:

1. Produce a `.mbtiles` file for your region, e.g. with
   [`tilemaker`](https://github.com/systemed/tilemaker) or
   [MapTiler Desktop](https://www.maptiler.com/desktop/) from OpenStreetMap
   extracts, or download a pre-built extract for your area.
2. Drop it in `app/src/main/assets/map/region.mbtiles`.
3. Point `MapScreen` at an `mbtiles://` asset URI (MapLibre Native Android
   supports reading local `.mbtiles` via its offline manager /
   `asset://` + a local tile server shim) or pre-convert to a bundled
   vector-tile directory and reference it as a `sources.<id>.tiles` template
   using a `file://`/`asset://` URL instead of `offline_style.json`'s empty
   `sources: {}`.
4. Update `MapConstants.STYLE_ASSET_PATH` in
   `presentation/map/MapScreen.kt` if you rename the style file.

None of this affects the mesh/gossip/heatmap logic — heatmap data is
computed entirely from local `PresenceRecord`s and rendered as a `GeoJSON`
overlay regardless of which basemap style is loaded underneath it.
