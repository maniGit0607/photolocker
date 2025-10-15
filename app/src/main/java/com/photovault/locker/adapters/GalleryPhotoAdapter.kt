package com.photovault.locker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.photovault.locker.R
import com.photovault.locker.databinding.ItemGalleryPhotoBinding
import com.photovault.locker.models.GalleryPhoto

class GalleryPhotoAdapter(
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<GalleryPhoto, GalleryPhotoAdapter.GalleryPhotoViewHolder>(GalleryPhotoDiffCallback()) {

    private val selectedPhotos = mutableSetOf<Long>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryPhotoViewHolder {
        val binding = ItemGalleryPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GalleryPhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GalleryPhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GalleryPhotoViewHolder(private val binding: ItemGalleryPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(photo: GalleryPhoto) {
            binding.apply {
                // Load photo
                Glide.with(ivGalleryPhoto.context)
                    .load(photo.uri)
                    .centerCrop()
                    .placeholder(R.drawable.ic_photo)
                    .error(R.drawable.ic_photo)
                    .into(ivGalleryPhoto)

                // Handle selection state
                val isSelected = selectedPhotos.contains(photo.id)
                updateSelectionUI(isSelected)

                // Set click listener
                root.setOnClickListener {
                    toggleSelection(photo.id)
                    updateSelectionUI(selectedPhotos.contains(photo.id))
                    onSelectionChanged(selectedPhotos.size)
                }
            }
        }

        private fun updateSelectionUI(isSelected: Boolean) {
            binding.apply {
                if (isSelected) {
                    selectionOverlay.visibility = View.VISIBLE
                    ivSelection.visibility = View.VISIBLE
                    unselectedCircle.visibility = View.GONE
                } else {
                    selectionOverlay.visibility = View.GONE
                    ivSelection.visibility = View.GONE
                    unselectedCircle.visibility = View.VISIBLE
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
    }

    fun getSelectedPhotos(): List<GalleryPhoto> {
        return currentList.filter { selectedPhotos.contains(it.id) }
    }

    private class GalleryPhotoDiffCallback : DiffUtil.ItemCallback<GalleryPhoto>() {
        override fun areItemsTheSame(oldItem: GalleryPhoto, newItem: GalleryPhoto): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: GalleryPhoto, newItem: GalleryPhoto): Boolean {
            return oldItem == newItem
        }
    }
}

