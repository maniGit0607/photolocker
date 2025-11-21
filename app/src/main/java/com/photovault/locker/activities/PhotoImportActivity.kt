package com.photovault.locker.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View

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
import com.google.android.gms.ads.interstitial.InterstitialAd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoImportActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPhotoImportBinding
    private lateinit var viewModel: PhotoImportViewModel
    private lateinit var galleryAdapter: GalleryPhotoAdapter
    
    private var albumId: Long = -1
    private var albumName: String = ""
    
    // Interstitial Ad
    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialAdShowing = false
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

            super.onCreate(savedInstanceState)
            binding = ActivityPhotoImportBinding.inflate(layoutInflater)
            setContentView(binding.root)
            

            getIntentExtras()
            

            setupToolbar()
            setupViewModel()
            setupRecyclerView()
            setupListeners()
            setupAds()
            

            if (PermissionUtils.hasStoragePermissions(this)) {

                loadGalleryPhotos()
            } else {

                requestPermissions()
            }
            

        } catch (e: Exception) {

            finish()
        }
    }
    
    private fun getIntentExtras() {
        albumId = intent.getLongExtra("album_id", -1)
        albumName = intent.getStringExtra("album_name") ?: "Album"
        

        
        if (albumId == -1L) {

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
            
            // Add drag selection touch listener with scroll detection
            addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                private var isDragging = false
                private var lastDraggedPosition = -1
                private var initialX = 0f
                private var initialY = 0f
                private var hasMovedBeyondSlop = false
                private val touchSlop = android.view.ViewConfiguration.get(this@PhotoImportActivity).scaledTouchSlop
                
                override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                    when (e.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            // Reset state
                            isDragging = false
                            hasMovedBeyondSlop = false
                            initialX = e.x
                            initialY = e.y
                            lastDraggedPosition = -1
                            
                            // Check if touch is on an item
                            val view = rv.findChildViewUnder(e.x, e.y)
                            if (view != null) {
                                val position = rv.getChildAdapterPosition(view)
                                if (position != RecyclerView.NO_POSITION) {
                                    lastDraggedPosition = position
                                }
                            }
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            // Only start drag selection if moved beyond touch slop
                            if (!hasMovedBeyondSlop) {
                                val dx = Math.abs(e.x - initialX)
                                val dy = Math.abs(e.y - initialY)
                                
                                // Check if movement exceeds touch slop
                                if (dx > touchSlop || dy > touchSlop) {
                                    hasMovedBeyondSlop = true
                                    
                                    // Only enable drag selection if horizontal movement is significant
                                    // or if moving within the same row (not scrolling vertically)
                                    if (dx > dy * 0.5f) {
                                        // More horizontal than vertical - likely drag selection
                                        isDragging = true
                                        galleryAdapter.startDragSelection()
                                        if (lastDraggedPosition != -1) {
                                            galleryAdapter.handleDragSelection(lastDraggedPosition)
                                        }
                                    }
                                    // If dy > dx, it's a scroll gesture - don't start drag selection
                                }
                            }
                            
                            // Handle drag over items only if in drag mode
                            if (isDragging) {
                                val view = rv.findChildViewUnder(e.x, e.y)
                                if (view != null) {
                                    val position = rv.getChildAdapterPosition(view)
                                    if (position != RecyclerView.NO_POSITION && position != lastDraggedPosition) {
                                        galleryAdapter.handleDragSelection(position)
                                        lastDraggedPosition = position
                                    }
                                }
                            }
                        }
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                            if (isDragging) {
                                galleryAdapter.endDragSelection()
                                isDragging = false
                                hasMovedBeyondSlop = false
                                lastDraggedPosition = -1
                                return true // Consume the event to prevent click
                            }
                            // Reset state
                            isDragging = false
                            hasMovedBeyondSlop = false
                            lastDraggedPosition = -1
                        }
                    }
                    return false // Don't intercept, let RecyclerView handle scrolling
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

                
                // Set result to indicate photos were imported successfully with count and gallery photos
                val resultIntent = Intent().apply {
                    putExtra("imported_count", count)
                    putExtra("imported_gallery_photos", importedGalleryPhotos.toTypedArray())
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            } else {

                binding.btnImport.isEnabled = true
                binding.btnImport.text = getString(R.string.import_photos)
            }
        }
        
        
        viewModel.error.observe(this) { error ->
            if (error.isNotEmpty()) {

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

                    }
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.llLoading.visibility = View.GONE
                    binding.llEmptyState.visibility = View.VISIBLE

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

                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .show()
    }
    
    private fun setupAds() {
        // Initialize AdMob with consent handling
        AdManager.initializeWithConsent(
            context = this,
            onConsentReceived = { hasConsent ->
                // Check if it's time to show ad based on frequency (every 3rd import)
                if (AdManager.shouldShowPhotoImportAd(this)) {
                    loadInterstitialAd()
                } else {
                    hasShownAdOnEntry = true // Skip ad this time
                }
                android.util.Log.d("PhotoImportActivity", "Consent received: $hasConsent")
            },
            onConsentError = { error ->
                // Still check for ads even if consent fails (fallback to non-personalized)
                if (AdManager.shouldShowPhotoImportAd(this)) {
                    loadInterstitialAd()
                } else {
                    hasShownAdOnEntry = true // Skip ad this time
                }
                android.util.Log.e("PhotoImportActivity", "Consent error: $error")
            }
        )
    }
    
    private fun loadInterstitialAd() {
        if (hasShownAdOnEntry || isInterstitialAdShowing) return
        
        AdManager.loadInterstitialAd(
            context = this,
            onAdLoaded = { ad ->
                interstitialAd = ad
                // Show the ad immediately after loading
                showInterstitialAd()
            },
            onAdFailedToLoad = { error ->
                // Failed to load ad, continue anyway
                hasShownAdOnEntry = true
            }
        )
    }
    
    private fun showInterstitialAd() {
        if (isInterstitialAdShowing || interstitialAd == null || hasShownAdOnEntry) return
        
        isInterstitialAdShowing = true
        hasShownAdOnEntry = true
        
        AdManager.showInterstitialAd(
            activity = this,
            interstitialAd = interstitialAd,
            onAdDismissed = {
                // Ad dismissed, reset state
                isInterstitialAdShowing = false
                interstitialAd = null
            }
        )
    }
    
}

