package com.simplecallrecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.simplecallrecorder.data.Recording
import com.simplecallrecorder.data.RecordingDatabase
import com.simplecallrecorder.databinding.ActivityMainBinding
import com.simplecallrecorder.ui.RecordingsAdapter
import com.simplecallrecorder.util.PreferencesManager
import com.simplecallrecorder.worker.UploadWorker
import kotlinx.coroutines.launch

class RecordingsViewModel(private val db: RecordingDatabase) : ViewModel() {
    val recordings = db.recordingDao().getAllRecordings()

    fun deleteRecording(recording: Recording) {
        viewModelScope.launch {
            db.recordingDao().delete(recording)
            val file = java.io.File(recording.filePath)
            if (file.exists()) file.delete()
        }
    }
}

class RecordingsViewModelFactory(private val db: RecordingDatabase) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecordingsViewModel::class.java)) {
            return RecordingsViewModel(db) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RecordingsAdapter
    private lateinit var prefsManager: PreferencesManager
    private val db by lazy { RecordingDatabase.getDatabase(this) }
    private val viewModel: RecordingsViewModel by viewModels {
        RecordingsViewModelFactory(db)
    }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(this, getString(R.string.permissions_required), Toast.LENGTH_LONG).show()
        } else {
            promptAccessibilityService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        prefsManager = PreferencesManager(this)
        setupRecyclerView()
        observeRecordings()
        checkPermissions()
    }

    private fun setupRecyclerView() {
        adapter = RecordingsAdapter(
            onUploadClick = { recording ->
                if (prefsManager.googleAccountEmail.isNullOrEmpty()) {
                    Toast.makeText(this, getString(R.string.configure_google_account), Toast.LENGTH_LONG).show()
                } else {
                    UploadWorker.scheduleUpload(this, recording.id)
                    Toast.makeText(this, getString(R.string.upload_scheduled), Toast.LENGTH_SHORT).show()
                }
            },
            onDeleteClick = { recording ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.delete_recording)
                    .setMessage(R.string.delete_recording_message)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        viewModel.deleteRecording(recording)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        )
        binding.recyclerView.adapter = adapter
    }

    private fun observeRecordings() {
        viewModel.recordings.observe(this) { recordings ->
            adapter.submitList(recordings)
            binding.tvEmptyState.visibility =
                if (recordings.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionsLauncher.launch(missing.toTypedArray())
        } else {
            promptAccessibilityService()
        }
    }

    private fun promptAccessibilityService() {
        if (!isAccessibilityServiceEnabled()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.enable_accessibility)
                .setMessage(R.string.enable_accessibility_message)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "${packageName}/.service.CallRecorderAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.split(":").any {
            it.equals(serviceName, ignoreCase = true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isAccessibilityServiceEnabled()) {
            binding.tvAccessibilityStatus.text = getString(R.string.accessibility_disabled)
        } else {
            binding.tvAccessibilityStatus.text = getString(R.string.accessibility_enabled)
        }
    }
}
