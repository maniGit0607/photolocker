package com.photovault.locker.models

import android.net.Uri

data class GalleryPhoto(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val dateModified: Long
)

