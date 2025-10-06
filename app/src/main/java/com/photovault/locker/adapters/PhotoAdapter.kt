package com.photovault.locker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.photovault.locker.R
import com.photovault.locker.databinding.ItemPhotoBinding
import com.photovault.locker.models.Photo
import java.io.File

class PhotoAdapter(
    private val onPhotoClick: (Photo, Int) -> Unit,
    private val onPhotoLongClick: (Photo) -> Unit
) : ListAdapter<Photo, PhotoAdapter.PhotoViewHolder>(PhotoDiffCallback()) {

    override fun submitList(list: List<Photo>?, commitCallback: Runnable?) {
        android.util.Log.d("PhotoAdapter", "submitList called with ${list?.size ?: 0} items")
        super.submitList(list, commitCallback)
    }

    private var selectionMode = false
    private val selectedPhotos = mutableSetOf<Long>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        android.util.Log.d("PhotoAdapter", "onBindViewHolder called for position $position")
        android.util.Log.d("PhotoAdapter", "Total items in adapter: ${itemCount}")
        holder.bind(getItem(position), position)
    }

    inner class PhotoViewHolder(private val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(photo: Photo, position: Int) {
            binding.apply {
                // Debug: Log the photo details
                android.util.Log.d("PhotoAdapter", "Binding photo at position $position: ${photo.originalName}")
                android.util.Log.d("PhotoAdapter", "Photo file path: ${photo.filePath}")
                
                // Check if file exists
                val file = java.io.File(photo.filePath)
                android.util.Log.d("PhotoAdapter", "File exists: ${file.exists()}, size: ${file.length()}")
                
                // Load photo with better configuration
                android.util.Log.d("PhotoAdapter", "Starting Glide load for ${photo.originalName}")
                
                Glide.with(ivPhoto.context)
                    .load(photo.filePath) // Use string path directly, not File object
                    .centerCrop()
                    .placeholder(R.drawable.ic_photo)
                    .error(R.drawable.ic_photo)
                    .override(300, 300) // Limit image size to prevent main thread blocking
                    .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                        override fun onLoadFailed(
                            e: com.bumptech.glide.load.engine.GlideException?,
                            model: Any?,
                            target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                            isFirstResource: Boolean
                        ): Boolean {
                            android.util.Log.e("PhotoAdapter", "Glide load failed for ${photo.originalName}: ${e?.message}")
                            android.util.Log.e("PhotoAdapter", "Glide exception: ${e?.logRootCauses}")
                            return false
                        }
                        
                        override fun onResourceReady(
                            resource: android.graphics.drawable.Drawable?,
                            model: Any?,
                            target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                            dataSource: com.bumptech.glide.load.DataSource?,
                            isFirstResource: Boolean
                        ): Boolean {
                            android.util.Log.d("PhotoAdapter", "Glide load successful for ${photo.originalName}")
                            return false
                        }
                    })
                    .into(ivPhoto)

                // Handle selection mode
                val isSelected = selectedPhotos.contains(photo.id)
                if (!selectionMode) {
                    selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
                    ivSelection.visibility = if (isSelected) View.VISIBLE else View.GONE
                } else {
                    selectionOverlay.visibility = View.GONE
                    ivSelection.visibility = View.GONE
                }

                // Set click listeners
                root.setOnClickListener {
                    if (selectionMode) {
                        toggleSelection(photo.id)
                        notifyItemChanged(position)
                    } else {
                        onPhotoClick(photo, position)
                    }
                }
                
                root.setOnLongClickListener {
                    if (!selectionMode) {
                        enableSelectionMode()
                        toggleSelection(photo.id)
                        notifyDataSetChanged()
                        onPhotoLongClick(photo)
                    }
                    true
                }
            }
        }
    }

    private fun toggleSelection(photoId: Long) {
        if (selectedPhotos.contains(photoId)) {
            selectedPhotos.remove(photoId)
        } else {
            selectedPhotos.add(photoId)
        }

        if (selectedPhotos.isEmpty()) {
            disableSelectionMode()
        }
    }

    fun enableSelectionMode() {
        selectionMode = true
        notifyDataSetChanged()
    }

    fun disableSelectionMode() {
        selectionMode = false
        selectedPhotos.clear()
        notifyDataSetChanged()
    }

    fun getSelectedPhotos(): List<Long> {
        return selectedPhotos.toList()
    }

    fun isSelectionMode(): Boolean {
        return selectionMode
    }

    fun getSelectedCount(): Int {
        return selectedPhotos.size
    }

    private class PhotoDiffCallback : DiffUtil.ItemCallback<Photo>() {
        override fun areItemsTheSame(oldItem: Photo, newItem: Photo): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Photo, newItem: Photo): Boolean {
            return oldItem == newItem
        }
    }
}

