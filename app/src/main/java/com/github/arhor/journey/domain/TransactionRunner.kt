package com.github.arhor.journey.domain

/**
 * Runs a suspend block atomically.
 *
 * The data layer provides the implementation (for example Room transactions).
 */
interface TransactionRunner {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}
