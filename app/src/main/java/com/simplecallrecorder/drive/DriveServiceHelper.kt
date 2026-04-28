package com.simplecallrecorder.drive

import android.content.Context
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DriveServiceHelper(private val context: Context, private val accountName: String) {

    companion object {
        private const val TAG = "DriveServiceHelper"
        private const val APP_FOLDER_NAME = "SimpleCallRecorder"
    }

    private fun buildDriveService(): Drive? {
        return try {
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(DriveScopes.DRIVE_FILE)
            ).apply {
                selectedAccountName = accountName
            }

            Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("SimpleCallRecorder")
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build Drive service", e)
            null
        }
    }

    suspend fun uploadFile(file: File): String? = withContext(Dispatchers.IO) {
        try {
            val driveService = buildDriveService() ?: return@withContext null

            val folderId = getOrCreateAppFolder(driveService) ?: return@withContext null

            val metadata = DriveFile().apply {
                name = file.name
                parents = listOf(folderId)
            }

            val mediaContent = FileContent("audio/mpeg", file)
            val uploadedFile = driveService.files().create(metadata, mediaContent)
                .setFields("id, name")
                .execute()

            Log.d(TAG, "Uploaded file: ${uploadedFile.id}")
            uploadedFile.id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload file", e)
            null
        }
    }

    private fun getOrCreateAppFolder(drive: Drive): String? {
        return try {
            val query = "mimeType='application/vnd.google-apps.folder' and name='$APP_FOLDER_NAME' and trashed=false"
            val result = drive.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            if (result.files.isNotEmpty()) {
                result.files[0].id
            } else {
                val folderMetadata = DriveFile().apply {
                    name = APP_FOLDER_NAME
                    mimeType = "application/vnd.google-apps.folder"
                }
                val folder = drive.files().create(folderMetadata)
                    .setFields("id")
                    .execute()
                folder.id
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get/create folder", e)
            null
        }
    }
}
