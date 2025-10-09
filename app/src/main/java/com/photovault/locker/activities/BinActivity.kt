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
    
    // Grid size management
    private var currentGridSize = GridSize.MEDIUM
    private lateinit var gridLayoutManager: GridLayoutManager
    
    enum class GridSize(val columnCount: Int, val displayName: String) {
        SMALL(5, "Small"),      // 75% smaller than medium (3 -> 5 columns)
        MEDIUM(3, "Medium"),    // Current size
        LARGE(2, "Large")       // 150% larger than medium (3 -> 2 columns)
    }
    
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
        // Set up custom toolbar
        val tvAlbumTitle = findViewById<android.widget.TextView>(R.id.tvAlbumTitle)
        val tvSelectionCount = findViewById<android.widget.TextView>(R.id.tvSelectionCount)
        val ivBack = findViewById<android.widget.ImageView>(R.id.ivBack)
        val ivMenu = findViewById<android.widget.ImageView>(R.id.ivMenu)
        
        tvAlbumTitle.text = "Bin"
        tvSelectionCount.visibility = android.view.View.GONE
        
        ivBack.setOnClickListener {
            onBackPressed()
        }
        
        ivMenu.setOnClickListener {
            android.util.Log.d("BinActivity", "Menu button clicked")
            showGridSizePopupMenu(ivMenu)
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
                if (!photoAdapter.isSelectionMode()) {
                    photoAdapter.enableSelectionMode()
                    toggleSelection(photo.id)
                    updateSelectionCount()
                } else {
                    toggleSelection(photo.id)
                    updateSelectionCount()
                }
            },
            onPhotoLongClick = { photo ->
                // Handle long click for selection mode
                if (!photoAdapter.isSelectionMode()) {
                    photoAdapter.enableSelectionMode()
                }
                toggleSelection(photo.id)
                updateSelectionCount()
            },
            onSetCoverPhoto = { photo ->
                // No cover photo functionality in bin
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
                invalidateOptionsMenu()
            },
            context = this
        )
        
        // Initialize grid layout manager
        gridLayoutManager = GridLayoutManager(this, currentGridSize.columnCount)
        
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
    
    private fun showGridSizePopupMenu(anchorView: android.view.View) {
        val popupMenu = android.widget.PopupMenu(this, anchorView)
        popupMenu.menuInflater.inflate(R.menu.album_menu, popupMenu.menu)
        
        // Hide items that are not relevant for grid size
        popupMenu.menu.findItem(R.id.action_delete_photos)?.isVisible = false
        popupMenu.menu.findItem(R.id.action_select_all)?.isVisible = false
        
        // Set checkmarks for current grid size
        when (currentGridSize) {
            GridSize.SMALL -> popupMenu.menu.findItem(R.id.action_grid_small)?.isChecked = true
            GridSize.MEDIUM -> popupMenu.menu.findItem(R.id.action_grid_medium)?.isChecked = true
            GridSize.LARGE -> popupMenu.menu.findItem(R.id.action_grid_large)?.isChecked = true
        }
        
        popupMenu.setOnMenuItemClickListener { item ->
            android.util.Log.d("BinActivity", "Popup menu item clicked: ${item.title} (ID: ${item.itemId})")
            
            when (item.itemId) {
                R.id.action_grid_small -> {
                    android.util.Log.d("BinActivity", "Grid small clicked")
                    changeGridSize(GridSize.SMALL)
                    true
                }
                R.id.action_grid_medium -> {
                    android.util.Log.d("BinActivity", "Grid medium clicked")
                    changeGridSize(GridSize.MEDIUM)
                    true
                }
                R.id.action_grid_large -> {
                    android.util.Log.d("BinActivity", "Grid large clicked")
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
            Toast.makeText(this, "Grid size changed to ${newSize.displayName}", Toast.LENGTH_SHORT).show()
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
    
    private fun showActionButtons() {
        val selectionButtons = findViewById<android.widget.LinearLayout>(R.id.selectionActionButtons)
        selectionButtons.visibility = android.view.View.VISIBLE
    }
    
    private fun hideActionButtons() {
        val selectionButtons = findViewById<android.widget.LinearLayout>(R.id.selectionActionButtons)
        selectionButtons.visibility = android.view.View.GONE
    }
    
    private fun updateSelectionCount() {
        val tvSelectionCount = findViewById<android.widget.TextView>(R.id.tvSelectionCount)
        val selectedCount = photoAdapter.getSelectedCount()
        tvSelectionCount.text = if (selectedCount == 1) "1 photo selected" else "$selectedCount photos selected"
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
