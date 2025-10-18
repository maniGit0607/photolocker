package com.photovault.locker.utils

object Constants {
    const val DUMMY_BIN_ALBUM_NAME = "__DUMMY_BIN_ALBUM__"
    const val RESTORED_ALBUM_NAME = "Restored"
    
    // AdMob Test Ad Unit IDs (replace with your real IDs in production)
    const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"  // Test Banner
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"  // Test Rewarded
    
    // Ad frequency settings - Moderate option (balanced monetization and UX)
    const val ALBUM_VIEW_AD_FREQUENCY = 5     // Show rewarded ad every 5th album view
    const val PHOTO_IMPORT_AD_FREQUENCY = 3   // Show rewarded ad every 3rd import session
    const val PREF_ALBUM_VIEW_COUNT = "album_view_count"
    const val PREF_PHOTO_IMPORT_COUNT = "photo_import_count"
}

