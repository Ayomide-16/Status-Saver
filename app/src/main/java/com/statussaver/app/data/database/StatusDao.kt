package com.statussaver.app.data.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface StatusDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(status: BackedUpStatus): Long
    
    @Query("SELECT * FROM backed_up_statuses ORDER BY backupTimestamp DESC")
    fun getAllBackups(): LiveData<List<BackedUpStatus>>
    
    @Query("SELECT * FROM backed_up_statuses ORDER BY backupTimestamp DESC")
    suspend fun getAllBackupsSync(): List<BackedUpStatus>
    
    @Query("SELECT * FROM backed_up_statuses WHERE backupTimestamp < :timestamp")
    suspend fun getBackupsOlderThan(timestamp: Long): List<BackedUpStatus>
    
    @Query("SELECT * FROM backed_up_statuses WHERE filename = :filename LIMIT 1")
    suspend fun getByFilename(filename: String): BackedUpStatus?
    
    @Query("SELECT EXISTS(SELECT 1 FROM backed_up_statuses WHERE filename = :filename)")
    suspend fun existsByFilename(filename: String): Boolean
    
    @Delete
    suspend fun delete(status: BackedUpStatus)
    
    @Query("DELETE FROM backed_up_statuses WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM backed_up_statuses WHERE backupTimestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long): Int
    
    @Query("SELECT COUNT(*) FROM backed_up_statuses")
    suspend fun getCount(): Int
}
