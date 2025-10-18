package com.photovault.locker.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.photovault.locker.R
import com.photovault.locker.adapters.AlbumAdapter
import com.photovault.locker.databinding.ActivityMainBinding
import com.photovault.locker.databinding.DialogCreateAlbumBinding
import com.photovault.locker.models.Album
import com.photovault.locker.utils.AdManager
import com.photovault.locker.utils.PermissionUtils
import com.photovault.locker.viewmodels.MainViewModel

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var albumAdapter: AlbumAdapter
    private var hasRequestedPermissions = false
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            // Check if any permission was actually granted
            val anyGranted = permissions.values.any { it }
            if (anyGranted || !hasRequestedPermissions) {
                // Some permissions granted or first request, check again
                if (!PermissionUtils.hasStoragePermissions(this)) {
                    showPermissionRationaleDialog()
                }
            } else {
                // User denied permissions, show rationale
                showPermissionRationaleDialog()
            }
        }
        hasRequestedPermissions = true
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupViewModel()
        setupRecyclerView()
        setupFab()
        observeData()
        setupAds()
        
        checkPermissions()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.albums)
    }
    
    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
    }
    
    private fun setupRecyclerView() {
        albumAdapter = AlbumAdapter(
            onAlbumClick = { album ->
                // Navigate to AlbumViewActivity
                val intent = Intent(this, AlbumViewActivity::class.java).apply {
                    putExtra("album_id", album.id)
                    putExtra("album_name", album.name)
                }
                startActivity(intent)
            },
            onAlbumLongClick = { album ->
                showAlbumOptionsDialog(album)
            }
        )
        
        binding.rvAlbums.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = albumAdapter
        }
    }
    
    private fun setupFab() {
        binding.fabCreateAlbum.setOnClickListener {
            if (PermissionUtils.hasStoragePermissions(this)) {
                showCreateAlbumDialog()
            } else {
                requestPermissions()
            }
        }
    }
    
    private fun setupAds() {
        // Initialize AdMob
        AdManager.initialize(this) {
            // Load banner ad after initialization
            AdManager.loadBannerAd(binding.adView)
        }
    }
    
    private fun observeData() {
        viewModel.albums.observe(this) { albums ->
            albumAdapter.submitList(albums)
            
            if (albums.isEmpty()) {
                binding.rvAlbums.visibility = View.GONE
                binding.llEmptyState.visibility = View.VISIBLE
            } else {
                binding.rvAlbums.visibility = View.VISIBLE
                binding.llEmptyState.visibility = View.GONE
            }
        }
        
        viewModel.error.observe(this) { error ->
            if (error.isNotEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun checkPermissions() {
        val hasPermissions = PermissionUtils.hasStoragePermissions(this)
        if (!hasPermissions) {
            requestPermissions()
        } else {
            // Permissions already granted
            viewModel.updateAlbumPhotoCounts()
        }
    }
    
    private fun requestPermissions() {
        requestPermissionLauncher.launch(PermissionUtils.getRequiredPermissions())
    }
    
    private fun openFavoritesActivity() {
        val intent = Intent(this, FavoritesActivity::class.java)
        startActivity(intent)
    }
    
    private fun openBinActivity() {
        val intent = Intent(this, BinActivity::class.java)
        startActivity(intent)
    }
    
    private fun showSettingsPopupMenu(anchorView: android.view.View) {
        val popupMenu = android.widget.PopupMenu(this, anchorView)
        popupMenu.menuInflater.inflate(R.menu.settings_menu, popupMenu.menu)
        
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_change_password -> {
                    showChangePasswordDialog()
                    true
                }
                R.id.action_about -> {
                    showAboutDialog()
                    true
                }
                else -> false
            }
        }
        
        popupMenu.show()
    }
    
    private fun showChangePasswordDialog() {
        val dialogBinding = com.photovault.locker.databinding.DialogChangePasswordBinding.inflate(layoutInflater)
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()
        
        dialogBinding.btnChange.setOnClickListener {
            val currentPassword = dialogBinding.etCurrentPassword.text.toString()
            val newPassword = dialogBinding.etNewPassword.text.toString()
            val confirmPassword = dialogBinding.etConfirmPassword.text.toString()
            
            // Clear previous errors
            dialogBinding.tilCurrentPassword.error = null
            dialogBinding.tilNewPassword.error = null
            dialogBinding.tilConfirmPassword.error = null
            
            // Validate inputs
            if (currentPassword.isEmpty()) {
                dialogBinding.tilCurrentPassword.error = "Enter current password"
                return@setOnClickListener
            }
            
            if (newPassword.isEmpty()) {
                dialogBinding.tilNewPassword.error = "Enter new password"
                return@setOnClickListener
            }
            
            if (newPassword.length < 4) {
                dialogBinding.tilNewPassword.error = "Password must be at least 4 characters"
                return@setOnClickListener
            }
            
            if (confirmPassword.isEmpty()) {
                dialogBinding.tilConfirmPassword.error = "Confirm your new password"
                return@setOnClickListener
            }
            
            if (newPassword != confirmPassword) {
                dialogBinding.tilConfirmPassword.error = "Passwords do not match"
                return@setOnClickListener
            }
            
            // Attempt to change password
            val passwordManager = com.photovault.locker.utils.PasswordManager(this)
            val success = passwordManager.changePassword(currentPassword, newPassword)
            
            if (success) {
                dialog.dismiss()
                MaterialAlertDialogBuilder(this)
                    .setTitle("Success")
                    .setMessage("Your password has been changed successfully")
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                dialogBinding.tilCurrentPassword.error = "Current password is incorrect"
            }
        }
        
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("About PhotoVault Locker")
            .setMessage("PhotoVault Locker\nVersion 1.0\n\nA secure photo vault application to keep your photos private and protected.")
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showPermissionRationaleDialog() {
        val message = if (hasRequestedPermissions) {
            "Storage permission is required to access and manage photos. Please go to Settings > Apps > PhotoVault Locker > Permissions and enable storage/media access."
        } else {
            getString(R.string.storage_permission_required)
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Storage Permission Required")
            .setMessage(message)
            .setPositiveButton(if (hasRequestedPermissions) "Open Settings" else getString(R.string.grant_permission)) { _, _ ->
                if (hasRequestedPermissions) {
                    // Open app settings
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = android.net.Uri.fromParts("package", packageName, null)
                        intent.data = uri
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Please enable storage permission in system settings", Toast.LENGTH_LONG).show()
                    }
                } else {
                    requestPermissions()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun showCreateAlbumDialog() {
        val dialogBinding = DialogCreateAlbumBinding.inflate(layoutInflater)
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()
        
        dialogBinding.btnCreate.setOnClickListener {
            val albumName = dialogBinding.etAlbumName.text.toString().trim()
            
            if (albumName.isEmpty()) {
                dialogBinding.tilAlbumName.error = getString(R.string.album_name_required)
                return@setOnClickListener
            }
            
            viewModel.createAlbum(albumName) { success ->
                if (success) {
                    dialog.dismiss()
                    Toast.makeText(this, getString(R.string.album_created), Toast.LENGTH_SHORT).show()
                } else {
                    dialogBinding.tilAlbumName.error = getString(R.string.album_exists)
                }
            }
        }
        
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showRenameAlbumDialog(album: Album) {
        val dialogBinding = DialogCreateAlbumBinding.inflate(layoutInflater)
        
        // Pre-fill with current album name
        dialogBinding.etAlbumName.setText(album.name)
        dialogBinding.etAlbumName.setSelection(album.name.length) // Place cursor at end
        dialogBinding.btnCreate.text = "Rename"
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()
        
        dialogBinding.btnCreate.setOnClickListener {
            val newName = dialogBinding.etAlbumName.text.toString().trim()
            
            if (newName.isEmpty()) {
                dialogBinding.tilAlbumName.error = getString(R.string.album_name_required)
                return@setOnClickListener
            }
            
            if (newName == album.name) {
                // Name hasn't changed
                dialog.dismiss()
                return@setOnClickListener
            }
            
            viewModel.renameAlbum(album, newName) { success ->
                if (success) {
                    dialog.dismiss()
                    Toast.makeText(this, "Album renamed successfully", Toast.LENGTH_SHORT).show()
                } else {
                    dialogBinding.tilAlbumName.error = getString(R.string.album_exists)
                }
            }
        }
        
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showAlbumOptionsDialog(album: Album) {
        val options = arrayOf("Rename Album", "Delete Album")
        
        MaterialAlertDialogBuilder(this)
            .setTitle(album.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // Rename Album
                        showRenameAlbumDialog(album)
                    }
                    1 -> {
                        // Delete Album
                        showDeleteConfirmationDialog(album)
                    }
                }
            }
            .show()
    }
    
    private fun showDeleteConfirmationDialog(album: Album) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_album))
            .setMessage(getString(R.string.delete_album_confirmation))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deleteAlbum(album)
                Toast.makeText(this, "Album deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        android.util.Log.d("MainActivity", "Menu created with ${menu?.size()} items")
        
        // Debug: Log each menu item
        for (i in 0 until (menu?.size() ?: 0)) {
            val item = menu?.getItem(i)
            android.util.Log.d("MainActivity", "Menu item $i: ${item?.title} (ID: ${item?.itemId})")
        }
        
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        android.util.Log.d("MainActivity", "Menu item selected: ${item.title} (ID: ${item.itemId})")
        return when (item.itemId) {
            R.id.action_favorites -> {
                openFavoritesActivity()
                true
            }
            R.id.action_bin -> {
                openBinActivity()
                true
            }
            R.id.action_settings -> {
                // Find the settings menu item view to anchor the popup
                val view = findViewById<android.view.View>(R.id.action_settings)
                if (view != null) {
                    showSettingsPopupMenu(view)
                } else {
                    // Fallback to toolbar if view not found
                    showSettingsPopupMenu(binding.toolbar)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Recheck permissions when returning from settings
        if (hasRequestedPermissions && PermissionUtils.hasStoragePermissions(this)) {
            // Permissions granted, refresh album information
            viewModel.updateAlbumPhotoCounts()
        }
    }
}

