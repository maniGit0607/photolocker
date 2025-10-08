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

class AuthenticationActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAuthenticationBinding
    private lateinit var passwordManager: PasswordManager
    private var isSetupMode = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthenticationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        passwordManager = PasswordManager(this)
        
        setupUI()
        setupListeners()
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
            binding.ivLock.setImageResource(R.drawable.ic_lock_red)
            binding.ivLock.clearColorFilter() // Remove any tint
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

