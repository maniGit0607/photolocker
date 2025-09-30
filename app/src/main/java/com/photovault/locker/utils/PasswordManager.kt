package com.photovault.locker.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

class PasswordManager(private val context: Context) {
    
    companion object {
        private const val PREFS_FILE_NAME = "photo_vault_prefs"
        private const val KEY_PASSWORD_SET = "password_set"
        private const val KEY_PASSWORD_HASH = "password_hash"
    }
    
    private val sharedPreferences: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
                
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to regular SharedPreferences if encryption fails
            context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
        }
    }
    
    fun isPasswordSet(): Boolean {
        return sharedPreferences.getBoolean(KEY_PASSWORD_SET, false)
    }
    
    fun setPassword(password: String): Boolean {
        return try {
            val hashedPassword = hashPassword(password)
            sharedPreferences.edit()
                .putBoolean(KEY_PASSWORD_SET, true)
                .putString(KEY_PASSWORD_HASH, hashedPassword)
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun verifyPassword(password: String): Boolean {
        if (!isPasswordSet()) return false
        
        val storedHash = sharedPreferences.getString(KEY_PASSWORD_HASH, "") ?: ""
        val inputHash = hashPassword(password)
        
        return storedHash == inputHash
    }
    
    fun changePassword(currentPassword: String, newPassword: String): Boolean {
        if (!verifyPassword(currentPassword)) return false
        return setPassword(newPassword)
    }
    
    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(password.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    fun clearPassword() {
        sharedPreferences.edit()
            .remove(KEY_PASSWORD_SET)
            .remove(KEY_PASSWORD_HASH)
            .apply()
    }
}

