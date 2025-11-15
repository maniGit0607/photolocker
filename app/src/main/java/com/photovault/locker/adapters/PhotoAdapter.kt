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
    private val onPhotoLongClick: (Photo) -> Unit,
    private val onSetCoverPhoto: (Photo) -> Unit,
    private val onSelectionModeChanged: (Boolean) -> Unit,
    private val context: android.content.Context,
    private val showContextMenu: Boolean
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
                    .override(400, 400) // Square thumbnails - increased size for better quality
                    .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                        override fun onLoadFailed(
                            e: com.bumptech.glide.load.engine.GlideException?,
                            model: Any?,
                            target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            android.util.Log.e("PhotoAdapter", "Glide load failed for ${photo.originalName}: ${e?.message}")
                            return false
                        }
                        
                        override fun onResourceReady(
                            resource: android.graphics.drawable.Drawable,
                            model: Any?,
                            target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                            dataSource: com.bumptech.glide.load.DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            android.util.Log.d("PhotoAdapter", "Glide load successful for ${photo.originalName}")
                            
                            // Debug UI dimensions
                            binding.root.post {
                                android.util.Log.d("PhotoAdapter", "CardView dimensions: ${binding.root.width}x${binding.root.height}")
                                android.util.Log.d("PhotoAdapter", "ImageView dimensions: ${binding.ivPhoto.width}x${binding.ivPhoto.height}")
                                android.util.Log.d("PhotoAdapter", "ImageView visibility: ${binding.ivPhoto.visibility}")
                                android.util.Log.d("PhotoAdapter", "ImageView drawable: ${binding.ivPhoto.drawable}")
                            }
                            
                            return false
                        }
                    })
                    .into(ivPhoto)

                // Handle selection mode
                val isSelected = selectedPhotos.contains(photo.id)
                if(!showContextMenu) {
                    binOverlay.visibility = View.VISIBLE
                } else {
                    binOverlay.visibility = View.GONE
                }
                if (selectionMode) {
                    ivSelection.visibility = if (isSelected) View.VISIBLE else View.GONE
                } else {
                    ivSelection.visibility = View.GONE
                }

                // Set click listeners
                root.setOnClickListener {
                    if (selectionMode) {
                        toggleSelection(photo.id)
                        notifyItemChanged(position)
                        // Notify the activity to update the selection count
                        onPhotoLongClick(photo)
                    } else {
                        onPhotoClick(photo, position)
                    }
                }
                
                root.setOnLongClickListener {
                    if (!selectionMode) {
                        // Enable selection mode and select this photo
                        enableSelectionMode()
                        toggleSelection(photo.id)
                        notifyItemChanged(position)
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
        onSelectionModeChanged(true)
    }

    fun disableSelectionMode() {
        selectionMode = false
        selectedPhotos.clear()
        notifyDataSetChanged()
        onSelectionModeChanged(false)
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
    
    fun selectAll() {
        if (currentList.isNotEmpty()) {
            selectedPhotos.clear()
            selectedPhotos.addAll(currentList.map { it.id })
            notifyDataSetChanged()
            onPhotoLongClick(currentList[0]) // Notify to update count
        }
    }
    
    fun isAllSelected(): Boolean {
        return currentList.isNotEmpty() && selectedPhotos.size == currentList.size
    }
    
    private fun showContextMenu(photo: Photo) {
        val options = arrayOf("Set as Cover Photo", "Select for Deletion")
        
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("Photo Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> onSetCoverPhoto(photo)
                    1 -> {
                        enableSelectionMode()
                        toggleSelection(photo.id)
                        notifyDataSetChanged()
                        onPhotoLongClick(photo)
                    }
                }
            }
            .show()
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

