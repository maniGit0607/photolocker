package com.photovault.locker.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.photovault.locker.R
import com.photovault.locker.adapters.PhotoAdapter
import com.photovault.locker.databinding.ActivityFavoritesBinding
import com.photovault.locker.models.Photo
import com.photovault.locker.viewmodels.FavoritesViewModel

class FavoritesActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityFavoritesBinding
    private lateinit var viewModel: FavoritesViewModel
    private lateinit var photoAdapter: PhotoAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoritesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupViewModel()
        setupRecyclerView()
        observeData()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[FavoritesViewModel::class.java]
    }
    
    private fun setupRecyclerView() {
        photoAdapter = PhotoAdapter(
            onPhotoClick = { photo, position ->
                // For favorites, we need to open the photo in its original album context
                // We'll navigate to PhotoViewActivity with the album context
                val intent = Intent(this, PhotoViewActivity::class.java).apply {
                    putExtra("album_id", photo.albumId)
                    putExtra("photo_id", photo.id)
                    putExtra("photo_position", position)
                }
                startActivity(intent)
            },
            onPhotoLongClick = { photo ->
                // Not needed for favorites view - we don't support selection mode here
            },
            onSetCoverPhoto = { photo ->
                // Not needed for favorites view
            },
            onSelectionModeChanged = { isSelectionMode ->
                // Not needed for favorites view
            },
            context = this
        )
        
        binding.rvFavorites.apply {
            layoutManager = GridLayoutManager(this@FavoritesActivity, 3)
            adapter = photoAdapter
        }
    }
    
    private fun observeData() {
        viewModel.favoritePhotos.observe(this) { photos ->
            photoAdapter.submitList(photos)
            
            if (photos.isEmpty()) {
                binding.rvFavorites.visibility = View.GONE
                binding.llEmptyState.visibility = View.VISIBLE
            } else {
                binding.rvFavorites.visibility = View.VISIBLE
                binding.llEmptyState.visibility = View.GONE
            }
        }
    }
}

