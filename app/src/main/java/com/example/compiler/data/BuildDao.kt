package com.example.compiler.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BuildDao {
    @Query("SELECT * FROM build_records ORDER BY createdAtTimestamp DESC")
    fun getAllBuildsFlow(): Flow<List<BuildRecordEntity>>

    @Query("SELECT * FROM build_records WHERE status IN ('PREPARING', 'UPLOADING', 'DISPATCHED', 'QUEUED', 'BUILDING', 'DOWNLOADING_ARTIFACT') ORDER BY createdAtTimestamp DESC")
    fun getActiveBuildsFlow(): Flow<List<BuildRecordEntity>>

    @Query("SELECT * FROM build_records WHERE id = :id LIMIT 1")
    suspend fun getBuildById(id: String): BuildRecordEntity?

    @Query("SELECT * FROM build_records WHERE id = :id LIMIT 1")
    fun getBuildFlow(id: String): Flow<BuildRecordEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBuild(build: BuildRecordEntity)

    @Update
    suspend fun updateBuild(build: BuildRecordEntity)

    @Query("DELETE FROM build_records WHERE id = :id")
    suspend fun deleteBuild(id: String)

    @Query("DELETE FROM build_records WHERE id IN (:ids)")
    suspend fun deleteBuilds(ids: List<String>)
}
