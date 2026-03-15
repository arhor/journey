package com.github.arhor.journey.core.common

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
    fun `map should preserve failure when output is failure`() {
        // Given
        val output: Output<Int, TestError> = Output.Failure(TestError("boom"))
        var transformCalled = false

        // When
        val result = output.map {
            transformCalled = true
            it * 2
        }

        // Then
        result shouldBe output
        transformCalled shouldBe false
    }

    @Test
    fun `flatMap should transform value when output is success`() {
        // Given
        val output: Output<Int, TestError> = Output.Success(4)

        // When
        val result = output.flatMap { Output.Success(it * 2) }

        // Then
        result shouldBe Output.Success(8)
    }

    @Test
    fun `flatMap should preserve failure when output is failure`() {
        // Given
        val output: Output<Int, TestError> = Output.Failure(TestError("boom"))
        var transformCalled = false

        // When
        val result = output.flatMap {
            transformCalled = true
            Output.Success(it * 2)
        }

        // Then
        result shouldBe output
        transformCalled shouldBe false
    }

    @Test
    fun `fold should return success branch result when output is success`() {
        // Given
        val output: Output<Int, TestError> = Output.Success(7)

        // When
        val result = output.fold(
            onSuccess = { "value:$it" },
            onFailure = { "error:${it.message}" },
        )

        // Then
        result shouldBe "value:7"
    }

    @Test
    fun `fold should return failure branch result when output is failure`() {
        // Given
        val output: Output<Int, TestError> = Output.Failure(TestError("boom"))

        // When
        val result = output.fold(
            onSuccess = { "value:$it" },
            onFailure = { "error:${it.message}" },
        )

        // Then
        result shouldBe "error:boom"
    }

    @Test
    fun `onSuccess should invoke block when output is success`() {
        // Given
        val output: Output<Int, TestError> = Output.Success(9)
        var captured = 0

        // When
        val result = output.onSuccess { captured = it }

        // Then
        result shouldBe output
        captured shouldBe 9
    }

    @Test
    fun `onSuccess should not invoke block when output is failure`() {
        // Given
        val output: Output<Int, TestError> = Output.Failure(TestError("boom"))
        var invoked = false

        // When
        val result = output.onSuccess { invoked = true }

        // Then
        result shouldBe output
        invoked shouldBe false
    }

    @Test
    fun `onFailure should invoke block when output is failure`() {
        // Given
        val output: Output<Int, TestError> = Output.Failure(TestError("boom"))
        var captured: TestError? = null

        // When
        val result = output.onFailure { captured = it }

        // Then
        result shouldBe output
        captured shouldBe TestError("boom")
    }

    @Test
    fun `onFailure should not invoke block when output is success`() {
        // Given
        val output: Output<Int, TestError> = Output.Success(9)
        var invoked = false

        // When
        val result = output.onFailure { invoked = true }

        // Then
        result shouldBe output
        invoked shouldBe false
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
    fun `recover should preserve success when output is success`() {
        // Given
        val output: Output<Int, TestError> = Output.Success(7)

        // When
        val result = output.recover { 42 }

        // Then
        result shouldBe output
    }

    @Test
    fun `combine should transform values when both outputs are success`() {
        // Given
        val left: Output<Int, TestError> = Output.Success(2)
        val right: Output<Int, TestError> = Output.Success(3)

        // When
        val result = combine(left, right) { a, b -> a + b }

        // Then
        result shouldBe Output.Success(5)
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
    fun `combine should return right failure when second output is failure`() {
        // Given
        val left: Output<Int, TestError> = Output.Success(1)
        val right: Output<Int, TestError> = Output.Failure(TestError("right"))

        // When
        val result = combine(left, right)

        // Then
        result shouldBe right
    }

    @Test
    fun `combine should return pair when both outputs are success`() {
        // Given
        val left: Output<Int, TestError> = Output.Success(1)
        val right: Output<String, TestError> = Output.Success("two")

        // When
        val result = combine(left, right)

        // Then
        result shouldBe Output.Success(1 to "two")
    }

    @Test
    fun `combine should support different error subtypes with common supertype`() {
        // Given
        val left: Output<Int, LeftError> = Output.Failure(LeftError("left"))
        val right: Output<Int, RightError> = Output.Failure(RightError("right"))

        // When
        val result: Output<Int, CombinedError> = combine(left, right) { a, b -> a + b }

        // Then
        result shouldBe Output.Failure(LeftError("left"))
    }

    @Test
    fun `combine should return second subtype failure when first subtype is success`() {
        // Given
        val left: Output<Int, LeftError> = Output.Success(1)
        val right: Output<Int, RightError> = Output.Failure(RightError("right"))

        // When
        val result: Output<Pair<Int, Int>, CombinedError> = combine(left, right)

        // Then
        result shouldBe Output.Failure(RightError("right"))
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

    @Test
    fun `toOutputFlow should emit mapped values when upstream flow succeeds`() = runTest {
        // Given
        val upstream = flowOf(1, 2, 3)

        // When
        val results = upstream.toOutputFlow(
            onSuccess = { "value:$it" },
            onFailure = { throwable -> TestError(throwable.message ?: "unknown") },
        ).toList()

        // Then
        results shouldBe listOf(
            Output.Success("value:1"),
            Output.Success("value:2"),
            Output.Success("value:3"),
        )
    }

    @Test
    fun `toOutputFlow should emit failure when success mapping throws exception`() = runTest {
        // Given
        val upstream = flowOf(1, 2)

        // When
        val results = upstream.toOutputFlow(
            onSuccess = {
                if (it == 2) error("mapping failed")
                "value:$it"
            },
            onFailure = { throwable -> TestError(throwable.message ?: "unknown") },
        ).toList()

        // Then
        results[0] shouldBe Output.Success("value:1")
        val failure = results[1] as Output.Failure<TestError>
        failure.error.message shouldBe "mapping failed"
    }

    @Test
    fun `combine flow should emit transformed success when both flows succeed`() = runTest {
        // Given
        val flow1 = flowOf(Output.Success(2))
        val flow2 = flowOf(Output.Success(3))

        // When
        val results = combine(flow1, flow2) { a, b -> a + b }.toList()

        // Then
        results shouldBe listOf(Output.Success(5))
    }

    @Test
    fun `combine flow should emit left failure when first flow emits failure`() = runTest {
        // Given
        val leftFailure: Output<Int, TestError> = Output.Failure(TestError("left"))
        val flow1 = flowOf(leftFailure)
        val flow2 = flowOf(Output.Success(3))

        // When
        val results = combine(flow1, flow2).toList()

        // Then
        results shouldBe listOf(leftFailure)
    }

    @Test
    fun `combine flow should emit right failure when second flow emits failure`() = runTest {
        // Given
        val rightFailure: Output<Int, TestError> = Output.Failure(TestError("right"))
        val flow1 = flowOf(Output.Success(2))
        val flow2 = flowOf(rightFailure)

        // When
        val results = combine(flow1, flow2).toList()

        // Then
        results shouldBe listOf(rightFailure)
    }

    @Test
    fun `combine flow should support different error subtypes with common supertype`() = runTest {
        // Given
        val flow1 = flowOf<Output<Int, LeftError>>(Output.Success(2))
        val flow2 = flowOf<Output<Int, RightError>>(Output.Failure(RightError("right")))

        // When
        val results: List<Output<Pair<Int, Int>, CombinedError>> = combine(flow1, flow2).toList()

        // Then
        results shouldBe listOf(Output.Failure(RightError("right")))
    }

    @Test
    fun `asFailure should preserve throwable as cause and message`() {
        // Given
        val exception = IllegalStateException("boom")

        // When
        val output: Output<Int, DomainError> = exception.asFailure()

        // Then
        val failure = output as Output.Failure<DomainError>
        failure.error.message shouldBe "boom"
        failure.error.cause shouldBe exception
    }

    @Test
    fun `asFailure should keep null message when throwable has null message`() {
        // Given
        val exception = Throwable()

        // When
        val output: Output<Int, DomainError> = exception.asFailure()

        // Then
        val failure = output as Output.Failure<DomainError>
        failure.error.message shouldBe null
        failure.error.cause shouldBe exception
    }

    @Test
    fun `domain error should expose null defaults when properties are not overridden`() {
        // Given
        val error = object : DomainError {}

        // When
        val message = error.message
        val cause = error.cause

        // Then
        message shouldBe null
        cause shouldBe null
    }

    private data class TestError(
        override val message: String,
    ) : DomainError

    private sealed interface CombinedError : DomainError

    private data class LeftError(
        override val message: String,
    ) : CombinedError

    private data class RightError(
        override val message: String,
    ) : CombinedError
}
