package com.photovault.locker.activities

import android.content.Intent
import android.os.Bundle
import android.view.View

import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.photovault.locker.R
import com.photovault.locker.adapters.PhotoAdapter
import com.photovault.locker.databinding.ActivityAlbumViewBinding
import com.photovault.locker.models.GalleryPhoto
import com.photovault.locker.models.Photo
import com.photovault.locker.utils.AdManager
import com.photovault.locker.utils.PermissionUtils
import com.photovault.locker.viewmodels.AlbumViewViewModel
import com.google.android.gms.ads.interstitial.InterstitialAd
import kotlinx.coroutines.launch

class AlbumViewActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAlbumViewBinding
    private lateinit var viewModel: AlbumViewViewModel
    private lateinit var photoAdapter: PhotoAdapter
    
    private var albumId: Long = -1
    private var albumName: String = ""
    
    // Store imported gallery photos for potential deletion
    private var importedGalleryPhotos: List<GalleryPhoto> = emptyList()
    
    // Flag to prevent repeated permission requests
    private var isPermissionRequestInProgress = false
    
    // Grid size management
    private var currentGridSize = GridSize.MEDIUM
    private lateinit var gridLayoutManager: GridLayoutManager
    
    // Interstitial Ad
    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialAdShowing = false
    
    enum class GridSize(val columnCount: Int, val displayName: String) {
        SMALL(5, "Small"),      // 75% smaller than medium (3 -> 5 columns)
        MEDIUM(3, "Medium"),    // Current size
        LARGE(2, "Large")       // 150% larger than medium (3 -> 2 columns)
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startPhotoImportActivity()
        } else {

        }
    }
    
    private val requestDeletePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        android.util.Log.d("AlbumViewActivity", "Permission result received: ${result.resultCode}")
        
        if (result.resultCode == RESULT_OK) {
            // Permission granted, deletion will be completed by system
            android.util.Log.d("AlbumViewActivity", "Permission granted, automatic deletion will be completed by system")

            
            // Don't retry - the system handles the deletion automatically
            // The MediaStore batch delete request will complete the deletion
        } else {
            android.util.Log.w("AlbumViewActivity", "Permission denied or cancelled for automatic deletion")
            // Don't show error toast for automatic deletion to avoid interrupting user experience
        }
        
        // Reset the flag
        isPermissionRequestInProgress = false
    }
    
    private val photoImportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Photos were imported successfully, refresh the view
            viewModel.refreshPhotos()
            
            // Get the imported count and gallery photos
            val importedCount = result.data?.getIntExtra("imported_count", 0) ?: 0
            val galleryPhotos = result.data?.getParcelableArrayExtra("imported_gallery_photos")
                ?.mapNotNull { it as? GalleryPhoto } ?: emptyList()
            
            if (importedCount > 0 && galleryPhotos.isNotEmpty()) {
                // Store the gallery photos for potential deletion
                importedGalleryPhotos = galleryPhotos
                
                // Show dialog after 3 seconds asking user if they want to delete from gallery
                binding.rvPhotos.postDelayed({
                    showDeleteFromGalleryDialog(galleryPhotos)
                }, 3000) // 3 second delay
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlbumViewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        getIntentExtras()
        setupViewModel()
        setupToolbar()
        setupRecyclerView()
        setupFab()
        setupActionButtons()
        observeData()
        setupAds()
        checkAndShowInterstitialAd()
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
        // Set up custom toolbar
        val tvAlbumTitle = findViewById<android.widget.TextView>(R.id.tvAlbumTitle)
        val tvSelectionCount = findViewById<android.widget.TextView>(R.id.tvSelectionCount)
        val ivBack = findViewById<android.widget.ImageView>(R.id.ivBack)
        val ivMenu = findViewById<android.widget.ImageView>(R.id.ivMenu)
        
        tvAlbumTitle.text = albumName
        tvSelectionCount.visibility = android.view.View.GONE
        
        ivBack.setOnClickListener {
            onBackPressed()
        }
        
        ivMenu.setOnClickListener {
            android.util.Log.d("AlbumViewActivity", "Menu button clicked")
            showGridSizePopupMenu(ivMenu)
        }
        
        // Load cover photo
        loadCoverPhoto()
    }
    
    private fun setupViewModel() {
        val factory = AlbumViewViewModel.Factory(application, albumId)
        viewModel = ViewModelProvider(this, factory)[AlbumViewViewModel::class.java]
    }
    
    private fun setupRecyclerView() {
        photoAdapter = PhotoAdapter(
            onPhotoClick = { photo, position ->
                // Navigate to PhotoViewActivity
                val intent = Intent(this, PhotoViewActivity::class.java).apply {
                    putExtra("album_id", albumId)
                    putExtra("photo_id", photo.id)
                    putExtra("photo_position", position)
                }
                startActivity(intent)
            },
            onPhotoLongClick = { photo ->
                // Handle long click for selection mode
                updateSelectionCount()
            },
            onSetCoverPhoto = { photo ->
                // Set photo as album cover
                setPhotoAsCover(photo)
            },
            onSelectionModeChanged = { isSelectionMode ->
                // Handle selection mode changes
                updateSelectionCount()
                val tvAlbumTitle = findViewById<android.widget.TextView>(R.id.tvAlbumTitle)
                val tvSelectionCount = findViewById<android.widget.TextView>(R.id.tvSelectionCount)
                if (isSelectionMode) {
                    tvAlbumTitle.visibility = android.view.View.GONE
                    tvSelectionCount.visibility = android.view.View.VISIBLE
                    showActionButtons()
                } else {
                    tvAlbumTitle.visibility = android.view.View.VISIBLE
                    tvSelectionCount.visibility = android.view.View.GONE
                    hideActionButtons()
                }
            },
            context = this,
            true
        )
        
        // Initialize grid layout manager
        gridLayoutManager = GridLayoutManager(this, currentGridSize.columnCount)
        
        binding.rvPhotos.apply {
            layoutManager = gridLayoutManager
            adapter = photoAdapter
        }
    }
    
    private fun setupFab() {
        binding.fabAddPhotos.setOnClickListener {

            try {
                if (PermissionUtils.hasStoragePermissions(this)) {

                    startPhotoImportActivity()
                } else {

                    requestPermissionLauncher.launch(PermissionUtils.getRequiredPermissions())
                }
            } catch (e: Exception) {

            }
        }
    }
    
    private fun setupActionButtons() {
        val btnSelectAll = findViewById<android.widget.LinearLayout>(R.id.btnSelectAll)
        val btnExport = findViewById<android.widget.LinearLayout>(R.id.btnExport)
        val btnMove = findViewById<android.widget.LinearLayout>(R.id.btnMove)
        val btnDelete = findViewById<android.widget.LinearLayout>(R.id.btnDelete)
        val ivSelectAll = findViewById<android.widget.ImageView>(R.id.ivSelectAll)
        
        btnSelectAll.setOnClickListener {
            if (photoAdapter.isAllSelected()) {
                // Deselect all
                photoAdapter.disableSelectionMode()
            } else {
                // Select all
                photoAdapter.selectAll()
                updateSelectAllIcon()
            }
            updateSelectionCount()
        }
        
        btnExport.setOnClickListener {
            exportSelectedPhotos()
        }
        
        btnMove.setOnClickListener {
            showAlbumSelectionDialog()
        }
        
        btnDelete.setOnClickListener {
            moveSelectedPhotosToBin()
        }
    }
    
    private fun observeData() {
        viewModel.photos.observe(this) { photos ->
            android.util.Log.d("AlbumViewActivity", "Photos LiveData updated with ${photos.size} photos")
            android.util.Log.d("AlbumViewActivity", "Photos data: ${photos.map { it.originalName }}")
            
            photoAdapter.submitList(photos) {
                android.util.Log.d("AlbumViewActivity", "Adapter updated with ${photos.size} photos")
            }
            
            if (photos.isEmpty()) {
                binding.rvPhotos.visibility = View.GONE
                binding.llEmptyState.visibility = View.VISIBLE
                android.util.Log.d("AlbumViewActivity", "Showing empty state")
            } else {
                binding.rvPhotos.visibility = View.VISIBLE
                binding.llEmptyState.visibility = View.GONE
                android.util.Log.d("AlbumViewActivity", "Showing photos in RecyclerView")
                
                // Debug RecyclerView state
                android.util.Log.d("AlbumViewActivity", "RecyclerView visibility: ${binding.rvPhotos.visibility}")
                android.util.Log.d("AlbumViewActivity", "RecyclerView adapter: ${binding.rvPhotos.adapter}")
                android.util.Log.d("AlbumViewActivity", "RecyclerView item count: ${binding.rvPhotos.adapter?.itemCount}")
                android.util.Log.d("AlbumViewActivity", "RecyclerView layout manager: ${binding.rvPhotos.layoutManager}")
                
                // Debug RecyclerView dimensions
                binding.rvPhotos.post {
                    android.util.Log.d("AlbumViewActivity", "RecyclerView dimensions: ${binding.rvPhotos.width}x${binding.rvPhotos.height}")
                    android.util.Log.d("AlbumViewActivity", "RecyclerView child count: ${binding.rvPhotos.childCount}")
                }
            }
        }

        viewModel.error.observe(this) { error ->
            if (error.isNotEmpty()) {
                android.util.Log.e("AlbumViewActivity", "Error: $error")

            }
        }
        
        // Gallery deletion dialog is no longer needed - deletion is automatic
        
        // Observe gallery deletion result
        viewModel.galleryDeletionResult.observe(this) { (success, deletedCount) ->
            if (success) {
                if (deletedCount > 0) {
                    val message = if (deletedCount == 1) {
                        "✓ 1 photo removed from gallery"
                    } else {
                        "✓ $deletedCount photos removed from gallery"
                    }

                    android.util.Log.d("AlbumViewActivity", "Automatic gallery deletion completed: $deletedCount photos deleted")
                }
            } else {
                android.util.Log.w("AlbumViewActivity", "Automatic gallery deletion failed")
                // Don't show error toast for automatic deletion to avoid interrupting user experience
            }
        }
        
        // Observe permission requests for gallery deletion
        viewModel.permissionRequired.observe(this) { intentSenders ->
            android.util.Log.d("AlbumViewActivity", "Permission required observed: ${intentSenders.size} intent senders")
            android.util.Log.d("AlbumViewActivity", "Permission request in progress: $isPermissionRequestInProgress")
            
            if (intentSenders.isNotEmpty() && !isPermissionRequestInProgress) {
                // For batch deletion, we typically only need one permission request
                // as MediaStore handles multiple items in a single operation
                isPermissionRequestInProgress = true
                val intentSender = intentSenders.first()
                
                android.util.Log.d("AlbumViewActivity", "Launching permission request for intent sender: $intentSender")
                
                try {
                    val intentSenderRequest = androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                    requestDeletePermissionLauncher.launch(intentSenderRequest)
                    android.util.Log.d("AlbumViewActivity", "Permission request launched successfully")
                } catch (e: Exception) {
                    android.util.Log.e("AlbumViewActivity", "Error launching permission request: ${e.message}", e)
                    isPermissionRequestInProgress = false

                }
            }
        }
    }
    
    private fun startPhotoImportActivity() {
        val intent = Intent(this, PhotoImportActivity::class.java).apply {
            putExtra("album_id", albumId)
            putExtra("album_name", albumName)
        }
        photoImportLauncher.launch(intent)
    }

    private fun showGridSizePopupMenu(anchorView: android.view.View) {
        val popupMenu = android.widget.PopupMenu(this, anchorView)
        popupMenu.menuInflater.inflate(R.menu.album_menu, popupMenu.menu)
        
        // Set checkmarks for current grid size
        when (currentGridSize) {
            GridSize.SMALL -> popupMenu.menu.findItem(R.id.action_grid_small)?.isChecked = true
            GridSize.MEDIUM -> popupMenu.menu.findItem(R.id.action_grid_medium)?.isChecked = true
            GridSize.LARGE -> popupMenu.menu.findItem(R.id.action_grid_large)?.isChecked = true
        }
        
        popupMenu.setOnMenuItemClickListener { item ->
            android.util.Log.d("AlbumViewActivity", "Popup menu item clicked: ${item.title} (ID: ${item.itemId})")
            
            when (item.itemId) {
                R.id.action_grid_small -> {
                    android.util.Log.d("AlbumViewActivity", "Grid small clicked")
                    changeGridSize(GridSize.SMALL)
                    true
                }
                R.id.action_grid_medium -> {
                    android.util.Log.d("AlbumViewActivity", "Grid medium clicked")
                    changeGridSize(GridSize.MEDIUM)
                    true
                }
                R.id.action_grid_large -> {
                    android.util.Log.d("AlbumViewActivity", "Grid large clicked")
                    changeGridSize(GridSize.LARGE)
                    true
                }
                else -> false
            }
        }
        
        popupMenu.show()
    }
    
    private fun changeGridSize(newSize: GridSize) {
        if (currentGridSize != newSize) {
            currentGridSize = newSize
            gridLayoutManager.spanCount = newSize.columnCount
            photoAdapter.notifyDataSetChanged()
            invalidateOptionsMenu()

        }
    }

    override fun onBackPressed() {
        if (photoAdapter.isSelectionMode()) {
            photoAdapter.disableSelectionMode()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh photos when returning from other activities
        viewModel.refreshPhotos()
    }
    
    private fun setPhotoAsCover(photo: Photo) {
        viewModel.setCoverPhoto(photo)

        // Reload cover photo after setting
        loadCoverPhoto()
    }
    
    private fun loadCoverPhoto() {
        val ivCoverPhoto = findViewById<android.widget.ImageView>(R.id.ivCoverPhoto)
        
        viewModel.getCoverPhoto().observe(this) { coverPhotoPath ->
            if (coverPhotoPath != null) {
                ivCoverPhoto.visibility = android.view.View.VISIBLE
                com.bumptech.glide.Glide.with(this)
                    .load(coverPhotoPath)
                    .circleCrop()
                    .placeholder(R.drawable.ic_photo_album)
                    .error(R.drawable.ic_photo_album)
                    .into(ivCoverPhoto)
            } else {
                ivCoverPhoto.visibility = android.view.View.GONE
            }
        }
    }
    
    private fun moveSelectedPhotosToBin() {
        val selectedPhotos = photoAdapter.getSelectedPhotos()
        if (selectedPhotos.isNotEmpty()) {
            viewModel.movePhotosToBin(selectedPhotos)
            photoAdapter.disableSelectionMode()


        }
    }
    
    private fun showAlbumSelectionDialog() {
        val selectedPhotos = photoAdapter.getSelectedPhotos()
        if (selectedPhotos.isNotEmpty()) {
            // Use coroutine to get albums synchronously
            lifecycleScope.launch {
                try {
                    val albums = viewModel.getAllAlbumsExceptCurrentSync()
                    // Always show the dialog, even if no albums (user can create new album)
                    showAlbumSelectionDialog(albums, selectedPhotos)
                } catch (e: Exception) {

                }
            }
        }
    }
    
    private fun showAlbumSelectionDialog(albums: List<com.photovault.locker.models.Album>, selectedPhotos: List<Long>) {
        // Create a custom dialog with ListView
        val listView = android.widget.ListView(this)
        
        // Create a list with "Create new album" option at the top
        val albumNames = mutableListOf("+ Create new album")
        albumNames.addAll(albums.map { it.name })
        
        // Create adapter for the list
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_list_item_1, albumNames)
        listView.adapter = adapter
        
        // Create dialog
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Move to Album")
            .setMessage("Select destination album for ${selectedPhotos.size} photo(s)")
            .setView(listView)
            .setNegativeButton("Cancel", null)
            .create()
        
        // Set click listener for list items
        listView.setOnItemClickListener { _, _, position, _ ->
            if (position == 0) {
                // "Create new album" option clicked
                dialog.dismiss()
                showCreateAlbumAndMoveDialog(selectedPhotos)
            } else {
                // Existing album clicked (position - 1 because of the "Create new album" at index 0)
                val selectedAlbum = albums[position - 1]
                movePhotosToAlbum(selectedPhotos, selectedAlbum.id, selectedAlbum.name)
                dialog.dismiss()
            }
        }
        
        dialog.show()
        
        // Style the dialog
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.white)
            
            // Style title and message
            val titleView = window.findViewById<android.widget.TextView>(android.R.id.title)
            titleView?.setTextColor(android.graphics.Color.BLACK)
            
            val messageView = window.findViewById<android.widget.TextView>(android.R.id.message)
            messageView?.setTextColor(android.graphics.Color.BLACK)
            
            // Style list items after they're rendered
            listView.post {
                for (i in 0 until listView.childCount) {
                    val child = listView.getChildAt(i)
                    if (child is android.widget.TextView) {
                        child.setTextColor(android.graphics.Color.BLACK)
                        // Make "Create new album" option bold
                        if (i == 0) {
                            child.setTypeface(child.typeface, android.graphics.Typeface.BOLD)
                        }
                    }
                }
            }
        }
    }
    
    private fun showCreateAlbumAndMoveDialog(selectedPhotos: List<Long>) {
        val dialogBinding = com.photovault.locker.databinding.DialogCreateAlbumBinding.inflate(layoutInflater)
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()
        
        // Update dialog title text
        dialogBinding.root.findViewById<android.widget.TextView>(android.R.id.text1)?.let { titleView ->
            // The title TextView is the first TextView in the layout
        }
        
        dialogBinding.btnCreate.setOnClickListener {
            val albumName = dialogBinding.etAlbumName.text.toString().trim()
            
            if (albumName.isEmpty()) {
                dialogBinding.tilAlbumName.error = getString(R.string.album_name_required)
                return@setOnClickListener
            }
            
            // Create album and move photos
            viewModel.createAlbumAndMovePhotos(albumName, selectedPhotos) { success, message ->
                if (success) {
                    dialog.dismiss()
                    photoAdapter.disableSelectionMode()
                    updateSelectionCount()
                    invalidateOptionsMenu()

                } else {
                    dialogBinding.tilAlbumName.error = message
                }
            }
        }
        
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showDeleteFromGalleryDialog(galleryPhotos: List<GalleryPhoto>) {
        if (galleryPhotos.isEmpty()) return
        
        val message = if (galleryPhotos.size == 1) {
            "Delete imported photo from gallery?"
        } else {
            "Delete ${galleryPhotos.size} imported photos from gallery?"
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete from Gallery")
            .setMessage(message)
            .setPositiveButton("Yes") { _, _ ->
                android.util.Log.d("AlbumViewActivity", "User confirmed deletion of ${galleryPhotos.size} photos from gallery")
                viewModel.deleteImportedPhotosFromGallery(galleryPhotos)
            }
            .setNegativeButton("No") { dialog, _ ->
                android.util.Log.d("AlbumViewActivity", "User declined deletion from gallery")
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }
    
    private fun movePhotosToAlbum(photoIds: List<Long>, targetAlbumId: Long, targetAlbumName: String) {
        viewModel.movePhotosToAlbum(photoIds, targetAlbumId)
        photoAdapter.disableSelectionMode()
        updateSelectionCount()
        invalidateOptionsMenu()

    }
    
    private fun showActionButtons() {
        val selectionButtons = findViewById<android.widget.LinearLayout>(R.id.selectionActionButtons)
        selectionButtons.visibility = android.view.View.VISIBLE
        binding.fabAddPhotos.visibility = android.view.View.GONE
    }
    
    private fun hideActionButtons() {
        val selectionButtons = findViewById<android.widget.LinearLayout>(R.id.selectionActionButtons)
        selectionButtons.visibility = android.view.View.GONE
        binding.fabAddPhotos.visibility = android.view.View.VISIBLE
    }
    
    private fun updateSelectionCount() {
        val tvSelectionCount = findViewById<android.widget.TextView>(R.id.tvSelectionCount)
        val selectedCount = photoAdapter.getSelectedCount()
        tvSelectionCount.text = if (selectedCount == 1) "1 selected" else "$selectedCount selected"
        updateSelectAllIcon()
    }
    
    private fun updateSelectAllIcon() {
        val ivSelectAll = findViewById<android.widget.ImageView>(R.id.ivSelectAll)
        if (photoAdapter.isAllSelected()) {
            ivSelectAll.setImageResource(R.drawable.ic_check_circle)
        } else {
            ivSelectAll.setImageResource(R.drawable.ic_select_all)
        }
    }
    
    private fun exportSelectedPhotos() {
        val selectedPhotos = photoAdapter.getSelectedPhotos()
        if (selectedPhotos.isEmpty()) {

            return
        }
        
        // Check if we have storage permissions
        if (!PermissionUtils.hasStoragePermissions(this)) {

            requestPermissionLauncher.launch(PermissionUtils.getRequiredPermissions())
            return
        }
        
        // Export photos in a coroutine
        lifecycleScope.launch {
            try {
                val photos = viewModel.photos.value ?: emptyList()
                val photosToExport = photos.filter { selectedPhotos.contains(it.id) }
                
                if (photosToExport.isEmpty()) {

                    return@launch
                }
                
                var successCount = 0
                var failureCount = 0
                
                for (photo in photosToExport) {
                    try {
                        val sourceFile = java.io.File(photo.filePath)
                        if (!sourceFile.exists()) {
                            failureCount++
                            continue
                        }
                        
                        // Determine MIME type from file extension
                        val mimeType = when (photo.originalName.substringAfterLast(".").lowercase()) {
                            "jpg", "jpeg" -> "image/jpeg"
                            "png" -> "image/png"
                            "gif" -> "image/gif"
                            "webp" -> "image/webp"
                            else -> "image/*"
                        }
                        
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            // Android Q+ (API 29+): Use MediaStore with scoped storage
                            val values = android.content.ContentValues().apply {
                                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, photo.originalName)
                                put(android.provider.MediaStore.Images.Media.MIME_TYPE, mimeType)
                                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhotoVault")
                                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                            }
                            
                            val uri = contentResolver.insert(
                                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                values
                            )
                            
                            if (uri != null) {
                                contentResolver.openOutputStream(uri)?.use { outputStream ->
                                    sourceFile.inputStream().use { inputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                                
                                // Mark the file as ready
                                values.clear()
                                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                                contentResolver.update(uri, values, null, null)
                                
                                successCount++
                            } else {
                                failureCount++
                            }
                        } else {
                            // Pre-Android Q: Direct file copy + MediaScanner
                            val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_PICTURES
                            )
                            val photovaultDir = java.io.File(picturesDir, "PhotoVault")
                            if (!photovaultDir.exists()) {
                                photovaultDir.mkdirs()
                            }
                            
                            // Create destination file with original name
                            var destFile = java.io.File(photovaultDir, photo.originalName)
                            var counter = 1
                            while (destFile.exists()) {
                                val nameWithoutExt = photo.originalName.substringBeforeLast(".")
                                val ext = photo.originalName.substringAfterLast(".")
                                destFile = java.io.File(photovaultDir, "${nameWithoutExt}_$counter.$ext")
                                counter++
                            }
                            
                            // Copy the file
                            sourceFile.copyTo(destFile, overwrite = false)
                            
                            // Trigger MediaScanner to make it visible in gallery
                            android.media.MediaScannerConnection.scanFile(
                                this@AlbumViewActivity,
                                arrayOf(destFile.absolutePath),
                                arrayOf(mimeType)
                            ) { path, uri ->
                                android.util.Log.d("AlbumViewActivity", "Media scan completed for: $path")
                            }
                            
                            successCount++
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AlbumViewActivity", "Failed to export photo: ${e.message}")
                        e.printStackTrace()
                        failureCount++
                    }
                }
                
                // Show result
                val message = when {
                    successCount > 0 && failureCount == 0 -> {
                        if (successCount == 1) "1 photo exported to gallery" 
                        else "$successCount photos exported to gallery"
                    }
                    successCount > 0 && failureCount > 0 -> {
                        "$successCount exported, $failureCount failed"
                    }
                    else -> "Failed to export photos"
                }
                

                
                // Disable selection mode after export
                if (successCount > 0) {
                    photoAdapter.disableSelectionMode()
                }
            } catch (e: Exception) {
                android.util.Log.e("AlbumViewActivity", "Export failed: ${e.message}")

            }
        }
    }
    
    // Gallery deletion dialog method removed - deletion is now automatic
    
    private fun setupAds() {
        // Initialize AdMob with consent handling
        AdManager.initializeWithConsent(
            context = this,
            onConsentReceived = { hasConsent ->
                // Load banner ad with consent status
                AdManager.loadBannerAd(binding.adView, this)
                android.util.Log.d("AlbumViewActivity", "Consent received: $hasConsent")
            },
            onConsentError = { error ->
                // Still load ads even if consent fails (fallback to non-personalized)
                AdManager.loadBannerAd(binding.adView, this)
                android.util.Log.e("AlbumViewActivity", "Consent error: $error")
            }
        )
    }
    
    private fun checkAndShowInterstitialAd() {
        // Check if it's time to show the ad based on frequency
        if (AdManager.shouldShowAlbumViewAd(this)) {
            loadAndShowInterstitialAd()
        }
    }
    
    private fun loadAndShowInterstitialAd() {
        if (isInterstitialAdShowing) return
        
        // Load interstitial ad
        AdManager.loadInterstitialAd(
            context = this,
            onAdLoaded = { ad ->
                interstitialAd = ad
                // Show the ad immediately after loading
                showInterstitialAd()
            },
            onAdFailedToLoad = { error ->
                // Failed to load ad, continue anyway
                // Silently fail - no need to show toast
            }
        )
    }
    
    private fun showInterstitialAd() {
        if (isInterstitialAdShowing || interstitialAd == null) return
        
        isInterstitialAdShowing = true
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

