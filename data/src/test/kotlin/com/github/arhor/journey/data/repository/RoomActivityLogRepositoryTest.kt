package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.ActivityLogDao
import com.github.arhor.journey.data.local.db.entity.ActivityLogEntity
import com.github.arhor.journey.domain.model.ActivitySource
import com.github.arhor.journey.domain.model.ActivityType
import com.github.arhor.journey.domain.model.ImportedActivityMetadata
import com.github.arhor.journey.domain.model.RecordedActivity
import com.github.arhor.journey.domain.model.Reward
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RoomActivityLogRepositoryTest {

    @Test
    fun `observeHistory should map dao entities when recent activities are emitted`() = runTest {
        // Given
        val dao = FakeActivityLogDao(
            recentFlow = flowOf(
                listOf(
                    activityLogEntity(
                        id = 7L,
                        type = "UNKNOWN",
                        source = "BROKEN",
                        rewardXp = 45L,
                        rewardEnergyDelta = 2,
                    ),
                ),
            ),
        )
        val subject = RoomActivityLogRepository(dao = dao)

        // When
        val actual = subject.observeHistory().first()

        // Then
        actual shouldHaveSize 1
        actual.first().id shouldBe 7L
        actual.first().recorded.type shouldBe ActivityType.WALK
        actual.first().recorded.source shouldBe ActivitySource.MANUAL
        actual.first().reward.xp shouldBe 45L
    }

    @Test
    fun `insert should skip insert when imported activity matches existing import identity`() = runTest {
        // Given
        val dao = FakeActivityLogDao()
        dao.duplicateByImportIdentity = activityLogEntity(id = 99L)
        val subject = RoomActivityLogRepository(dao = dao)

        // When
        val actual = subject.insert(
            recorded = importedActivity(
                originPackageName = "com.vendor.import",
            ),
            reward = Reward(xp = 20L),
        )

        // Then
        actual.logEntryId shouldBe 99L
        actual.shouldApplyReward shouldBe false
        dao.insertedEntities.shouldBeEmpty()
        dao.findOverlappingCalls shouldBe 0
    }

    @Test
    fun `insert should keep preferred overlap when existing import comes from trusted source`() = runTest {
        // Given
        val dao = FakeActivityLogDao()
        dao.overlapsBySource = listOf(
            activityLogEntity(
                id = 42L,
                source = ActivitySource.IMPORTED.name,
                originPackageName = "com.google.android.apps.healthdata",
            ),
        )
        val subject = RoomActivityLogRepository(dao = dao)

        // When
        val actual = subject.insert(
            recorded = importedActivity(
                originPackageName = "com.other.health",
            ),
            reward = Reward(xp = 30L),
        )

        // Then
        actual.logEntryId shouldBe 42L
        actual.shouldApplyReward shouldBe false
        dao.deletedIds.shouldBeEmpty()
        dao.insertedEntities.shouldBeEmpty()
    }

    @Test
    fun `insert should replace overlaps and keep max xp when incoming import is preferred`() = runTest {
        // Given
        val dao = FakeActivityLogDao()
        dao.overlapsBySource = listOf(
            activityLogEntity(
                id = 11L,
                source = ActivitySource.IMPORTED.name,
                originPackageName = "com.untrusted.one",
                rewardXp = 80L,
            ),
            activityLogEntity(
                id = 12L,
                source = ActivitySource.IMPORTED.name,
                originPackageName = "com.untrusted.two",
                rewardXp = 120L,
            ),
        )
        dao.insertResult = 500L
        val subject = RoomActivityLogRepository(dao = dao)

        // When
        val actual = subject.insert(
            recorded = importedActivity(
                originPackageName = "com.google.android.apps.healthdata",
            ),
            reward = Reward(xp = 10L),
        )

        // Then
        actual.logEntryId shouldBe 500L
        actual.shouldApplyReward shouldBe false
        dao.deletedIds shouldContainExactly listOf(11L, 12L)
        dao.insertedEntities shouldHaveSize 1
        dao.insertedEntities.single().rewardXp shouldBe 120L
    }

    @Test
    fun `insert should persist import and apply reward when no duplicate and overlap exist`() = runTest {
        // Given
        val dao = FakeActivityLogDao()
        dao.insertResult = 77L
        val subject = RoomActivityLogRepository(dao = dao)

        // When
        val actual = subject.insert(
            recorded = importedActivity(
                originPackageName = "com.vendor.import",
            ),
            reward = Reward(xp = 25L, energyDelta = 1),
        )

        // Then
        actual.logEntryId shouldBe 77L
        actual.shouldApplyReward shouldBe true
        dao.insertedEntities shouldHaveSize 1
        dao.insertedEntities.single().rewardXp shouldBe 25L
        dao.insertedEntities.single().originPackageName shouldBe "com.vendor.import"
    }

    private fun importedActivity(
        originPackageName: String,
    ): RecordedActivity =
        RecordedActivity(
            type = ActivityType.RUN,
            source = ActivitySource.IMPORTED,
            startedAt = Instant.parse("2026-02-10T10:00:00Z"),
            duration = Duration.ofMinutes(20),
            distanceMeters = 3_000,
            steps = 3_500,
            note = "Imported record",
            importMetadata = ImportedActivityMetadata(
                externalRecordId = "external-1",
                originPackageName = originPackageName,
                timeBoundsHash = "hash-1",
            ),
        )

    private fun activityLogEntity(
        id: Long = 0L,
        type: String = ActivityType.WALK.name,
        source: String = ActivitySource.IMPORTED.name,
        startedAtMs: Long = Instant.parse("2026-02-10T10:00:00Z").toEpochMilli(),
        durationSeconds: Long = Duration.ofMinutes(15).seconds,
        distanceMeters: Int? = 1_500,
        steps: Int? = 2_000,
        note: String? = "Existing record",
        rewardXp: Long = 40L,
        rewardEnergyDelta: Int = 0,
        externalRecordId: String? = "external-1",
        originPackageName: String? = "com.vendor.import",
        timeBoundsHash: String? = "hash-1",
    ): ActivityLogEntity =
        ActivityLogEntity(
            id = id,
            type = type,
            source = source,
            startedAtMs = startedAtMs,
            durationSeconds = durationSeconds,
            distanceMeters = distanceMeters,
            steps = steps,
            note = note,
            rewardXp = rewardXp,
            rewardEnergyDelta = rewardEnergyDelta,
            externalRecordId = externalRecordId,
            originPackageName = originPackageName,
            timeBoundsHash = timeBoundsHash,
        )

    private class FakeActivityLogDao(
        private val recentFlow: Flow<List<ActivityLogEntity>> = flowOf(emptyList()),
    ) : ActivityLogDao {
        var duplicateByImportIdentity: ActivityLogEntity? = null
        var overlapsBySource: List<ActivityLogEntity> = emptyList()
        var insertResult: Long = 1L

        var findByImportIdentityCalls: Int = 0
        var findOverlappingCalls: Int = 0

        val deletedIds = mutableListOf<Long>()
        val insertedEntities = mutableListOf<ActivityLogEntity>()

        override fun observeRecent(): Flow<List<ActivityLogEntity>> = recentFlow

        override suspend fun findByImportIdentity(
            externalRecordId: String,
            originPackageName: String,
            timeBoundsHash: String,
        ): ActivityLogEntity? {
            findByImportIdentityCalls += 1
            return duplicateByImportIdentity
        }

        override suspend fun findOverlappingBySource(
            source: String,
            startedAtMs: Long,
            endedAtMs: Long,
        ): List<ActivityLogEntity> {
            findOverlappingCalls += 1
            return overlapsBySource
        }

        override suspend fun deleteById(id: Long) {
            deletedIds += id
        }

        override suspend fun insert(entity: ActivityLogEntity): Long {
            insertedEntities += entity
            return insertResult
        }
    }
}
