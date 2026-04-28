package com.simplecallrecorder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val callType: String,          // "incoming", "outgoing", "unknown"
    val phoneNumber: String,
    val dateTime: String,          // ISO-8601 string
    val durationSeconds: Long = 0,
    val fileSize: Long = 0,
    val isUploaded: Boolean = false,
    val uploadedAt: String? = null
)
