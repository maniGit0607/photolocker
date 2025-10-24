# Ad Frequency Configuration

## Overview
Implemented **Moderate** ad frequency settings to balance monetization with user experience.

## Current Ad Configuration

### Banner Ads (Always Displayed) ✅
| Location | Frequency | Position |
|----------|-----------|----------|
| **MainActivity** | Always | Bottom of screen |
| **AlbumViewActivity** | Always | Bottom of screen |

**Details**:
- Non-intrusive placement
- Doesn't block content
- Industry standard practice
- Users can still interact with all features

### Rewarded Ads (Frequency-Controlled) ⚡

| Location | Frequency | User Impact |
|----------|-----------|-------------|
| **PhotoImportActivity** | Every **3rd** import session | Low - users import occasionally |
| **AlbumViewActivity** | Every **5th** album view | Low - spread across multiple views |

## User Journey Examples

### Scenario 1: Heavy User (Multiple Imports)
```
Day 1:
- Import photos (1st time) → No ad
- Import photos (2nd time) → No ad  
- Import photos (3rd time) → See rewarded ad 📺
- Import photos (4th time) → No ad
- Import photos (5th time) → No ad
- Import photos (6th time) → See rewarded ad 📺

Banner ads visible on every screen ✅
```

### Scenario 2: Album Browser
```
Session:
- Open Album A (1st) → No ad
- Open Album B (2nd) → No ad
- Open Album C (3rd) → No ad
- Open Album D (4th) → No ad
- Open Album E (5th) → See rewarded ad 📺

Banner ads visible on every screen ✅
```

### Scenario 3: Mixed Usage
```
- Main page → Banner ad
- Open album (1st) → Banner ad, no rewarded
- Import photos (1st) → No rewarded ad
- Open album (2nd) → Banner ad, no rewarded
- Import photos (2nd) → No rewarded ad
- Open album (3rd) → Banner ad, no rewarded
- Import photos (3rd) → Rewarded ad 📺
- Open album (4th) → Banner ad, no rewarded
- Open album (5th) → Rewarded ad 📺

User sees 2 rewarded ads in whole session ✅
```

## Implementation Details

### Files Modified

#### 1. `utils/Constants.kt`
```kotlin
// Moderate frequency settings
const val ALBUM_VIEW_AD_FREQUENCY = 5     // Every 5th view
const val PHOTO_IMPORT_AD_FREQUENCY = 3   // Every 3rd import
const val PREF_ALBUM_VIEW_COUNT = "album_view_count"
const val PREF_PHOTO_IMPORT_COUNT = "photo_import_count"
```

#### 2. `utils/AdManager.kt`
**New Methods**:
- `shouldShowPhotoImportAd(context)` - Tracks import count, returns true every 3rd time
- `resetPhotoImportCount(context)` - Reset counter if needed

**Existing Methods**:
- `shouldShowAlbumViewAd(context)` - Now uses updated frequency (5 instead of 3)
- `loadBannerAd(adView)` - Loads banner ads for MainActivity and AlbumViewActivity

#### 3. `activities/PhotoImportActivity.kt`
**Before**:
```kotlin
private fun setupAds() {
    AdManager.initialize(this) {
        loadRewardedAd()  // EVERY TIME ❌
    }
}
```

**After**:
```kotlin
private fun setupAds() {
    AdManager.initialize(this) {
        if (AdManager.shouldShowPhotoImportAd(this)) {  // Check frequency ✅
            loadRewardedAd()
        } else {
            hasShownAdOnEntry = true  // Skip this time
        }
    }
}
```

#### 4. `activities/AlbumViewActivity.kt`
**No changes needed** - Already using `shouldShowAlbumViewAd()` which now reads the updated frequency (5) from Constants.

#### 5. `activities/MainActivity.kt`
**Banner ad setup**:
```kotlin
private fun setupAds() {
    AdManager.initialize(this) {
        AdManager.loadBannerAd(binding.adView)  // Always shown ✅
    }
}
```

## Ad Frequency Comparison

| Metric | Before | After | Impact |
|--------|--------|-------|--------|
| Photo Import Ads | **Every time** | Every 3rd time | 66% reduction ⬇️ |
| Album View Ads | Every 3rd time | Every 5th time | 40% reduction ⬇️ |
| Banner Ads | Always | Always | No change ✅ |
| **User Annoyance** | High 😠 | Low 😊 | Much better! |
| **Retention** | Risk of uninstall | Healthy balance | Improved 📈 |

## Why "Moderate" Is Best

### ✅ Benefits
1. **Better User Experience**: Ads don't interrupt core workflows
2. **Higher Retention**: Users won't uninstall due to ad fatigue
3. **Long-term Revenue**: Retained users = more ad views over time
4. **Better Reviews**: Less "too many ads" complaints
5. **Trust**: Users feel app respects their time

### 📊 Revenue Impact
- **Short term**: Slightly lower (33% less import ads, 40% less album ads)
- **Long term**: Higher (more users staying = more total ad views)
- **Banner ads**: Still shown 100% of the time (main revenue source)

## Alternative Frequency Options

If you want to adjust later:

### Conservative (Lower ads, best retention)
```kotlin
const val ALBUM_VIEW_AD_FREQUENCY = 7
const val PHOTO_IMPORT_AD_FREQUENCY = 5
```

### Aggressive (More ads, risk retention)
```kotlin
const val ALBUM_VIEW_AD_FREQUENCY = 3
const val PHOTO_IMPORT_AD_FREQUENCY = 2
```

## Testing Checklist

- [x] Banner ads show in MainActivity
- [x] Banner ads show in AlbumViewActivity
- [x] First 2 imports show no rewarded ad
- [x] 3rd import shows rewarded ad
- [x] First 4 album views show no rewarded ad
- [x] 5th album view shows rewarded ad
- [x] Counters persist across app restarts (SharedPreferences)

## Monitoring Recommendations

After release, monitor:
1. **User retention** (Day 1, Day 7, Day 30)
2. **Ad fill rates** (how often ads load successfully)
3. **eCPM** (earnings per thousand impressions)
4. **App reviews** mentioning ads
5. **Uninstall rate**

If retention drops, reduce frequency.  
If retention is great, can slightly increase frequency.

## Future Enhancements

1. **A/B Testing**: Test different frequencies with different users
2. **Adaptive Frequency**: Show more ads to power users, fewer to casual users
3. **Remove Ads IAP**: Offer one-time purchase to remove all ads
4. **Premium Features**: Subscription with no ads + extra features

---

**Implemented**: October 16, 2025  
**Status**: ✅ Moderate frequency active  
**Next Review**: After 1000+ installs, analyze retention data




