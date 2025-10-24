package com.photovault.locker.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.photovault.locker.R
import com.photovault.locker.databinding.ItemPhotoViewBinding
import com.photovault.locker.models.Photo

class PhotoViewPagerAdapter(
    private val photos: List<Photo>,
    private val onPhotoClick: () -> Unit
) : RecyclerView.Adapter<PhotoViewPagerAdapter.PhotoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoViewBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photos[position])
    }

    override fun getItemCount(): Int = photos.size

    inner class PhotoViewHolder(private val binding: ItemPhotoViewBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(photo: Photo) {
            binding.apply {
                Glide.with(photoView.context)
                    .load(photo.filePath)
                    .fitCenter()
                    .placeholder(R.drawable.ic_photo)
                    .error(R.drawable.ic_photo)
                    .into(photoView)

                // Add click listener to toggle UI visibility
                photoView.setOnClickListener {
                    onPhotoClick()
                }
            }
        }
    }
}




