package com.crowdmesh.presentation.map

import com.crowdmesh.domain.model.DensityLevel
import com.crowdmesh.domain.model.HeatmapCell
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class HeatmapLayerBuilderTest {

    @Test
    fun `empty cell list produces an empty FeatureCollection`() {
        val json = Json.parseToJsonElement(HeatmapLayerBuilder.buildGeoJson(emptyList())).jsonObject
        assertEquals("FeatureCollection", json["type"]!!.jsonPrimitive.content)
        assertEquals(0, json["features"]!!.jsonArray.size)
    }

    @Test
    fun `each cell becomes a closed polygon feature with its properties`() {
        val cell = HeatmapCell(
            geohash = "u4pruy",
            userCount = 4,
            latestTimestampMillis = 1_700_000_000_000L,
            confidence = 0.8,
            level = DensityLevel.ORANGE,
        )

        val json = Json.parseToJsonElement(HeatmapLayerBuilder.buildGeoJson(listOf(cell))).jsonObject
        val feature = json["features"]!!.jsonArray.single().jsonObject

        assertEquals("Feature", feature["type"]!!.jsonPrimitive.content)

        val geometry = feature["geometry"]!!.jsonObject
        assertEquals("Polygon", geometry["type"]!!.jsonPrimitive.content)
        val ring = geometry["coordinates"]!!.jsonArray.first().jsonArray
        assertEquals(5, ring.size) // closed ring: first point repeated as the last
        assertEquals(ring.first(), ring.last())

        val properties = feature["properties"]!!.jsonObject
        assertEquals("ORANGE", properties[HeatmapLayerBuilder.PROPERTY_LEVEL]!!.jsonPrimitive.content)
        assertEquals(4, properties[HeatmapLayerBuilder.PROPERTY_COUNT]!!.jsonPrimitive.int)
    }
}
