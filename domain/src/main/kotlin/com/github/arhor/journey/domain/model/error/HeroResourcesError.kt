package com.github.arhor.journey.domain.model.error

import com.github.arhor.journey.core.common.DomainError

sealed interface HeroResourcesError : DomainError {

    data class InsufficientAmount(
        val resourceTypeId: String,
        val requestedAmount: Int,
        val availableAmount: Int,
    ) : HeroResourcesError {
        override val message: String =
            "Insufficient $resourceTypeId amount: requested $requestedAmount, available $availableAmount."
    }
}
