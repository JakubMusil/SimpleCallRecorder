package com.simplecallrecorder.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.simplecallrecorder.data.RecordingDatabase
import com.simplecallrecorder.drive.DriveServiceHelper
import com.simplecallrecorder.util.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UploadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "UploadWorker"
        const val KEY_RECORDING_ID = "recording_id"

        fun scheduleUpload(context: Context, recordingId: Long) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val data = workDataOf(KEY_RECORDING_ID to recordingId)

            val uploadRequest = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    java.util.concurrent.TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "upload_$recordingId",
                    ExistingWorkPolicy.KEEP,
                    uploadRequest
                )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val recordingId = inputData.getLong(KEY_RECORDING_ID, -1L)
        if (recordingId == -1L) {
            Log.e(TAG, "No recording ID provided")
            return@withContext Result.failure()
        }

        val prefs = PreferencesManager(context)
        if (!prefs.isCloudUploadEnabled) {
            Log.d(TAG, "Cloud upload disabled, skipping")
            return@withContext Result.success()
        }

        val accountName = prefs.googleAccountEmail
        if (accountName.isNullOrEmpty()) {
            Log.e(TAG, "No Google account configured")
            return@withContext Result.failure()
        }

        val db = RecordingDatabase.getDatabase(context)
        val recording = db.recordingDao().getById(recordingId)
        if (recording == null) {
            Log.e(TAG, "Recording not found: $recordingId")
            return@withContext Result.failure()
        }

        val file = File(recording.filePath)
        if (!file.exists()) {
            Log.e(TAG, "Recording file not found: ${recording.filePath}")
            return@withContext Result.failure()
        }

        val driveHelper = DriveServiceHelper(context, accountName)
        val fileId = driveHelper.uploadFile(file)

        return@withContext if (fileId != null) {
            val uploadedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                .format(Date())
            db.recordingDao().markAsUploaded(recordingId, uploadedAt)
            Log.d(TAG, "Successfully uploaded recording $recordingId")
            Result.success()
        } else {
            Log.e(TAG, "Failed to upload recording $recordingId")
            Result.retry()
        }
    }
}
