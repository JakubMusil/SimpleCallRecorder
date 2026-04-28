package com.simplecallrecorder.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY dateTime DESC")
    fun getAllRecordings(): LiveData<List<Recording>>

    @Query("SELECT * FROM recordings WHERE isUploaded = 0 ORDER BY dateTime DESC")
    suspend fun getPendingUploadRecordings(): List<Recording>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: Recording): Long

    @Update
    suspend fun update(recording: Recording)

    @Delete
    suspend fun delete(recording: Recording)

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): Recording?

    @Query("UPDATE recordings SET isUploaded = 1, uploadedAt = :uploadedAt WHERE id = :id")
    suspend fun markAsUploaded(id: Long, uploadedAt: String)
}
