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
        android.util.Log.d("GalleryPhotoAdapter", "onCreateViewHolder called")
        val binding = ItemGalleryPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GalleryPhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GalleryPhotoViewHolder, position: Int) {
        android.util.Log.d("GalleryPhotoAdapter", "onBindViewHolder called for position $position")
        holder.bind(getItem(position))
    }

    inner class GalleryPhotoViewHolder(private val binding: ItemGalleryPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(photo: GalleryPhoto) {
            android.util.Log.d("GalleryPhotoAdapter", "Binding photo: ${photo.name}, URI: ${photo.uri}")
            binding.apply {
                // Load photo
                Glide.with(ivGalleryPhoto.context)
                    .load(photo.uri)
                    .centerCrop()
                    .placeholder(R.drawable.ic_photo)
                    .error(R.drawable.ic_photo)
                    .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                        override fun onLoadFailed(e: com.bumptech.glide.load.engine.GlideException?, model: Any?, target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?, isFirstResource: Boolean): Boolean {
                            android.util.Log.e("GalleryPhotoAdapter", "Glide load failed for URI: ${photo.uri}, error: $e")
                            return false
                        }
                        
                        override fun onResourceReady(resource: android.graphics.drawable.Drawable?, model: Any?, target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?, dataSource: com.bumptech.glide.load.DataSource?, isFirstResource: Boolean): Boolean {
                            android.util.Log.d("GalleryPhotoAdapter", "Glide loaded successfully for URI: ${photo.uri}")
                            return false
                        }
                    })
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

    fun getSelectedCount(): Int {
        return selectedPhotos.size
    }

    fun clearSelections() {
        selectedPhotos.clear()
        notifyDataSetChanged()
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

