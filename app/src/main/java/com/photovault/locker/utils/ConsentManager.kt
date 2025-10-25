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
        Log.d(TAG, "Requesting consent...")
        
        // Ensure we have an Activity context
        if (context !is android.app.Activity) {
            Log.e(TAG, "Context must be an Activity for UMP SDK")
            onConsentError("Context must be an Activity")
            return
        }
        
        val consentInformation = UserMessagingPlatform.getConsentInformation(context)
        val currentStatus = consentInformation.consentStatus
        val canRequest = consentInformation.canRequestAds()
        
        Log.d(TAG, "Current consent status: $currentStatus")
        Log.d(TAG, "Can request ads: $canRequest")
        
        // Check if consent is already obtained
        if (canRequest) {
            Log.d(TAG, "Consent can be requested - proceeding with consent flow")
            
            val params = ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false) // Set to true if your app is directed to children
                .build()
            
            Log.d(TAG, "Requesting consent info update...")
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
            Log.d(TAG, "Consent cannot be requested, using current status: $currentStatus")
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
        Log.d(TAG, "Loading consent form...")
        
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
                val status = consentInformation.consentStatus
                Log.d(TAG, "Consent status before showing form: $status")
                
                if (status == ConsentInformation.ConsentStatus.REQUIRED) {
                    Log.d(TAG, "Consent is required - showing consent form")
                    consentForm.show(
                        context,
                        { formError ->
                            if (formError != null) {
                                Log.e(TAG, "Consent form error: ${formError.message}")
                                onConsentError("Consent form error: ${formError.message}")
                            } else {
                                Log.d(TAG, "Consent form dismissed by user")
                                // Handle the result after form is dismissed
                                val finalStatus = consentInformation.consentStatus
                                Log.d(TAG, "Final consent status: $finalStatus")
                                setConsentStatus(context, finalStatus)
                                val hasConsent = finalStatus == ConsentInformation.ConsentStatus.OBTAINED
                                setUserConsent(context, hasConsent)
                                onConsentReceived(hasConsent)
                            }
                        }
                    )
                } else {
                    Log.d(TAG, "Consent not required - status: $status")
                    // Handle consent result
                    setConsentStatus(context, status)
                    val hasConsent = status == ConsentInformation.ConsentStatus.OBTAINED
                    setUserConsent(context, hasConsent)
                    onConsentReceived(hasConsent)
                }
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
        try {
            val consentInformation = UserMessagingPlatform.getConsentInformation(context)
            consentInformation.reset()
            
            val prefs = context.getSharedPreferences(CONSENT_PREF, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            
            Log.d(TAG, "Consent reset successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting consent: ${e.message}", e)
        }
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


