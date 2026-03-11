package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.domain.model.HealthConnectAvailability

interface HealthConnectAvailabilityRepository {

    fun checkAvailability(): HealthConnectAvailability
}
