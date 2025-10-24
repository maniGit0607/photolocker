package com.photovault.locker.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermissionUtils {
    
    fun hasStoragePermissions(context: Context): Boolean {
        // For Android 11+ (API 30+), we only need READ_MEDIA_IMAGES
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    }
    
    fun getRequiredPermissions(): Array<String> {
        // For Android 11+ (API 30+), we only need READ_MEDIA_IMAGES
        return arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    }
}


