package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.healthconnect.HealthConnectDataSource
import com.github.arhor.journey.domain.model.HealthConnectAvailability
import com.github.arhor.journey.domain.model.HealthDataSyncFailure
import com.github.arhor.journey.domain.model.HealthDataSyncPayload
import com.github.arhor.journey.domain.model.HealthDataSyncRequest
import com.github.arhor.journey.domain.model.HealthDataSyncResult
import com.github.arhor.journey.domain.model.HealthDataSyncSummary
import com.github.arhor.journey.domain.model.HealthDataTimeRange
import com.github.arhor.journey.domain.model.HealthDataType
import com.github.arhor.journey.domain.model.ImportedHealthEntry
import com.github.arhor.journey.domain.model.RecordedActivity
import com.github.arhor.journey.domain.repository.HealthConnectAvailabilityRepository
import com.github.arhor.journey.domain.repository.HealthDataSyncRepository
import com.github.arhor.journey.domain.usecase.ImportActivitiesUseCase
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthDataSyncRepositoryImpl @Inject constructor(
    private val availabilityRepository: HealthConnectAvailabilityRepository,
    private val dataSource: HealthConnectDataSource,
    private val importActivities: ImportActivitiesUseCase,
) : HealthDataSyncRepository {

    override suspend fun syncHealthData(request: HealthDataSyncRequest): HealthDataSyncResult {
        return try {
            HealthDataSyncResult.Success(
                readHealthData(
                    timeRange = request.timeRange,
                    selectedDataTypes = request.selectedDataTypes,
                ),
            )
        } catch (e: SecurityException) {
            HealthDataSyncResult.Failure(HealthDataSyncFailure.PermissionMissing)
        } catch (e: IllegalStateException) {
            HealthDataSyncResult.Failure(HealthDataSyncFailure.UnavailableProvider)
        } catch (e: Throwable) {
            HealthDataSyncResult.Failure(HealthDataSyncFailure.TransientError(e.message, e))
        }
    }

    override suspend fun readHealthData(
        timeRange: HealthDataTimeRange,
        selectedDataTypes: Set<HealthDataType>,
    ): HealthDataSyncPayload {
        check(availabilityRepository.checkAvailability() == HealthConnectAvailability.AVAILABLE) {
            "Health Connect provider is unavailable."
        }

        val importedEntries = mutableListOf<ImportedHealthEntry>()
        val importedActivities = mutableListOf<RecordedActivity>()

        if (HealthDataType.SESSIONS in selectedDataTypes) {
            var cursor = timeRange.startTime
            while (cursor.isBefore(timeRange.endTime)) {
                val windowEnd = minOf(cursor.plus(MAX_SYNC_WINDOW), timeRange.endTime)
                val window = HealthDataTimeRange(startTime = cursor, endTime = windowEnd)
                val sessions = dataSource.readExerciseSessions(window)
                importedActivities += sessions
                importedEntries += sessions.map { session ->
                    ImportedHealthEntry(
                        sourceId = session.importMetadata?.externalRecordId.orEmpty(),
                        type = HealthDataType.SESSIONS,
                        startTime = session.startedAt,
                        endTime = session.startedAt.plus(session.duration),
                        value = session.duration.seconds,
                    )
                }
                cursor = windowEnd
            }
        }

        if (importedActivities.isNotEmpty()) {
            importActivities(records = importedActivities)
        }

        return HealthDataSyncPayload(
            summary = HealthDataSyncSummary(
                importedEntriesCount = importedEntries.size,
                importedStepsCount = importedActivities.sumOf { it.steps?.toLong() ?: 0L },
                importedSessionsCount = importedActivities.size,
                importedSessionDuration = importedActivities.fold(Duration.ZERO) { acc, item -> acc.plus(item.duration) },
            ),
            importedEntries = importedEntries,
        )
    }

    private companion object {
        val MAX_SYNC_WINDOW: Duration = Duration.ofHours(6)
    }
}
