package com.simplecallrecorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed, accessibility service should auto-restart if enabled")
            // Accessibility service restarts automatically if it was enabled
            // We can notify the user if the service is not enabled
        }
    }
}
