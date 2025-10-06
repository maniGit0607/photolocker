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
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.photovault.locker.R
import com.photovault.locker.adapters.PhotoAdapter
import com.photovault.locker.databinding.ActivityAlbumViewBinding
import com.photovault.locker.models.Photo
import com.photovault.locker.utils.PermissionUtils
import com.photovault.locker.viewmodels.AlbumViewViewModel

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
            //viewModel.refreshPhotos()
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
        val ivBack = findViewById<android.widget.ImageView>(R.id.ivBack)
        val ivMenu = findViewById<android.widget.ImageView>(R.id.ivMenu)
        val ivCoverPhoto = findViewById<android.widget.ImageView>(R.id.ivCoverPhoto)
        
        tvAlbumTitle.text = albumName
        
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
                invalidateOptionsMenu()
            },
            onSetCoverPhoto = { photo ->
                // Set photo as album cover
                setPhotoAsCover(photo)
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
        
        // Show/hide menu items based on selection mode
        val deleteItem = menu?.findItem(R.id.action_delete_photos)
        val selectAllItem = menu?.findItem(R.id.action_select_all)
        val tvAlbumTitle = findViewById<android.widget.TextView>(R.id.tvAlbumTitle)
        
        if (photoAdapter.isSelectionMode()) {
            deleteItem?.isVisible = false  // Hide menu delete, use action buttons instead
            selectAllItem?.isVisible = true
            tvAlbumTitle.text = "${photoAdapter.getSelectedCount()} selected"
            showActionButtons()
        } else {
            deleteItem?.isVisible = false
            selectAllItem?.isVisible = false
            tvAlbumTitle.text = albumName
            hideActionButtons()
        }
        
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete_photos -> {
                showDeleteConfirmationDialog()
                true
            }
            R.id.action_select_all -> {
                // TODO: Implement select all functionality
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showDeleteConfirmationDialog() {
        val selectedCount = photoAdapter.getSelectedCount()
        val message = if (selectedCount == 1) {
            "Delete 1 photo?"
        } else {
            "Delete $selectedCount photos?"
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_photo))
            .setMessage(message)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                val selectedPhotos = photoAdapter.getSelectedPhotos()
                viewModel.deletePhotos(selectedPhotos)
                photoAdapter.disableSelectionMode()
                invalidateOptionsMenu()
                Toast.makeText(this, "Photos deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    override fun onBackPressed() {
        if (photoAdapter.isSelectionMode()) {
            photoAdapter.disableSelectionMode()
            invalidateOptionsMenu()
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
            hideActionButtons()
            invalidateOptionsMenu()
            Toast.makeText(this, "Photos moved to bin", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showAlbumSelectionDialog() {
        val selectedPhotos = photoAdapter.getSelectedPhotos()
        if (selectedPhotos.isNotEmpty()) {
            // TODO: Implement album selection dialog
            Toast.makeText(this, "Album selection dialog - TODO", Toast.LENGTH_SHORT).show()
        }
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
}

