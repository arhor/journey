package com.github.arhor.journey.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.github.arhor.journey.data.local.db.entity.HeroResourceEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface HeroResourceDao {

    @Query(
        """
        SELECT *
        FROM hero_resources
        WHERE heroId = :heroId
            AND amount > 0
        ORDER BY typeId ASC
        """,
    )
    fun observeAll(heroId: String): Flow<List<HeroResourceEntity>>

    @Query(
        """
        SELECT amount
        FROM hero_resources
        WHERE heroId = :heroId
          AND typeId = :typeId
        LIMIT 1
        """,
    )
    fun observeAmount(
        heroId: String,
        typeId: String,
    ): Flow<Int?>

    @Query(
        """
        SELECT amount
        FROM hero_resources
        WHERE heroId = :heroId
            AND typeId = :typeId
        LIMIT 1
        """,
    )
    suspend fun getAmount(
        heroId: String,
        typeId: String,
    ): Int?

    @Query(
        """
        SELECT *
        FROM hero_resources
        WHERE heroId = :heroId
            AND typeId = :typeId
        LIMIT 1
        """,
    )
    suspend fun getById(
        heroId: String,
        typeId: String,
    ): HeroResourceEntity?

    @Upsert
    suspend fun upsert(entity: HeroResourceEntity)

    @Query(
        """
        INSERT INTO hero_resources (
            heroId,
            typeId,
            amount,
            updatedAt
        )
        VALUES (
            :heroId,
            :typeId,
            :amountDelta,
            :updatedAt
        )
        ON CONFLICT(heroId, typeId) DO UPDATE SET
            amount = hero_resources.amount + excluded.amount,
            updatedAt = excluded.updatedAt
        """,
    )
    suspend fun incrementAmount(
        heroId: String,
        typeId: String,
        amountDelta: Int,
        updatedAt: Instant,
    )

    @Query(
        """
        UPDATE hero_resources
        SET amount = amount - :amountDelta,
            updatedAt = :updatedAt
        WHERE heroId = :heroId
            AND typeId = :typeId
            AND amount >= :amountDelta
        """,
    )
    suspend fun decrementAmountIfEnough(
        heroId: String,
        typeId: String,
        amountDelta: Int,
        updatedAt: Instant,
    ): Int
}
