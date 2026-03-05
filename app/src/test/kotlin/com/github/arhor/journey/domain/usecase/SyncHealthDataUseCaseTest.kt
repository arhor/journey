package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.HealthDataSyncFailure
import com.github.arhor.journey.domain.model.HealthDataSyncMode
import com.github.arhor.journey.domain.model.HealthDataSyncPayload
import com.github.arhor.journey.domain.model.HealthDataSyncRequest
import com.github.arhor.journey.domain.model.HealthDataSyncResult
import com.github.arhor.journey.domain.model.HealthDataSyncSummary
import com.github.arhor.journey.domain.model.HealthDataTimeRange
import com.github.arhor.journey.domain.model.HealthDataType
import com.github.arhor.journey.domain.model.ImportedHealthEntry
import com.github.arhor.journey.domain.repository.HealthDataSyncRepository
import com.github.arhor.journey.domain.repository.HealthPermissionRepository
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Duration
import java.time.Instant

class SyncHealthDataUseCaseTest {

    @Test
    fun `invoke should return permission missing failure when read permission is missing`() = runTest {
        // Given
        val repository = FakeHealthDataSyncRepository()
        val permissionRepository = FakeHealthPermissionRepository(hasPermission = false)
        val useCase = SyncHealthDataUseCase(repository, permissionRepository)

        // When
        val result = useCase(
            timeRange = defaultTimeRange,
            selectedDataTypes = setOf(HealthDataType.STEPS),
            syncMode = HealthDataSyncMode.MANUAL,
        )

        // Then
        result shouldBe SyncHealthDataUseCaseResult.Failure(HealthDataSyncFailure.PermissionMissing)
    }

    @Test
    fun `invoke should return empty data failure when selected data types are empty`() = runTest {
        // Given
        val repository = FakeHealthDataSyncRepository()
        val useCase = SyncHealthDataUseCase(repository)

        // When
        val result = useCase(
            timeRange = defaultTimeRange,
            selectedDataTypes = emptySet(),
            syncMode = HealthDataSyncMode.BACKGROUND,
        )

        // Then
        result shouldBe SyncHealthDataUseCaseResult.Failure(HealthDataSyncFailure.EmptyData)
    }

    @Test
    fun `invoke should return unavailable provider failure when repository reports provider unavailable`() = runTest {
        // Given
        val repository = FakeHealthDataSyncRepository(
            result = HealthDataSyncResult.Failure(HealthDataSyncFailure.UnavailableProvider),
        )
        val useCase = SyncHealthDataUseCase(repository)

        // When
        val result = useCase(
            timeRange = defaultTimeRange,
            selectedDataTypes = setOf(HealthDataType.STEPS, HealthDataType.SESSIONS),
            syncMode = HealthDataSyncMode.MANUAL,
        )

        // Then
        result shouldBe SyncHealthDataUseCaseResult.Failure(HealthDataSyncFailure.UnavailableProvider)
    }

    @Test
    fun `invoke should return synced payload when repository succeeds`() = runTest {
        // Given
        val payload = HealthDataSyncPayload(
            summary = HealthDataSyncSummary(
                importedEntriesCount = 2,
                importedStepsCount = 5000,
                importedSessionsCount = 1,
                importedSessionDuration = Duration.ofMinutes(45),
            ),
            importedEntries = listOf(
                ImportedHealthEntry(
                    sourceId = "steps-1",
                    type = HealthDataType.STEPS,
                    startTime = Instant.parse("2026-01-01T00:00:00Z"),
                    endTime = Instant.parse("2026-01-01T23:59:59Z"),
                    value = 5000,
                ),
                ImportedHealthEntry(
                    sourceId = "session-1",
                    type = HealthDataType.SESSIONS,
                    startTime = Instant.parse("2026-01-01T10:00:00Z"),
                    endTime = Instant.parse("2026-01-01T10:45:00Z"),
                    value = 45,
                ),
            ),
        )
        val repository = FakeHealthDataSyncRepository(result = HealthDataSyncResult.Success(payload))
        val permissionRepository = FakeHealthPermissionRepository(hasPermission = true)
        val useCase = SyncHealthDataUseCase(repository, permissionRepository)

        // When
        val result = useCase(
            timeRange = defaultTimeRange,
            selectedDataTypes = setOf(HealthDataType.STEPS, HealthDataType.SESSIONS),
            syncMode = HealthDataSyncMode.BACKGROUND,
        )

        // Then
        result shouldBe SyncHealthDataUseCaseResult.Success(payload)
    }

    private class FakeHealthDataSyncRepository(
        private val result: HealthDataSyncResult = HealthDataSyncResult.Failure(HealthDataSyncFailure.EmptyData),
    ) : HealthDataSyncRepository {

        override suspend fun syncHealthData(request: HealthDataSyncRequest): HealthDataSyncResult = result

        override suspend fun readHealthData(
            timeRange: HealthDataTimeRange,
            selectedDataTypes: Set<HealthDataType>,
        ): HealthDataSyncPayload {
            return when (result) {
                is HealthDataSyncResult.Success -> result.payload
                is HealthDataSyncResult.Failure -> error("No payload available for failure result")
            }
        }
    }

    private class FakeHealthPermissionRepository(
        private val hasPermission: Boolean,
    ) : HealthPermissionRepository {

        override suspend fun hasReadPermissions(selectedDataTypes: Set<HealthDataType>): Boolean = hasPermission
    }

    private companion object {
        val defaultTimeRange = HealthDataTimeRange(
            startTime = Instant.parse("2026-01-01T00:00:00Z"),
            endTime = Instant.parse("2026-01-02T00:00:00Z"),
        )
    }
}
