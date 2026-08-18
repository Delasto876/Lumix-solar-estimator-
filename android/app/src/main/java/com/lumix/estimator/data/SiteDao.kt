package com.lumix.estimator.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteDao {
    @Query("SELECT * FROM sites ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<SiteEntity>>

    @Query("SELECT * FROM sites WHERE id = :id")
    suspend fun getById(id: String): SiteEntity?

    /** One row per site id — a save always overwrites the previous save of the same site, never duplicates. */
    @Upsert
    suspend fun upsert(entity: SiteEntity)

    @Query("DELETE FROM sites WHERE id = :id")
    suspend fun deleteById(id: String)
}
