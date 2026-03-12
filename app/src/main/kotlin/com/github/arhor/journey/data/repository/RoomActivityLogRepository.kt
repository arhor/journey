package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.ActivityLogDao
import com.github.arhor.journey.data.local.db.entity.ActivityLogEntity
import com.github.arhor.journey.data.mapper.toDomain
import com.github.arhor.journey.data.mapper.toEntity
import com.github.arhor.journey.domain.activity.model.ActivityLogEntry
import com.github.arhor.journey.domain.activity.model.ActivitySource
import com.github.arhor.journey.domain.activity.model.RecordedActivity
import com.github.arhor.journey.domain.player.model.Reward
import com.github.arhor.journey.domain.activity.model.ActivityLogInsertResult
import com.github.arhor.journey.domain.activity.repository.ActivityLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomActivityLogRepository @Inject constructor(
    private val dao: ActivityLogDao,
) : ActivityLogRepository {

    override fun observeHistory(): Flow<List<ActivityLogEntry>> =
        dao.observeRecent()
            .map { items -> items.map { it.toDomain() } }

    override suspend fun insert(
        recorded: RecordedActivity,
        reward: Reward,
    ): ActivityLogInsertResult {
        val importMetadata = recorded.importMetadata

        if (importMetadata != null) {
            val duplicate = dao.findByImportIdentity(
                externalRecordId = importMetadata.externalRecordId,
                originPackageName = importMetadata.originPackageName,
                timeBoundsHash = importMetadata.timeBoundsHash,
            )

            if (duplicate != null) {
                return ActivityLogInsertResult(
                    logEntryId = duplicate.id,
                    shouldApplyReward = false,
                )
            }

            val incomingEntity = recorded.toEntity(reward)
            val incomingEndMs = incomingEntity.startedAtMs + (incomingEntity.durationSeconds * 1000L)
            val overlaps = dao.findOverlappingBySource(
                source = ActivitySource.IMPORTED.name,
                startedAtMs = incomingEntity.startedAtMs,
                endedAtMs = incomingEndMs,
            )

            val preferredOverIncoming = overlaps.firstOrNull { existing ->
                compareByTrustAndQuality(existing, incomingEntity) >= 0
            }
            if (preferredOverIncoming != null) {
                return ActivityLogInsertResult(
                    logEntryId = preferredOverIncoming.id,
                    shouldApplyReward = false,
                )
            }

            if (overlaps.isNotEmpty()) {
                overlaps.forEach { dao.deleteById(it.id) }
                val retainedXp = overlaps.maxOf { it.rewardXp }
                val insertedId = dao.insert(incomingEntity.copy(rewardXp = retainedXp))
                return ActivityLogInsertResult(
                    logEntryId = insertedId,
                    shouldApplyReward = false,
                )
            }
        }

        val logEntryId = dao.insert(recorded.toEntity(reward))
        return ActivityLogInsertResult(logEntryId = logEntryId, shouldApplyReward = true)
    }

    private fun compareByTrustAndQuality(existing: ActivityLogEntity, incoming: ActivityLogEntity): Int {
        val existingTrusted = isTrustedSource(existing)
        val incomingTrusted = isTrustedSource(incoming)

        if (existingTrusted != incomingTrusted) {
            return if (existingTrusted) 1 else -1
        }

        return qualityScore(existing).compareTo(qualityScore(incoming))
    }

    private fun isTrustedSource(entity: ActivityLogEntity): Boolean =
        entity.originPackageName in TRUSTED_IMPORT_PACKAGES

    private fun qualityScore(entity: ActivityLogEntity): Int {
        var score = 0
        if (entity.steps != null && entity.steps > 0) score += 2
        if (entity.distanceMeters != null && entity.distanceMeters > 0) score += 2
        if (entity.note.isNullOrBlank().not()) score += 1
        if (entity.durationSeconds > 0) score += 1
        return score
    }

    private companion object {
        val TRUSTED_IMPORT_PACKAGES = setOf("com.google.android.apps.healthdata")
    }
}
