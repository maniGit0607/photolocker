package com.photovault.locker.activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.photovault.locker.R
import com.photovault.locker.adapters.PhotoViewPagerAdapter
import com.photovault.locker.databinding.ActivityPhotoViewBinding
import com.photovault.locker.models.Photo
import com.photovault.locker.viewmodels.PhotoViewViewModel
import java.text.SimpleDateFormat
import java.util.*

class PhotoViewActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPhotoViewBinding
    private lateinit var viewModel: PhotoViewViewModel
    private lateinit var pagerAdapter: PhotoViewPagerAdapter
    
    private var albumId: Long = -1
    private var photoId: Long = -1
    private var initialPosition: Int = 0
    private var photos = listOf<Photo>()
    private var overlayVisible = true
    private var isInitialLoad = true
    private var isFavoritesMode = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        getIntentExtras()
        setupViewModel()
        setupListeners()
        observeData()
    }
    
    private fun getIntentExtras() {
        albumId = intent.getLongExtra("album_id", -1)
        photoId = intent.getLongExtra("photo_id", -1)
        initialPosition = intent.getIntExtra("photo_position", 0)
        isFavoritesMode = intent.getBooleanExtra("is_favorites_mode", false)
        
        // If in favorites mode, albumId is not required
        if (photoId == -1L) {
            finish()
            return
        }
        
        if (!isFavoritesMode && albumId == -1L) {
            finish()
            return
        }
    }
    
    private fun setupViewModel() {
        val factory = PhotoViewViewModel.Factory(application, albumId, isFavoritesMode)
        viewModel = ViewModelProvider(this, factory)[PhotoViewViewModel::class.java]
    }
    
    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        binding.btnFavorite.setOnClickListener {
            toggleFavoriteForCurrentPhoto()
        }
        
        binding.btnSetCover.setOnClickListener {
            setCurrentPhotoAsCover()
        }
        
        // Hide the set cover button in favorites mode
        if (isFavoritesMode) {
            binding.btnSetCover.visibility = View.GONE
        }
        
        binding.btnDelete.setOnClickListener {
            showDeleteConfirmation()
        }
        
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updatePhotoInfo(position)
                updateFavoriteIcon(position)
            }
        })
    }
    
    private fun observeData() {
        viewModel.photos.observe(this) { photosList ->
            val currentPosition = if (!isInitialLoad) binding.viewPager.currentItem else -1
            
            photos = photosList
            setupViewPager()
            
            if (isInitialLoad) {
                // Find the position of the selected photo on initial load
                val position = photos.indexOfFirst { it.id == photoId }
                if (position >= 0) {
                    binding.viewPager.setCurrentItem(position, false)
                    updatePhotoInfo(position)
                    updateFavoriteIcon(position)
                }
                isInitialLoad = false
            } else {
                // Preserve current position on subsequent updates (e.g., when toggling favorite)
                if (currentPosition >= 0 && currentPosition < photos.size) {
                    binding.viewPager.setCurrentItem(currentPosition, false)
                    updatePhotoInfo(currentPosition)
                    updateFavoriteIcon(currentPosition)
                }
            }
        }
        
        viewModel.error.observe(this) { error ->
            if (error.isNotEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun setupViewPager() {
        pagerAdapter = PhotoViewPagerAdapter(photos) {
            toggleOverlayVisibility()
        }
        
        binding.viewPager.adapter = pagerAdapter
    }
    
    private fun updatePhotoInfo(position: Int) {
        if (position < photos.size) {
            val photo = photos[position]
            val currentPos = position + 1
            val totalCount = photos.size
            
            binding.tvPhotoCounter.text = getString(R.string.photo_of, currentPos, totalCount)
            binding.tvPhotoName.text = photo.originalName
            
            // Format photo info
            val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
            val formattedDate = dateFormat.format(photo.importedDate)
            val sizeInMB = photo.fileSize / (1024.0 * 1024.0)
            val dimensions = if (photo.width > 0 && photo.height > 0) {
                " • ${photo.width} × ${photo.height}"
            } else ""
            
            binding.tvPhotoInfo.text = "$formattedDate • ${String.format("%.1f MB", sizeInMB)}$dimensions"
        }
    }
    
    private fun updateFavoriteIcon(position: Int) {
        if (position < photos.size) {
            val photo = photos[position]
            if (photo.isFavorite) {
                binding.btnFavorite.setImageResource(R.drawable.ic_heart_filled)
            } else {
                binding.btnFavorite.setImageResource(R.drawable.ic_heart)
            }
        }
    }
    
    private fun toggleFavoriteForCurrentPhoto() {
        val currentPosition = binding.viewPager.currentItem
        if (currentPosition < photos.size) {
            val currentPhoto = photos[currentPosition]
            viewModel.toggleFavorite(currentPhoto.id, currentPhoto.isFavorite)
            
            val message = if (currentPhoto.isFavorite) {
                "Removed from favorites"
            } else {
                "Added to favorites"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setCurrentPhotoAsCover() {
        val currentPosition = binding.viewPager.currentItem
        if (currentPosition < photos.size) {
            val currentPhoto = photos[currentPosition]
            viewModel.setCoverPhoto(currentPhoto)
            Toast.makeText(this, "Set as album cover", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun toggleOverlayVisibility() {
        overlayVisible = !overlayVisible
        
        val visibility = if (overlayVisible) View.VISIBLE else View.GONE
        binding.topOverlay.visibility = visibility
        binding.bottomOverlay.visibility = visibility
    }
    
    private fun showDeleteConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_photo))
            .setMessage(getString(R.string.delete_photo_confirmation))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deleteCurrentPhoto()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun deleteCurrentPhoto() {
        val currentPosition = binding.viewPager.currentItem
        if (currentPosition < photos.size) {
            val currentPhoto = photos[currentPosition]
            viewModel.deletePhoto(currentPhoto)
            
            Toast.makeText(this, "Photo deleted", Toast.LENGTH_SHORT).show()
            
            // If this was the only photo, finish the activity
            if (photos.size == 1) {
                finish()
            }
        }
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Hide system UI for immersive experience using modern API
            hideSystemUI()
        }
    }
    
    private fun hideSystemUI() {
        // Use WindowInsetsController for API 30+ (replaces deprecated systemUiVisibility)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

