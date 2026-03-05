package com.github.arhor.journey.domain.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Represents the lifecycle state of a generic resource.
 *
 * @param T the type of the asset data held when the state is [Success]
 */
sealed interface Resource<out T> {
    /**
     * Represents the state when a resource is currently being loaded or processed.
     */
    data object Loading : Resource<Nothing>

    /**
     * Represents the state when a resource has been successfully loaded and its result
     * is ready for use.
     *
     * @param T the type of the loaded data.
     * @property value the actual value of the resource.
     */
    data class Success<T>(val value: T) : Resource<T>

    /**
     * Represents the state when a resource processing has failed.
     *
     * @property message a human-readable description of the failure.
     * @property error the exception or error that caused the failure.
     */
    data class Failure(val message: String? = null, val error: Throwable? = null) : Resource<Nothing>
}

fun <T> Flow<T>.asResourceFlow(): Flow<Resource<T>> =
    this.map<T, Resource<T>> { Resource.Success(it) }
        .onStart { emit(Resource.Loading) }
        .catch { emit(Resource.Failure(error = it)) }

fun <T, R> Flow<T>.asResourceFlow(transform: suspend (T) -> R): Flow<Resource<R>> =
    this.map(transform)
        .asResourceFlow()
