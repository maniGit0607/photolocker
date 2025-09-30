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

class PhotoAdapter(
    private val onPhotoClick: (Photo, Int) -> Unit,
    private val onPhotoLongClick: (Photo) -> Unit
) : ListAdapter<Photo, PhotoAdapter.PhotoViewHolder>(PhotoDiffCallback()) {

    private var selectionMode = false
    private val selectedPhotos = mutableSetOf<Long>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class PhotoViewHolder(private val binding: ItemPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(photo: Photo, position: Int) {
            binding.apply {
                // Load photo
                Glide.with(ivPhoto.context)
                    .load(photo.filePath)
                    .centerCrop()
                    .placeholder(R.drawable.ic_photo)
                    .error(R.drawable.ic_photo)
                    .into(ivPhoto)

                // Handle selection mode
                val isSelected = selectedPhotos.contains(photo.id)
                if (selectionMode) {
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

