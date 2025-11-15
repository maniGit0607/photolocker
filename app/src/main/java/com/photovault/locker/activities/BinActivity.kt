package com.photovault.locker.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.photovault.locker.R
import com.photovault.locker.adapters.PhotoAdapter
import com.photovault.locker.databinding.ActivityBinBinding
import com.photovault.locker.viewmodels.BinViewModel

class BinActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityBinBinding
    private lateinit var viewModel: BinViewModel
    private lateinit var photoAdapter: PhotoAdapter
    
    // Grid layout manager
    private lateinit var gridLayoutManager: GridLayoutManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBinBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViewModel()
        setupToolbar()
        setupRecyclerView()
        observeData()
    }
    
    private fun setupToolbar() {
        // Set up MaterialToolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Bin"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Handle navigation icon click
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupViewModel() {
        val factory = BinViewModel.Factory(application)
        viewModel = ViewModelProvider(this, factory)[BinViewModel::class.java]
    }
    
    private fun setupRecyclerView() {
        photoAdapter = PhotoAdapter(
            onPhotoClick = { photo, position ->
                // No photo view for bin - just enable selection mode
            },
            onPhotoLongClick = { photo ->
                // Handle long click for selection mode
                updateSelectionCount()
            },
            onSetCoverPhoto = { photo ->
                // No cover photo functionality in bin
            },
            onSelectionModeChanged = { isSelectionMode ->
                // Handle selection mode changes
                updateSelectionCount()
                invalidateOptionsMenu()
            },
            context = this,
            false
        )
        
        // Initialize grid layout manager
        gridLayoutManager = GridLayoutManager(this, 5) // Fixed 5 columns
        
        binding.rvPhotos.apply {
            layoutManager = gridLayoutManager
            adapter = photoAdapter
        }
    }
    
    private fun observeData() {
        viewModel.deletedPhotos.observe(this) { photos ->
            android.util.Log.d("BinActivity", "Deleted photos LiveData updated with ${photos.size} photos")
            
            photoAdapter.submitList(photos) {
                android.util.Log.d("BinActivity", "Adapter updated with ${photos.size} photos")
            }
            
            if (photos.isEmpty()) {
                binding.rvPhotos.visibility = View.GONE
                binding.llEmptyState.visibility = View.VISIBLE
                android.util.Log.d("BinActivity", "Showing empty state")
            } else {
                binding.rvPhotos.visibility = View.VISIBLE
                binding.llEmptyState.visibility = View.GONE
                android.util.Log.d("BinActivity", "Showing photos in RecyclerView")
            }
        }

        viewModel.error.observe(this) { error ->
            if (error.isNotEmpty()) {
                android.util.Log.e("BinActivity", "Error: $error")
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            // Handle loading state if needed
            android.util.Log.d("BinActivity", "Loading state: $isLoading")
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
        // Refresh deleted photos when returning from other activities
        viewModel.refreshDeletedPhotos()
    }
    
    private fun toggleSelection(photoId: Long) {
        // This will be handled by the PhotoAdapter's internal selection logic
        // We just need to update the UI after the adapter handles it
    }
    
    private fun updateSelectionCount() {
        val selectedCount = photoAdapter.getSelectedCount()
        if (photoAdapter.isSelectionMode()) {
            supportActionBar?.title = if (selectedCount == 1) "1 photo selected" else "$selectedCount photos selected"
        } else {
            supportActionBar?.title = "Bin"
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_bin, menu)
        return true
    }
    
    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val isSelectionMode = photoAdapter.isSelectionMode()
        val selectedCount = photoAdapter.getSelectedCount()
        
        menu?.findItem(R.id.action_restore)?.isEnabled = isSelectionMode && selectedCount > 0
        menu?.findItem(R.id.action_delete_permanently)?.isEnabled = isSelectionMode && selectedCount > 0
        
        return super.onPrepareOptionsMenu(menu)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_restore -> {
                restoreSelectedPhotos()
                true
            }
            R.id.action_delete_permanently -> {
                permanentlyDeleteSelectedPhotos()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun restoreSelectedPhotos() {
        val selectedPhotos = photoAdapter.getSelectedPhotos()
        if (selectedPhotos.isNotEmpty()) {
            val message = if (selectedPhotos.size == 1) {
                "Restore 1 photo from bin?"
            } else {
                "Restore ${selectedPhotos.size} photos from bin?"
            }
            
            MaterialAlertDialogBuilder(this)
                .setTitle("Restore Photos")
                .setMessage(message)
                .setPositiveButton("Restore") { _, _ ->
                    viewModel.restorePhotos(selectedPhotos)
                    photoAdapter.disableSelectionMode()
                    Toast.makeText(this, "Photos restored", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun permanentlyDeleteSelectedPhotos() {
        val selectedPhotos = photoAdapter.getSelectedPhotos()
        if (selectedPhotos.isNotEmpty()) {
            val message = if (selectedPhotos.size == 1) {
                "Permanently delete 1 photo? This action cannot be undone."
            } else {
                "Permanently delete ${selectedPhotos.size} photos? This action cannot be undone."
            }
            
            MaterialAlertDialogBuilder(this)
                .setTitle("Delete Permanently")
                .setMessage(message)
                .setPositiveButton("Delete") { _, _ ->
                    viewModel.permanentlyDeletePhotos(selectedPhotos)
                    photoAdapter.disableSelectionMode()
                    Toast.makeText(this, "Photos permanently deleted", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
