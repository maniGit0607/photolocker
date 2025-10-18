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
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

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
     * Load a rewarded ad
     */
    fun loadRewardedAd(
        context: Context,
        adUnitId: String = Constants.REWARDED_AD_UNIT_ID,
        onAdLoaded: (RewardedAd) -> Unit,
        onAdFailedToLoad: (LoadAdError) -> Unit
    ) {
        val adRequest = AdRequest.Builder().build()
        
        RewardedAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    Log.d(TAG, "Rewarded ad loaded successfully")
                    onAdLoaded(rewardedAd)
                }
                
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.e(TAG, "Rewarded ad failed to load: ${loadAdError.message}")
                    onAdFailedToLoad(loadAdError)
                }
            }
        )
    }
    
    /**
     * Show a rewarded ad
     */
    fun showRewardedAd(
        activity: Activity,
        rewardedAd: RewardedAd?,
        onUserEarnedReward: () -> Unit,
        onAdDismissed: () -> Unit
    ) {
        if (rewardedAd == null) {
            Log.e(TAG, "Rewarded ad is not ready yet")
            onAdDismissed()
            return
        }
        
        rewardedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded ad dismissed")
                onAdDismissed()
            }
            
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Rewarded ad failed to show: ${adError.message}")
                onAdDismissed()
            }
            
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded ad showed")
            }
        }
        
        rewardedAd.show(activity) { rewardItem ->
            Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
            onUserEarnedReward()
        }
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
     * Reset album view count
     */
    fun resetAlbumViewCount(context: Context) {
        val prefs = context.getSharedPreferences("ad_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt(Constants.PREF_ALBUM_VIEW_COUNT, 0).apply()
    }
}

