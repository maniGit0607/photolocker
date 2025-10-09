package com.photovault.locker.models

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class GalleryPhoto(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val dateModified: Long
) : Parcelable

