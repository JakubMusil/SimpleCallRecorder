package com.simplecallrecorder

import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.simplecallrecorder.databinding.ActivitySettingsBinding
import com.simplecallrecorder.util.PreferencesManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefsManager: PreferencesManager

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            task.addOnSuccessListener { account ->
                prefsManager.googleAccountEmail = account.email
                binding.tvGoogleAccount.text = account.email
                Toast.makeText(this, getString(R.string.signed_in_as, account.email), Toast.LENGTH_SHORT).show()
            }.addOnFailureListener {
                Toast.makeText(this, getString(R.string.sign_in_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)

        prefsManager = PreferencesManager(this)
        setupUI()
    }

    private fun setupUI() {
        binding.switchCloudUpload.isChecked = prefsManager.isCloudUploadEnabled
        binding.switchCloudUpload.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.isCloudUploadEnabled = isChecked
            binding.layoutGoogleAccount.visibility =
                if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }

        val email = prefsManager.googleAccountEmail
        binding.tvGoogleAccount.text = email ?: getString(R.string.not_signed_in)
        binding.layoutGoogleAccount.visibility =
            if (prefsManager.isCloudUploadEnabled) android.view.View.VISIBLE else android.view.View.GONE

        binding.btnSignIn.setOnClickListener {
            signInToGoogle()
        }

        binding.btnSignOut.setOnClickListener {
            signOut()
        }
    }

    private fun signInToGoogle() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()

        val client = GoogleSignIn.getClient(this, gso)
        signInLauncher.launch(client.signInIntent)
    }

    private fun signOut() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(this, gso)
        client.signOut().addOnCompleteListener {
            prefsManager.googleAccountEmail = null
            binding.tvGoogleAccount.text = getString(R.string.not_signed_in)
            Toast.makeText(this, getString(R.string.signed_out), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
