package com.photovault.locker.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.photovault.locker.R
import com.photovault.locker.databinding.ItemAlbumBinding
import com.photovault.locker.models.Album
import java.text.SimpleDateFormat
import java.util.*

class AlbumAdapter(
    private val onAlbumClick: (Album) -> Unit,
    private val onAlbumLongClick: (Album) -> Unit
) : ListAdapter<Album, AlbumAdapter.AlbumViewHolder>(AlbumDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val binding = ItemAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlbumViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AlbumViewHolder(private val binding: ItemAlbumBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(album: Album) {
            binding.apply {
                tvAlbumName.text = album.name
                
                // Format created date
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                tvCreatedDate.text = "Created on ${dateFormat.format(album.createdDate)}"
                
                // Set photo count
                val photoCountText = if (album.photoCount == 1) {
                    "1 photo"
                } else {
                    "${album.photoCount} photos"
                }
                tvPhotoCount.text = photoCountText
                
                // Load cover image
                if (album.coverPhotoPath != null) {
                    Glide.with(ivAlbumCover.context)
                        .load(album.coverPhotoPath)
                        .centerCrop()
                        .placeholder(R.drawable.ic_photo_album)
                        .error(R.drawable.ic_photo_album)
                        .into(ivAlbumCover)
                } else {
                    ivAlbumCover.setImageResource(R.drawable.ic_photo_album)
                }
                
                // Set click listeners
                root.setOnClickListener { onAlbumClick(album) }
                root.setOnLongClickListener { 
                    onAlbumLongClick(album)
                    true
                }
            }
        }
    }

    private class AlbumDiffCallback : DiffUtil.ItemCallback<Album>() {
        override fun areItemsTheSame(oldItem: Album, newItem: Album): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Album, newItem: Album): Boolean {
            return oldItem == newItem
        }
    }
}




