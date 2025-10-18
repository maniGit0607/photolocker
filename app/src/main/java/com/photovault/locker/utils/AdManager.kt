package com.photovault.locker.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
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
     * Load and display a banner ad
     */
    fun loadBannerAd(adView: AdView) {
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
        Log.d(TAG, "Banner ad loading...")
    }
    
    /**
     * Load an interstitial ad
     */
    fun loadInterstitialAd(
        context: Context,
        adUnitId: String = Constants.INTERSTITIAL_AD_UNIT_ID,
        onAdLoaded: (InterstitialAd) -> Unit,
        onAdFailedToLoad: (LoadAdError) -> Unit
    ) {
        val adRequest = AdRequest.Builder().build()
        
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

