# Google AdMob Integration Guide

## Overview
Google AdMob has been integrated into the PhotoVault Locker app with the following ad placements:

### 1. Banner Ads
- **Location**: Footer of MainActivity and AlbumViewActivity
- **Type**: Standard banner ad (320x50)
- **Behavior**: Loads automatically when the activity is created

### 2. Rewarded Ads - PhotoImportActivity
- **Trigger**: Every time user enters the photo import screen
- **Behavior**: Ad loads and shows immediately when activity opens
- **User Experience**: User must watch the ad to earn reward, then can proceed to select photos

### 3. Rewarded Ads - AlbumViewActivity
- **Trigger**: Shows with reduced frequency (every 3rd album view)
- **Behavior**: Ad loads and shows based on frequency counter
- **User Experience**: Less intrusive, shows occasionally when opening albums

## Files Modified

### Configuration Files
- `app/build.gradle` - Added AdMob dependency
- `AndroidManifest.xml` - Added internet permissions and AdMob app ID
- `values/strings.xml` - Added ad unit ID strings

### Utility Classes
- `utils/AdManager.kt` - New utility class for managing all ad operations
- `utils/Constants.kt` - Added ad unit IDs and frequency settings

### Layout Files
- `layout/activity_main.xml` - Added banner AdView
- `layout/activity_album_view.xml` - Added banner AdView

### Activity Files
- `activities/MainActivity.kt` - Initialized AdMob and banner ad
- `activities/AlbumViewActivity.kt` - Added banner ad and occasional rewarded ads
- `activities/PhotoImportActivity.kt` - Added rewarded ad on entry

## Ad Unit IDs (Currently Using Test IDs)

⚠️ **IMPORTANT**: The app is currently using Google's test ad unit IDs. Before publishing to production, you MUST replace these with your own ad unit IDs from AdMob console.

### Current Test IDs:
- **Banner Ad**: `ca-app-pub-3940256099942544/6300978111`
- **Rewarded Ad**: `ca-app-pub-3940256099942544/5224354917`

### Where to Replace:
1. `res/values/strings.xml`:
   ```xml
   <string name="banner_ad_unit_id">YOUR_BANNER_AD_UNIT_ID</string>
   <string name="rewarded_ad_unit_id">YOUR_REWARDED_AD_UNIT_ID</string>
   ```

2. `utils/Constants.kt`:
   ```kotlin
   const val BANNER_AD_UNIT_ID = "YOUR_BANNER_AD_UNIT_ID"
   const val REWARDED_AD_UNIT_ID = "YOUR_REWARDED_AD_UNIT_ID"
   ```

3. `AndroidManifest.xml`:
   ```xml
   <meta-data
       android:name="com.google.android.gms.ads.APPLICATION_ID"
       android:value="YOUR_ADMOB_APP_ID"/>
   ```

## Ad Frequency Configuration

You can adjust how often rewarded ads appear in AlbumViewActivity:

In `utils/Constants.kt`:
```kotlin
const val ALBUM_VIEW_AD_FREQUENCY = 3  // Show ad every 3rd album view
```

Change the value to control frequency:
- `2` = Show every 2nd time
- `5` = Show every 5th time
- etc.

## Getting Your Own Ad Unit IDs

1. Go to [AdMob Console](https://apps.admob.com/)
2. Create a new app or select your existing app
3. Create Ad Units:
   - Create a Banner ad unit
   - Create a Rewarded ad unit
4. Copy the ad unit IDs
5. Replace the test IDs in the locations mentioned above

## Testing

### Test Ads
The current implementation uses Google's test ad unit IDs, so you'll see "Test Ad" labels on the ads during development.

### Real Ads
⚠️ **DO NOT** click on real ads during testing - this can get your AdMob account suspended. Always use test IDs during development.

## Important Notes

1. **Internet Permission**: Required for ads to load (already added)
2. **Network State**: App checks network state for optimal ad loading
3. **User Experience**: 
   - Banner ads are non-intrusive and appear at the bottom
   - Rewarded ads in PhotoImport show every time for maximum monetization
   - Rewarded ads in AlbumView are frequency-controlled for better UX
4. **Error Handling**: If ads fail to load, the app continues normally without disruption

## AdMob Policies

Remember to comply with AdMob policies:
- No clicking your own ads
- No encouraging users to click ads
- Properly implement GDPR/CCPA consent if required
- Follow app content policies

## Revenue Optimization Tips

1. Monitor performance in AdMob console
2. Adjust AlbumView ad frequency based on user retention metrics
3. Consider adding more ad placements in non-critical flows
4. Use mediation to maximize fill rates and eCPM

---

**Last Updated**: October 16, 2025

