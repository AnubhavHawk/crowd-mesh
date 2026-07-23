package com.crowdmesh.domain.geohash

/**
 * Pure-Kotlin base32 geohash implementation (no Android/location framework
 * dependency, so it's trivially unit-testable). Precision 8 (~19m x 19m
 * cells) is used for the record a user owns; heatmap aggregation truncates
 * to a coarser precision (see [HeatmapAggregator]).
 */
object GeohashEncoder {

    const val DEFAULT_PRECISION = 8

    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    data class Bounds(
        val minLat: Double,
        val minLon: Double,
        val maxLat: Double,
        val maxLon: Double,
    ) {
        val centerLat: Double get() = (minLat + maxLat) / 2.0
        val centerLon: Double get() = (minLon + maxLon) / 2.0
    }

    fun encode(latitude: Double, longitude: Double, precision: Int = DEFAULT_PRECISION): String {
        require(precision in 1..12) { "precision must be between 1 and 12, was $precision" }
        require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }

        var minLat = -90.0
        var maxLat = 90.0
        var minLon = -180.0
        var maxLon = 180.0

        val result = StringBuilder(precision)
        var isEvenBit = true
        var bitIndex = 0
        var charBits = 0

        while (result.length < precision) {
            if (isEvenBit) {
                val mid = (minLon + maxLon) / 2.0
                if (longitude >= mid) {
                    charBits = (charBits shl 1) or 1
                    minLon = mid
                } else {
                    charBits = charBits shl 1
                    maxLon = mid
                }
            } else {
                val mid = (minLat + maxLat) / 2.0
                if (latitude >= mid) {
                    charBits = (charBits shl 1) or 1
                    minLat = mid
                } else {
                    charBits = charBits shl 1
                    maxLat = mid
                }
            }
            isEvenBit = !isEvenBit

            bitIndex++
            if (bitIndex == 5) {
                result.append(BASE32[charBits])
                bitIndex = 0
                charBits = 0
            }
        }

        return result.toString()
    }

    /** Truncates a geohash to a coarser (shorter) prefix used for heatmap-cell aggregation. */
    fun prefix(geohash: String, length: Int): String {
        require(length in 1..geohash.length) {
            "length $length out of range for geohash of length ${geohash.length}"
        }
        return geohash.substring(0, length)
    }

    fun decodeBounds(geohash: String): Bounds {
        require(geohash.isNotEmpty()) { "geohash must not be empty" }

        var minLat = -90.0
        var maxLat = 90.0
        var minLon = -180.0
        var maxLon = 180.0
        var isEvenBit = true

        for (char in geohash) {
            val charValue = BASE32.indexOf(char.lowercaseChar())
            require(charValue >= 0) { "invalid geohash character: $char" }

            for (bit in 4 downTo 0) {
                val bitValue = (charValue shr bit) and 1
                if (isEvenBit) {
                    val mid = (minLon + maxLon) / 2.0
                    if (bitValue == 1) minLon = mid else maxLon = mid
                } else {
                    val mid = (minLat + maxLat) / 2.0
                    if (bitValue == 1) minLat = mid else maxLat = mid
                }
                isEvenBit = !isEvenBit
            }
        }

        return Bounds(minLat = minLat, minLon = minLon, maxLat = maxLat, maxLon = maxLon)
    }
}
