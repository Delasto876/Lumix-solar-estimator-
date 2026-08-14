package com.lumix.estimator.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteDao {
    @Query("SELECT * FROM quotes ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<QuoteEntity>>

    @Query("SELECT * FROM quotes WHERE id = :id")
    suspend fun getById(id: Long): QuoteEntity?

    @Insert
    suspend fun insert(entity: QuoteEntity): Long

    /**
     * A56: the estimate flow now saves a preliminary row when a system is first calculated
     * (before any customer/quote details exist), then finishes that SAME row once CREATE QUOTE
     * completes — never a second, duplicate row for one project.
     */
    @Update
    suspend fun update(entity: QuoteEntity)

    @Delete
    suspend fun delete(entity: QuoteEntity)

    @Query("DELETE FROM quotes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM quotes")
    suspend fun deleteAll()
}
