package com.github.arhor.journey.data.local.db

import androidx.room.withTransaction
import com.github.arhor.journey.domain.TransactionRunner
import javax.inject.Inject

class RoomTransactionRunner @Inject constructor(
    private val db: JourneyDatabase,
) : TransactionRunner {

    override suspend fun <T> runInTransaction(block: suspend () -> T): T =
        db.withTransaction { block() }
}

