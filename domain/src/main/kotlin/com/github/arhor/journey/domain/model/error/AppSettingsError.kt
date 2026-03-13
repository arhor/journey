package com.github.arhor.journey.domain.model.error

import com.github.arhor.journey.core.common.DomainError

interface AppSettingsError : DomainError {

    data class LoadingFailed(
        override val cause: Throwable? = null,
        override val message: String? = null,
    ) : AppSettingsError
}
