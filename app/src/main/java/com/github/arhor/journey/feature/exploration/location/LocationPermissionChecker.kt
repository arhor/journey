package com.github.arhor.journey.feature.exploration.location

interface LocationPermissionChecker {
    fun hasAnyLocationPermission(): Boolean

    fun hasFineLocationPermission(): Boolean
}
