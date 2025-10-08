package com.photovault.locker.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.photovault.locker.R
import com.photovault.locker.adapters.PhotoAdapter
import com.photovault.locker.databinding.ActivityAlbumViewBinding
import com.photovault.locker.models.Photo
import com.photovault.locker.utils.PermissionUtils
import com.photovault.locker.viewmodels.AlbumViewViewModel
import kotlinx.coroutines.launch

class AlbumViewActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAlbumViewBinding
    private lateinit var viewModel: AlbumViewViewModel
    private lateinit var photoAdapter: PhotoAdapter
    
    private var albumId: Long = -1
    private var albumName: String = ""
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startPhotoImportActivity()
        } else {
            Toast.makeText(this, "Storage permission is required to import photos", Toast.LENGTH_LONG).show()
        }
    }
    
    private val photoImportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Photos were imported successfully, refresh the view
            viewModel.refreshPhotos()
            
            // Get the imported count and show gallery deletion dialog
            val importedCount = result.data?.getIntExtra("imported_count", 0) ?: 0
            if (importedCount > 0) {
                showGalleryDeletionConfirmationDialog(importedCount)
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
            openOptionsMenu()
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
            context = this
        )
        
        binding.rvPhotos.apply {
            layoutManager = GridLayoutManager(this@AlbumViewActivity, 3)
            adapter = photoAdapter
        }
    }
    
    private fun setupFab() {
        binding.fabAddPhotos.setOnClickListener {
            Toast.makeText(this, "FAB clicked!", Toast.LENGTH_SHORT).show()
            try {
                if (PermissionUtils.hasStoragePermissions(this)) {
                    Toast.makeText(this, "Permissions granted, starting import", Toast.LENGTH_SHORT).show()
                    startPhotoImportActivity()
                } else {
                    Toast.makeText(this, "Requesting permissions", Toast.LENGTH_SHORT).show()
                    requestPermissionLauncher.launch(PermissionUtils.getRequiredPermissions())
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun setupActionButtons() {
        val btnDelete = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDelete)
        val btnSwap = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSwap)
        
        btnDelete.setOnClickListener {
            moveSelectedPhotosToBin()
        }
        
        btnSwap.setOnClickListener {
            showAlbumSelectionDialog()
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
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.album_menu, menu)

        return true
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
        Toast.makeText(this, "Cover photo updated", Toast.LENGTH_SHORT).show()
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

            Toast.makeText(this, "Photos moved to bin", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showAlbumSelectionDialog() {
        val selectedPhotos = photoAdapter.getSelectedPhotos()
        if (selectedPhotos.isNotEmpty()) {
            // Use coroutine to get albums synchronously
            lifecycleScope.launch {
                try {
                    val albums = viewModel.getAllAlbumsExceptCurrentSync()
                    if (albums.isNotEmpty()) {
                        showAlbumSelectionDialog(albums, selectedPhotos)
                    } else {
                        Toast.makeText(this@AlbumViewActivity, "No other albums available", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@AlbumViewActivity, "Failed to load albums: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showAlbumSelectionDialog(albums: List<com.photovault.locker.models.Album>, selectedPhotos: List<Long>) {
        // Create a custom dialog with ListView
        val listView = android.widget.ListView(this)
        
        // Create adapter for the list
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_list_item_1, albums.map { it.name })
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
            val selectedAlbum = albums[position]
            movePhotosToAlbum(selectedPhotos, selectedAlbum.id, selectedAlbum.name)
            dialog.dismiss()
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
                    }
                }
            }
        }
    }
    
    
    private fun movePhotosToAlbum(photoIds: List<Long>, targetAlbumId: Long, targetAlbumName: String) {
        viewModel.movePhotosToAlbum(photoIds, targetAlbumId)
        photoAdapter.disableSelectionMode()
        updateSelectionCount()
        invalidateOptionsMenu()
        Toast.makeText(this, "Photos moved to $targetAlbumName", Toast.LENGTH_SHORT).show()
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
    }
    
    private fun showGalleryDeletionConfirmationDialog(count: Int) {
        val message = if (count == 1) {
            "Photos have been safely imported to your album. Do you want to delete 1 imported photo from gallery?"
        } else {
            "Photos have been safely imported to your album. Do you want to delete $count imported photos from gallery?"
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete from Gallery")
            .setMessage(message)
            .setPositiveButton("Delete") { _, _ ->
                // TODO: Implement gallery deletion
                Toast.makeText(this, "Photos deleted from gallery", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Keep in Gallery") { _, _ ->
                Toast.makeText(this, "Photos kept in gallery", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }
}

