package com.simplecallrecorder.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isCloudUploadEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLOUD_UPLOAD, false)
        set(value) = prefs.edit { putBoolean(KEY_CLOUD_UPLOAD, value) }

    var googleAccountEmail: String?
        get() = prefs.getString(KEY_GOOGLE_ACCOUNT, null)
        set(value) = prefs.edit { putString(KEY_GOOGLE_ACCOUNT, value) }

    var recordingsFolderUri: String?
        get() = prefs.getString(KEY_RECORDINGS_FOLDER, null)
        set(value) = prefs.edit { putString(KEY_RECORDINGS_FOLDER, value) }

    companion object {
        private const val PREFS_NAME = "simple_call_recorder_prefs"
        private const val KEY_CLOUD_UPLOAD = "cloud_upload_enabled"
        private const val KEY_GOOGLE_ACCOUNT = "google_account_email"
        private const val KEY_RECORDINGS_FOLDER = "recordings_folder_uri"
    }
}
