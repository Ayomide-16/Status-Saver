package com.statussaver.app.data.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface StatusDao {
    
    // ========== Status Operations ==========
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStatus(status: StatusEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(statuses: List<StatusEntity>): List<Long>
    
    @Query("SELECT filename FROM statuses WHERE source = :source AND filename IN (:filenames)")
    suspend fun getExistingFilenames(filenames: List<String>, source: StatusSource): List<String>
    
    @Query("SELECT * FROM statuses WHERE source = :source ORDER BY savedAt DESC, id DESC")
    fun getStatusesBySource(source: StatusSource): LiveData<List<StatusEntity>>
    
    @Query("SELECT * FROM statuses WHERE source = :source ORDER BY savedAt DESC, id DESC")
    suspend fun getStatusesBySourceSync(source: StatusSource): List<StatusEntity>
    
    @Query("SELECT DISTINCT * FROM statuses WHERE source = :source AND fileType = :fileType ORDER BY savedAt DESC, id DESC")
    fun getStatusesBySourceAndType(source: StatusSource, fileType: FileType): LiveData<List<StatusEntity>>
    
    @Query("SELECT * FROM statuses WHERE savedAt < :timestamp AND source = 'CACHED'")
    suspend fun getCachedStatusesOlderThan(timestamp: Long): List<StatusEntity>
    
    @Query("SELECT EXISTS(SELECT 1 FROM statuses WHERE filename = :filename AND source = :source)")
    suspend fun existsByFilenameAndSource(filename: String, source: StatusSource): Boolean
    
    @Query("SELECT * FROM statuses WHERE filename = :filename AND source = :source LIMIT 1")
    suspend fun getStatusByFilenameAndSource(filename: String, source: StatusSource): StatusEntity?
    
    @Delete
    suspend fun deleteStatus(status: StatusEntity)
    
    @Query("DELETE FROM statuses WHERE id = :id")
    suspend fun deleteStatusById(id: Long)
    
    @Query("SELECT * FROM statuses WHERE id = :id LIMIT 1")
    suspend fun getStatusByIdSync(id: Long): StatusEntity?
    
    @Query("DELETE FROM statuses WHERE savedAt < :timestamp AND source = 'CACHED'")
    suspend fun deleteCachedOlderThan(timestamp: Long): Int
    
    @Query("SELECT COUNT(*) FROM statuses WHERE source = :source")
    suspend fun getCountBySource(source: StatusSource): Int
    
    // Remove duplicates - keep only the latest entry for each filename
    @Query("DELETE FROM statuses WHERE id NOT IN (SELECT MAX(id) FROM statuses GROUP BY filename, source)")
    suspend fun removeDuplicates(): Int
    
    // ========== Downloaded Status Operations ==========
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markAsDownloaded(downloaded: DownloadedStatus)
    
    @Query("SELECT * FROM downloaded_status WHERE filename = :filename AND source = :source")
    suspend fun getDownloadedByFilenameAndSource(filename: String, source: StatusSource): DownloadedStatus?
    
    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_status WHERE filename = :filename AND source = :source)")
    suspend fun isDownloaded(filename: String, source: StatusSource): Boolean
    
    @Query("SELECT * FROM downloaded_status ORDER BY downloadedAt DESC")
    fun getAllDownloaded(): LiveData<List<DownloadedStatus>>
    
    @Query("SELECT filename || '|' || source FROM downloaded_status")
    suspend fun getAllDownloadedKeys(): List<String>
    
    @Query("DELETE FROM downloaded_status WHERE filename = :filename AND source = :source")
    suspend fun removeDownloadedStatus(filename: String, source: StatusSource)
}
