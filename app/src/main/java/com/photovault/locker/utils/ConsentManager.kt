package com.photovault.locker.utils

import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform

object ConsentManager {
    
    private const val TAG = "ConsentManager"
    private const val CONSENT_PREF = "consent_prefs"
    private const val HAS_CONSENT_KEY = "has_consent"
    private const val CONSENT_STATUS_KEY = "consent_status"
    
    /**
     * Check if user has given consent for personalized ads
     */
    fun hasUserConsented(context: Context): Boolean {
        val prefs = context.getSharedPreferences(CONSENT_PREF, Context.MODE_PRIVATE)
        return prefs.getBoolean(HAS_CONSENT_KEY, false)
    }
    
    /**
     * Store user consent status
     */
    fun setUserConsent(context: Context, hasConsent: Boolean) {
        val prefs = context.getSharedPreferences(CONSENT_PREF, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(HAS_CONSENT_KEY, hasConsent)
            .apply()
        Log.d(TAG, "User consent stored: $hasConsent")
    }
    
    /**
     * Get detailed consent status from UMP
     */
    fun getConsentStatus(context: Context): Int {
        val prefs = context.getSharedPreferences(CONSENT_PREF, Context.MODE_PRIVATE)
        return prefs.getInt(CONSENT_STATUS_KEY, ConsentInformation.ConsentStatus.UNKNOWN)
    }
    
    /**
     * Store detailed consent status from UMP
     */
    private fun setConsentStatus(context: Context, status: Int) {
        val prefs = context.getSharedPreferences(CONSENT_PREF, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(CONSENT_STATUS_KEY, status)
            .apply()
    }
    
    /**
     * Request consent using UMP SDK
     */
    fun requestConsent(
        context: Context,
        onConsentReceived: (Boolean) -> Unit,
        onConsentError: (String) -> Unit
    ) {
        // Ensure we have an Activity context
        if (context !is android.app.Activity) {
            Log.e(TAG, "Context must be an Activity for UMP SDK")
            onConsentError("Context must be an Activity")
            return
        }
        
        val consentInformation = UserMessagingPlatform.getConsentInformation(context)
        
        // Check if consent is already obtained
        if (consentInformation.canRequestAds()) {
            Log.d(TAG, "Consent can be requested")
            
            val params = ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false) // Set to true if your app is directed to children
                .build()
            
            consentInformation.requestConsentInfoUpdate(
                context,
                params,
                {
                    Log.d(TAG, "Consent info updated successfully")
                    loadConsentForm(context, onConsentReceived, onConsentError)
                },
                { formError ->
                    Log.e(TAG, "Consent info update failed: ${formError.message}")
                    onConsentError("Failed to update consent info: ${formError.message}")
                }
            )
        } else {
            Log.d(TAG, "Consent cannot be requested, using current status")
            val currentStatus = consentInformation.consentStatus
            setConsentStatus(context, currentStatus)
            val hasConsent = currentStatus == ConsentInformation.ConsentStatus.OBTAINED
            setUserConsent(context, hasConsent)
            onConsentReceived(hasConsent)
        }
    }
    
    /**
     * Load and show consent form
     */
    private fun loadConsentForm(
        context: Context,
        onConsentReceived: (Boolean) -> Unit,
        onConsentError: (String) -> Unit
    ) {
        // Ensure we have an Activity context
        if (context !is android.app.Activity) {
            Log.e(TAG, "Context must be an Activity for UMP SDK")
            onConsentError("Context must be an Activity")
            return
        }
        
        UserMessagingPlatform.loadConsentForm(
            context,
            { consentForm ->
                Log.d(TAG, "Consent form loaded successfully")
                
                val consentInformation = UserMessagingPlatform.getConsentInformation(context)
                if (consentInformation.consentStatus == ConsentInformation.ConsentStatus.REQUIRED) {
                    Log.d(TAG, "Showing consent form")
                    consentForm.show(
                        context,
                        { formError ->
                            Log.e(TAG, "Consent form error: ${formError?.message}")
                            onConsentError("Consent form error: ${formError?.message}")
                        }
                    )
                }
                
                // Handle consent result
                val status = consentInformation.consentStatus
                setConsentStatus(context, status)
                val hasConsent = status == ConsentInformation.ConsentStatus.OBTAINED
                setUserConsent(context, hasConsent)
                onConsentReceived(hasConsent)
            },
            { formError ->
                Log.e(TAG, "Failed to load consent form: ${formError.message}")
                onConsentError("Failed to load consent form: ${formError.message}")
            }
        )
    }
    
    /**
     * Reset consent (for testing purposes)
     */
    fun resetConsent(context: Context) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(context)
        consentInformation.reset()
        
        val prefs = context.getSharedPreferences(CONSENT_PREF, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        
        Log.d(TAG, "Consent reset")
    }
    
    /**
     * Check if consent is required
     */
    fun isConsentRequired(context: Context): Boolean {
        val consentInformation = UserMessagingPlatform.getConsentInformation(context)
        return consentInformation.consentStatus == ConsentInformation.ConsentStatus.REQUIRED
    }
    
    /**
     * Get consent status description for debugging
     */
    fun getConsentStatusDescription(context: Context): String {
        val status = getConsentStatus(context)
        return when (status) {
            ConsentInformation.ConsentStatus.UNKNOWN -> "Unknown"
            ConsentInformation.ConsentStatus.REQUIRED -> "Required"
            ConsentInformation.ConsentStatus.NOT_REQUIRED -> "Not Required"
            ConsentInformation.ConsentStatus.OBTAINED -> "Obtained"
            else -> "Unknown Status: $status"
        }
    }
}


