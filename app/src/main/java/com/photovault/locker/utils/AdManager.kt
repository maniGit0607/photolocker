package com.photovault.locker.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import android.os.Bundle
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdManager {
    
    private const val TAG = "AdManager"
    private var isInitialized = false
    
    /**
     * Initialize AdMob SDK
     */
    fun initialize(context: Context, onComplete: (() -> Unit)? = null) {
        if (isInitialized) {
            onComplete?.invoke()
            return
        }
        
        MobileAds.initialize(context) { initializationStatus ->
            isInitialized = true
            Log.d(TAG, "AdMob initialized: ${initializationStatus.adapterStatusMap}")
            onComplete?.invoke()
        }
    }
    
    /**
     * Initialize consent and AdMob SDK
     */
    fun initializeWithConsent(
        context: Context,
        onConsentReceived: (Boolean) -> Unit,
        onConsentError: (String) -> Unit,
        onComplete: (() -> Unit)? = null
    ) {
        // First initialize AdMob
        initialize(context) {
            // Then request consent
            ConsentManager.requestConsent(
                context = context,
                onConsentReceived = { hasConsent ->
                    Log.d(TAG, "Consent received: $hasConsent")
                    onConsentReceived(hasConsent)
                    onComplete?.invoke()
                },
                onConsentError = { error ->
                    Log.e(TAG, "Consent error: $error")
                    onConsentError(error)
                    onComplete?.invoke()
                }
            )
        }
    }
    
    /**
     * Load and display a banner ad with consent handling
     */
    fun loadBannerAd(adView: AdView, context: Context) {
        val hasConsent = ConsentManager.hasUserConsented(context)
        val adRequest = if (hasConsent) {
            Log.d(TAG, "Loading personalized banner ad")
            AdRequest.Builder().build()
        } else {
            Log.d(TAG, "Loading non-personalized banner ad")
            val extras = Bundle().apply {
                putString("npa", "1") // npa = non-personalized ads
            }
            AdRequest.Builder()
                .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
                .build()
        }
        adView.loadAd(adRequest)
        Log.d(TAG, "Banner ad loading... (Consent: $hasConsent)")
    }
    
    /**
     * Load and display a banner ad (legacy method for backward compatibility)
     */
    fun loadBannerAd(adView: AdView) {
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
        Log.d(TAG, "Banner ad loading... (No consent context - using personalized)")
    }
    
    /**
     * Load an interstitial ad with consent handling
     */
    fun loadInterstitialAd(
        context: Context,
        adUnitId: String = Constants.INTERSTITIAL_AD_UNIT_ID,
        onAdLoaded: (InterstitialAd) -> Unit,
        onAdFailedToLoad: (LoadAdError) -> Unit
    ) {
        val hasConsent = ConsentManager.hasUserConsented(context)
        val adRequest = if (hasConsent) {
            Log.d(TAG, "Loading personalized interstitial ad")
            AdRequest.Builder().build()
        } else {
            Log.d(TAG, "Loading non-personalized interstitial ad")
            val extras = Bundle().apply {
                putString("npa", "1") // npa = non-personalized ads
            }
            AdRequest.Builder()
                .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
                .build()
        }
        
        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad loaded successfully")
                    onAdLoaded(interstitialAd)
                }
                
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG, "Interstitial ad failed to load: ${loadAdError.message}")
                    onAdFailedToLoad(loadAdError)
                }
            }
        )
    }
    
    /**
     * Show an interstitial ad
     */
    fun showInterstitialAd(
        activity: Activity,
        interstitialAd: InterstitialAd?,
        onAdDismissed: () -> Unit
    ) {
        if (interstitialAd == null) {
            Log.e(TAG, "Interstitial ad is not ready yet")
            onAdDismissed()
            return
        }
        
        interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial ad dismissed")
                onAdDismissed()
            }
            
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Interstitial ad failed to show: ${adError.message}")
                onAdDismissed()
            }
            
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial ad showed")
            }
        }
        
        interstitialAd.show(activity)
    }
    
    /**
     * Check if it's time to show album view ad based on frequency
     */
    fun shouldShowAlbumViewAd(context: Context): Boolean {
        val prefs = context.getSharedPreferences("ad_prefs", Context.MODE_PRIVATE)
        val currentCount = prefs.getInt(Constants.PREF_ALBUM_VIEW_COUNT, 0)
        val newCount = currentCount + 1
        
        prefs.edit().putInt(Constants.PREF_ALBUM_VIEW_COUNT, newCount).apply()
        
        return newCount % Constants.ALBUM_VIEW_AD_FREQUENCY == 0
    }
    
    /**
     * Check if it's time to show photo import ad based on frequency
     */
    fun shouldShowPhotoImportAd(context: Context): Boolean {
        val prefs = context.getSharedPreferences("ad_prefs", Context.MODE_PRIVATE)
        val currentCount = prefs.getInt(Constants.PREF_PHOTO_IMPORT_COUNT, 0)
        val newCount = currentCount + 1
        
        prefs.edit().putInt(Constants.PREF_PHOTO_IMPORT_COUNT, newCount).apply()
        
        return newCount % Constants.PHOTO_IMPORT_AD_FREQUENCY == 0
    }
    
    /**
     * Reset album view count
     */
    fun resetAlbumViewCount(context: Context) {
        val prefs = context.getSharedPreferences("ad_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt(Constants.PREF_ALBUM_VIEW_COUNT, 0).apply()
    }
    
    /**
     * Reset photo import count
     */
    fun resetPhotoImportCount(context: Context) {
        val prefs = context.getSharedPreferences("ad_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt(Constants.PREF_PHOTO_IMPORT_COUNT, 0).apply()
    }
}

