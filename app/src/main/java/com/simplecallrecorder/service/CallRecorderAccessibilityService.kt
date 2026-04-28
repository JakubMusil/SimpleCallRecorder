package com.simplecallrecorder.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.simplecallrecorder.MainActivity
import com.simplecallrecorder.R
import com.simplecallrecorder.audio.AudioRecordManager
import com.simplecallrecorder.data.Recording
import com.simplecallrecorder.data.RecordingDatabase
import com.simplecallrecorder.util.PreferencesManager
import com.simplecallrecorder.worker.UploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallRecorderAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioManager = AudioRecordManager()
    private lateinit var prefsManager: PreferencesManager
    private lateinit var telephonyManager: TelephonyManager
    private var currentCallType = "unknown"
    private var currentPhoneNumber = "unknown"
    private var recordingStartTime: Long = 0
    private var currentOutputFile: File? = null

    // For Android 12+
    private var telephonyCallback: TelephonyCallback? = null

    // For older Android
    @Suppress("DEPRECATION")
    private var phoneStateListener: PhoneStateListener? = null

    companion object {
        private const val TAG = "CallRecorderService"
        private const val NOTIFICATION_CHANNEL_ID = "call_recorder_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        prefsManager = PreferencesManager(this)
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        createNotificationChannel()
        registerPhoneStateListener()
        Log.d(TAG, "CallRecorderAccessibilityService created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        serviceInfo = info
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We primarily use TelephonyManager for call detection
        // Accessibility events can supplement for package-specific call UI detection
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterPhoneStateListener()
        if (audioManager.isRecording()) {
            stopRecording()
        }
        serviceScope.cancel()
    }

    @Suppress("DEPRECATION")
    private fun registerPhoneStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(),
                TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallState(state, "")
                }
            }
            telephonyCallback = callback
            try {
                telephonyManager.registerTelephonyCallback(
                    mainExecutor,
                    callback
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "Cannot register telephony callback", e)
            }
        } else {
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    if (!phoneNumber.isNullOrEmpty()) {
                        currentPhoneNumber = phoneNumber
                    }
                    handleCallState(state, phoneNumber ?: "")
                }
            }
            phoneStateListener = listener
            try {
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            } catch (e: SecurityException) {
                Log.e(TAG, "Cannot register phone state listener", e)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun unregisterPhoneStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
        } else {
            phoneStateListener?.let {
                telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }
    }

    private fun handleCallState(state: Int, phoneNumber: String) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                currentCallType = "incoming"
                if (phoneNumber.isNotEmpty()) currentPhoneNumber = phoneNumber
                Log.d(TAG, "Call ringing from: $currentPhoneNumber")
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.d(TAG, "Call active, starting recording")
                startRecording()
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                Log.d(TAG, "Call ended, stopping recording")
                if (audioManager.isRecording()) {
                    stopRecording()
                }
                currentCallType = "unknown"
                currentPhoneNumber = "unknown"
            }
        }
    }

    private fun startRecording() {
        if (audioManager.isRecording()) return

        showRecordingNotification()
        recordingStartTime = System.currentTimeMillis()

        serviceScope.launch {
            currentOutputFile = audioManager.startRecording(currentCallType, currentPhoneNumber)
            if (currentOutputFile == null) {
                Log.e(TAG, "Failed to start recording")
            }
        }
    }

    private fun stopRecording() {
        serviceScope.launch {
            val file = audioManager.stopRecording()
            hideRecordingNotification()

            if (file != null && file.exists() && file.length() > 0) {
                val durationSeconds = (System.currentTimeMillis() - recordingStartTime) / 1000
                val dateTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                    .format(Date(recordingStartTime))

                val recording = Recording(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    callType = currentCallType,
                    phoneNumber = currentPhoneNumber,
                    dateTime = dateTime,
                    durationSeconds = durationSeconds,
                    fileSize = file.length(),
                    isUploaded = false
                )

                val db = RecordingDatabase.getDatabase(applicationContext)
                val recordingId = db.recordingDao().insert(recording)

                // Schedule upload if cloud upload is enabled
                if (prefsManager.isCloudUploadEnabled) {
                    UploadWorker.scheduleUpload(applicationContext, recordingId)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Call Recorder",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for active call recording"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun showRecordingNotification() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_upload)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.recording_in_progress))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun hideRecordingNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIFICATION_ID)
    }
}
