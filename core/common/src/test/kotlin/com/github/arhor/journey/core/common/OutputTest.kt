package com.github.arhor.journey.core.common

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OutputTest {

    @Test
    fun `map should transform value when output is success`() {
        // Given
        val output: Output<Int, TestError> = Output.Success(4)

        // When
        val result = output.map { it * 2 }

        // Then
        result shouldBe Output.Success(8)
    }

    @Test
    fun `recover should return fallback value when output is failure`() {
        // Given
        val output: Output<Int, TestError> = Output.Failure(TestError("boom"))

        // When
        val result = output.recover { 42 }

        // Then
        result shouldBe Output.Success(42)
    }

    @Test
    fun `combine should return left failure when first output is failure`() {
        // Given
        val left: Output<Int, TestError> = Output.Failure(TestError("left"))
        val right: Output<Int, TestError> = Output.Failure(TestError("right"))

        // When
        val result = combine(left, right)

        // Then
        result shouldBe left
    }

    @Test
    fun `toOutputFlow should emit failure when upstream flow throws exception`() = runTest {
        // Given
        val upstream = flow {
            emit(1)
            throw IllegalStateException("failure")
        }

        // When
        val results = upstream.toOutputFlow(
            onSuccess = { "value:$it" },
            onFailure = { throwable -> TestError(throwable.message ?: "unknown") },
        ).toList()

        // Then
        results[0] shouldBe Output.Success("value:1")
        val failure = results[1] as Output.Failure<TestError>
        failure.error.message shouldBe "failure"
    }

    private data class TestError(
        override val message: String,
    ) : DomainError
}
