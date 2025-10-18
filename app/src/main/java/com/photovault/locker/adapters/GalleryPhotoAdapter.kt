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
    private var isDragSelecting = false
    private var dragSelectMode: Boolean? = null // true = select, false = deselect, null = not determined yet

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
    
    fun startDragSelection() {
        isDragSelecting = true
        dragSelectMode = null
    }
    
    fun endDragSelection() {
        isDragSelecting = false
        dragSelectMode = null
    }
    
    fun handleDragSelection(position: Int) {
        if (!isDragSelecting || position < 0 || position >= currentList.size) return
        
        val photo = getItem(position)
        val isCurrentlySelected = selectedPhotos.contains(photo.id)
        
        // Determine drag mode on first item
        if (dragSelectMode == null) {
            dragSelectMode = !isCurrentlySelected // If first item is unselected, we're selecting; otherwise deselecting
        }
        
        // Apply the drag mode
        if (dragSelectMode == true && !isCurrentlySelected) {
            // Select mode: add if not selected
            selectedPhotos.add(photo.id)
            notifyItemChanged(position)
            onSelectionChanged(selectedPhotos.size)
        } else if (dragSelectMode == false && isCurrentlySelected) {
            // Deselect mode: remove if selected
            selectedPhotos.remove(photo.id)
            notifyItemChanged(position)
            onSelectionChanged(selectedPhotos.size)
        }
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

