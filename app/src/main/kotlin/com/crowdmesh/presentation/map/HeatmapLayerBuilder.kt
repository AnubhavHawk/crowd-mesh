package com.crowdmesh.presentation.map

import com.crowdmesh.domain.geohash.GeohashEncoder
import com.crowdmesh.domain.model.DensityLevel
import com.crowdmesh.domain.model.HeatmapCell
import com.crowdmesh.domain.model.PresenceRecord
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Turns locally-aggregated [HeatmapCell]s into a GeoJSON `FeatureCollection`
 * of cell-bound rectangles that MapLibre renders as a choropleth fill layer
 * — a faithful rendering of "geohash cell density", not a Gaussian-blur
 * heatmap. Nothing here ever leaves the device; this is purely local
 * rendering of data already gossiped in via [com.crowdmesh.domain.model.PresenceRecord].
 */
object HeatmapLayerBuilder {

    const val SOURCE_ID = "crowdmesh-heatmap-source"
    const val FILL_LAYER_ID = "crowdmesh-heatmap-fill"

    const val OWN_LOCATION_SOURCE_ID = "crowdmesh-own-location-source"
    const val OWN_LOCATION_LAYER_ID = "crowdmesh-own-location-circle"

    const val PROPERTY_LEVEL = "level"
    const val PROPERTY_COUNT = "count"
    const val PROPERTY_CONFIDENCE = "confidence"

    fun buildGeoJson(cells: List<HeatmapCell>): String {
        val featureCollection = buildJsonObject {
            put("type", "FeatureCollection")
            put(
                "features",
                buildJsonArray {
                    cells.forEach { cell -> add(toFeature(cell)) }
                },
            )
        }
        return featureCollection.toString()
    }

    private fun toFeature(cell: HeatmapCell): JsonObject {
        val bounds = GeohashEncoder.decodeBounds(cell.geohash)
        val ring = buildJsonArray {
            addCoordinate(bounds.minLon, bounds.minLat)
            addCoordinate(bounds.maxLon, bounds.minLat)
            addCoordinate(bounds.maxLon, bounds.maxLat)
            addCoordinate(bounds.minLon, bounds.maxLat)
            addCoordinate(bounds.minLon, bounds.minLat)
        }

        return buildJsonObject {
            put("type", "Feature")
            put(
                "geometry",
                buildJsonObject {
                    put("type", "Polygon")
                    put("coordinates", buildJsonArray { add(ring) })
                },
            )
            put(
                "properties",
                buildJsonObject {
                    put(PROPERTY_LEVEL, cell.level.name)
                    put(PROPERTY_COUNT, cell.userCount)
                    put(PROPERTY_CONFIDENCE, cell.confidence)
                },
            )
        }
    }

    /** A single-point `FeatureCollection` for the device's own presence record, or an empty one if there isn't one yet. */
    fun buildOwnLocationGeoJson(record: PresenceRecord?): String {
        val featureCollection = buildJsonObject {
            put("type", "FeatureCollection")
            put(
                "features",
                buildJsonArray {
                    if (record != null) {
                        val bounds = GeohashEncoder.decodeBounds(record.geohash)
                        add(
                            buildJsonObject {
                                put("type", "Feature")
                                put(
                                    "geometry",
                                    buildJsonObject {
                                        put("type", "Point")
                                        put(
                                            "coordinates",
                                            buildJsonArray {
                                                add(bounds.centerLon)
                                                add(bounds.centerLat)
                                            },
                                        )
                                    },
                                )
                                put("properties", buildJsonObject {})
                            },
                        )
                    }
                },
            )
        }
        return featureCollection.toString()
    }

    private fun JsonArrayBuilder.addCoordinate(lon: Double, lat: Double) {
        add(buildJsonArray {
            add(lon)
            add(lat)
        })
    }

    fun colorHexFor(level: DensityLevel): String = when (level) {
        DensityLevel.GREEN -> "#43A047"
        DensityLevel.YELLOW -> "#FDD835"
        DensityLevel.ORANGE -> "#FB8C00"
        DensityLevel.RED -> "#E53935"
    }
}
