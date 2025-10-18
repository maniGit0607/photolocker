package com.photovault.locker.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.photovault.locker.R
import com.photovault.locker.adapters.GalleryPhotoAdapter
import com.photovault.locker.databinding.ActivityPhotoImportBinding
import com.photovault.locker.models.GalleryPhoto
import com.photovault.locker.utils.AdManager
import com.photovault.locker.utils.PermissionUtils
import com.photovault.locker.viewmodels.PhotoImportViewModel
import com.google.android.gms.ads.rewarded.RewardedAd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoImportActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPhotoImportBinding
    private lateinit var viewModel: PhotoImportViewModel
    private lateinit var galleryAdapter: GalleryPhotoAdapter
    
    private var albumId: Long = -1
    private var albumName: String = ""
    
    // Rewarded Ad
    private var rewardedAd: RewardedAd? = null
    private var isRewardedAdShowing = false
    private var hasShownAdOnEntry = false
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            loadGalleryPhotos()
        } else {
            showPermissionDeniedDialog()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Toast.makeText(this, "PhotoImportActivity onCreate started", Toast.LENGTH_SHORT).show()
            super.onCreate(savedInstanceState)
            binding = ActivityPhotoImportBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            Toast.makeText(this, "Getting intent extras", Toast.LENGTH_SHORT).show()
            getIntentExtras()
            
            Toast.makeText(this, "Setting up UI", Toast.LENGTH_SHORT).show()
            setupToolbar()
            setupViewModel()
            setupRecyclerView()
            setupListeners()
            setupAds()
            
            Toast.makeText(this, "Checking permissions", Toast.LENGTH_SHORT).show()
            if (PermissionUtils.hasStoragePermissions(this)) {
                Toast.makeText(this, "Permissions granted, loading photos", Toast.LENGTH_SHORT).show()
                loadGalleryPhotos()
            } else {
                Toast.makeText(this, "Requesting permissions", Toast.LENGTH_SHORT).show()
                requestPermissions()
            }
            
            Toast.makeText(this, "PhotoImportActivity onCreate completed", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error in PhotoImportActivity onCreate: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun getIntentExtras() {
        albumId = intent.getLongExtra("album_id", -1)
        albumName = intent.getStringExtra("album_name") ?: "Album"
        
        Toast.makeText(this, "Album ID: $albumId, Album Name: $albumName", Toast.LENGTH_SHORT).show()
        
        if (albumId == -1L) {
            Toast.makeText(this, "Invalid album ID, finishing activity", Toast.LENGTH_LONG).show()
            finish()
            return
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Select Photos"
            setDisplayHomeAsUpEnabled(true)
        }
        
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }
    
    private fun setupViewModel() {
        val factory = PhotoImportViewModel.Factory(application, albumId, albumName)
        viewModel = ViewModelProvider(this, factory)[PhotoImportViewModel::class.java]
    }
    
    private fun setupRecyclerView() {
        android.util.Log.d("PhotoImportActivity", "Setting up RecyclerView")
        galleryAdapter = GalleryPhotoAdapter { selectedCount ->
            updateUI(selectedCount)
        }
        
        binding.rvGalleryPhotos.apply {
            layoutManager = GridLayoutManager(this@PhotoImportActivity, 3)
            adapter = galleryAdapter
            
            // Add drag selection touch listener
            addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                private var isDragging = false
                private var lastDraggedPosition = -1
                
                override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                    when (e.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            // Check if touch is on an item
                            val view = rv.findChildViewUnder(e.x, e.y)
                            if (view != null) {
                                val position = rv.getChildAdapterPosition(view)
                                if (position != RecyclerView.NO_POSITION) {
                                    isDragging = false // Will become true on ACTION_MOVE
                                    lastDraggedPosition = position
                                }
                            }
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            if (!isDragging) {
                                isDragging = true
                                galleryAdapter.startDragSelection()
                                // Handle the first item
                                if (lastDraggedPosition != -1) {
                                    galleryAdapter.handleDragSelection(lastDraggedPosition)
                                }
                            }
                            
                            // Handle drag over items
                            val view = rv.findChildViewUnder(e.x, e.y)
                            if (view != null) {
                                val position = rv.getChildAdapterPosition(view)
                                if (position != RecyclerView.NO_POSITION && position != lastDraggedPosition) {
                                    galleryAdapter.handleDragSelection(position)
                                    lastDraggedPosition = position
                                }
                            }
                        }
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                            if (isDragging) {
                                galleryAdapter.endDragSelection()
                                isDragging = false
                                lastDraggedPosition = -1
                                return true // Consume the event to prevent click
                            }
                        }
                    }
                    return false // Don't intercept, let click events through
                }
                
                override fun onTouchEvent(rv: RecyclerView, e: android.view.MotionEvent) {
                    // Not needed for our implementation
                }
                
                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
                    // Not needed for our implementation
                }
            })
            
            // Debug RecyclerView layout
            post {
                android.util.Log.d("PhotoImportActivity", "RecyclerView dimensions: ${width}x${height}")
                android.util.Log.d("PhotoImportActivity", "RecyclerView visibility: $visibility")
                android.util.Log.d("PhotoImportActivity", "RecyclerView adapter item count: ${adapter?.itemCount}")
            }
        }
    }
    
    private fun setupListeners() {
        binding.btnImport.setOnClickListener {
            importSelectedPhotos()
        }
        
        // Observe import progress
        viewModel.importProgress.observe(this) { progress ->
            // You could show a progress dialog here
        }
        
        viewModel.importComplete.observe(this) { (success, count, importedGalleryPhotos) ->
            if (success) {
                val message = getString(R.string.photos_imported, count)
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                
                // Set result to indicate photos were imported successfully with count and gallery photos
                val resultIntent = Intent().apply {
                    putExtra("imported_count", count)
                    putExtra("imported_gallery_photos", importedGalleryPhotos.toTypedArray())
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            } else {
                Toast.makeText(this, getString(R.string.import_failed), Toast.LENGTH_LONG).show()
                binding.btnImport.isEnabled = true
                binding.btnImport.text = getString(R.string.import_photos)
            }
        }
        
        
        viewModel.error.observe(this) { error ->
            if (error.isNotEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        }
        
    }
    
    private fun loadGalleryPhotos() {
        lifecycleScope.launch {
            try {
                val photos = withContext(Dispatchers.IO) {
                    loadPhotosFromMediaStore()
                }
                
                withContext(Dispatchers.Main) {
                    binding.llLoading.visibility = View.GONE
                    
                    if (photos.isNotEmpty()) {
                        galleryAdapter.submitList(photos)
                        
                        // Ensure proper visibility states
                        binding.llLoading.visibility = View.GONE
                        binding.llEmptyState.visibility = View.GONE
                        binding.rvGalleryPhotos.visibility = View.VISIBLE
                    } else {
                        binding.llEmptyState.visibility = View.VISIBLE
                        binding.rvGalleryPhotos.visibility = View.GONE
                        Toast.makeText(this@PhotoImportActivity, "No photos found in gallery. Add some photos to the emulator first!", Toast.LENGTH_LONG).show()
                    }
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.llLoading.visibility = View.GONE
                    binding.llEmptyState.visibility = View.VISIBLE
                    Toast.makeText(this@PhotoImportActivity, "Failed to load photos", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun loadPhotosFromMediaStore(): List<GalleryPhoto> {
        val photos = mutableListOf<GalleryPhoto>()
        
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED
        )
        
        val selection = "${MediaStore.Images.Media.SIZE} > ?"
        val selectionArgs = arrayOf("0")
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val size = cursor.getLong(sizeColumn)
                val date = cursor.getLong(dateColumn)
                
                val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                
                photos.add(GalleryPhoto(id, uri, name, size, date))
            }
        }
        
        return photos
    }
    
    private fun updateUI(selectedCount: Int) {
        if (selectedCount > 0) {
            binding.tvSelectedCount.text = "$selectedCount photo${if (selectedCount == 1) "" else "s"} selected"
            binding.btnImport.isEnabled = true
        } else {
            binding.tvSelectedCount.text = "Select photos to import"
            binding.btnImport.isEnabled = false
        }
    }
    
    private fun importSelectedPhotos() {
        val selectedPhotos = galleryAdapter.getSelectedPhotos()
        if (selectedPhotos.isNotEmpty()) {
            binding.btnImport.isEnabled = false
            binding.btnImport.text = "Importing..."
            
            viewModel.importPhotos(selectedPhotos)
        }
    }
    
    private fun requestPermissions() {
        requestPermissionLauncher.launch(PermissionUtils.getRequiredPermissions())
    }
    
    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Storage Permission Required")
            .setMessage("Storage permission is required to access and import photos. Please go to Settings > Apps > PhotoVault Locker > Permissions and enable storage/media access.")
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Please enable storage permission in system settings", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .show()
    }
    
    private fun setupAds() {
        // Initialize AdMob and load rewarded ad
        AdManager.initialize(this) {
            // Load rewarded ad after initialization
            loadRewardedAd()
        }
    }
    
    private fun loadRewardedAd() {
        if (hasShownAdOnEntry || isRewardedAdShowing) return
        
        AdManager.loadRewardedAd(
            context = this,
            onAdLoaded = { ad ->
                rewardedAd = ad
                // Show the ad immediately after loading
                showRewardedAd()
            },
            onAdFailedToLoad = { error ->
                // Failed to load ad, continue anyway
                hasShownAdOnEntry = true
            }
        )
    }
    
    private fun showRewardedAd() {
        if (isRewardedAdShowing || rewardedAd == null || hasShownAdOnEntry) return
        
        isRewardedAdShowing = true
        hasShownAdOnEntry = true
        
        AdManager.showRewardedAd(
            activity = this,
            rewardedAd = rewardedAd,
            onUserEarnedReward = {
                // User watched the ad and earned reward
                Toast.makeText(this, "Thank you for watching!", Toast.LENGTH_SHORT).show()
            },
            onAdDismissed = {
                // Ad dismissed, reset state
                isRewardedAdShowing = false
                rewardedAd = null
            }
        )
    }
    
}

