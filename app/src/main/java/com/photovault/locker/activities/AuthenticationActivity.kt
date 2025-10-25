package com.photovault.locker.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.photovault.locker.R
import com.photovault.locker.databinding.ActivityAuthenticationBinding
import com.photovault.locker.utils.PasswordManager
import com.photovault.locker.utils.AdManager
import com.photovault.locker.utils.ConsentManager

class AuthenticationActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAuthenticationBinding
    private lateinit var passwordManager: PasswordManager
    private var isSetupMode = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthenticationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        passwordManager = PasswordManager(this)
        
        // Initialize consent and AdMob on app launch
        initializeConsentAndAds()
        
        setupUI()
        setupListeners()
    }
    
    private fun initializeConsentAndAds() {
        android.util.Log.d("AuthenticationActivity", "Initializing consent and AdMob...")
        
        // Debug: Check current consent status
        val currentStatus = ConsentManager.getConsentStatusDescription(this)
        android.util.Log.d("AuthenticationActivity", "Current consent status: $currentStatus")
        
        // For testing: Reset consent to force dialog (remove this in production)
        if (android.os.BuildConfig.DEBUG) {
            android.util.Log.d("AuthenticationActivity", "DEBUG: Resetting consent for testing")
            ConsentManager.resetConsent(this)
        }
        
        AdManager.initializeWithConsent(
            context = this,
            onConsentReceived = { hasConsent ->
                android.util.Log.d("AuthenticationActivity", "Consent received: $hasConsent")
                val newStatus = ConsentManager.getConsentStatusDescription(this)
                android.util.Log.d("AuthenticationActivity", "New consent status: $newStatus")
                // Consent dialog will be shown automatically if needed
            },
            onConsentError = { error ->
                android.util.Log.e("AuthenticationActivity", "Consent error: $error")
                // Continue with app even if consent fails
            }
        )
    }
    
    private fun setupUI() {
        isSetupMode = !passwordManager.isPasswordSet()
        
        if (isSetupMode) {
            // First time setup
            binding.tvTitle.text = getString(R.string.setup_password)
            binding.btnAction.text = getString(R.string.setup_password)
            binding.tilConfirmPassword.visibility = View.VISIBLE
        } else {
            // Login mode
            binding.tvTitle.text = getString(R.string.enter_password)
            binding.btnAction.text = getString(R.string.unlock)
            binding.tilConfirmPassword.visibility = View.GONE
        }
    }
    
    private fun setupListeners() {
        binding.btnAction.setOnClickListener {
            if (isSetupMode) {
                handlePasswordSetup()
            } else {
                handlePasswordVerification()
            }
        }
        
        // Add text watcher to clear error when user starts typing
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                clearError()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        
        binding.etPassword.addTextChangedListener(textWatcher)
        binding.etConfirmPassword.addTextChangedListener(textWatcher)
    }
    
    private fun handlePasswordSetup() {
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()
        
        when {
            password.isEmpty() -> {
                showError(getString(R.string.password_hint))
                return
            }
            password.length < 4 -> {
                showError("Password must be at least 4 characters long")
                return
            }
            confirmPassword.isEmpty() -> {
                showError(getString(R.string.confirm_password))
                return
            }
            password != confirmPassword -> {
                showError(getString(R.string.password_mismatch))
                return
            }
        }
        
        if (passwordManager.setPassword(password)) {
            Toast.makeText(this, getString(R.string.password_set_success), Toast.LENGTH_SHORT).show()
            navigateToMainActivity()
        } else {
            showError(getString(R.string.error_occurred))
        }
    }
    
    private fun handlePasswordVerification() {
        val password = binding.etPassword.text.toString().trim()
        
        if (password.isEmpty()) {
            showError(getString(R.string.password_hint))
            return
        }
        
        if (passwordManager.verifyPassword(password)) {
            navigateToMainActivity()
        } else {
            showError(getString(R.string.wrong_password))
            binding.etPassword.text?.clear()
        }
    }
    
    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
        
        // Change lock icon to red on wrong password
        if (message == getString(R.string.wrong_password)) {
            //binding.ivLock.setImageResource(R.drawable.ic_lock_red)
            val ivRedLock = findViewById<android.widget.ImageView>(R.id.ivRedLock)
            val ivLock = findViewById<android.widget.ImageView>(R.id.ivLock)
            ivLock.visibility = android.view.View.GONE
            ivRedLock.visibility = android.view.View.VISIBLE
        }
    }
    
    private fun clearError() {
        binding.tvError.visibility = View.GONE
        
        // Reset lock icon to normal white
        binding.ivLock.setImageResource(R.drawable.ic_lock)
        binding.ivLock.setColorFilter(android.graphics.Color.WHITE) // Restore white tint
    }
    
    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
    
    override fun onBackPressed() {
        // Prevent back navigation from authentication screen
        if (isSetupMode) {
            super.onBackPressed()
        } else {
            // Move app to background instead of closing
            moveTaskToBack(true)
        }
    }
}

