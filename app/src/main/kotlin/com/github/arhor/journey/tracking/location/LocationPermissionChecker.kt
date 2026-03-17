package com.github.arhor.journey.tracking.location

interface LocationPermissionChecker {
    fun hasAnyLocationPermission(): Boolean

    fun hasFineLocationPermission(): Boolean
}
