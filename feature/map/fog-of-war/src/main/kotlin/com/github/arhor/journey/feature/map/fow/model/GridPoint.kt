package com.github.arhor.journey.feature.map.fow.model

import kotlin.math.abs
import kotlin.math.hypot

internal data class GridPoint(
    val x: Double,
    val y: Double,
) {
    fun length(): Double = hypot(x, y)

    fun normalized(epsilon: Double): GridPoint {
        val length = length()
        check(length > epsilon) { "Cannot normalize a zero-length vector." }

        return this * (1.0 / length)
    }

    fun closeTo(other: GridPoint, epsilon: Double): Boolean {
        if (epsilon == 0.0) {
            return this == other
        }
        return abs(x - other.x) <= epsilon
            && abs(y - other.y) <= epsilon
    }

    operator fun plus(other: GridPoint): GridPoint = GridPoint(
        x = x + other.x,
        y = y + other.y,
    )

    operator fun minus(other: GridPoint): GridPoint = GridPoint(
        x = x - other.x,
        y = y - other.y,
    )

    operator fun times(scale: Double): GridPoint = GridPoint(
        x = x * scale,
        y = y * scale,
    )
}
