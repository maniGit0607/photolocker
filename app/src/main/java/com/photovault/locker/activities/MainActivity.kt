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
import com.photovault.locker.utils.PermissionUtils
import com.photovault.locker.viewmodels.MainViewModel

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var albumAdapter: AlbumAdapter
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            showPermissionRationaleDialog()
        }
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
        if (!PermissionUtils.hasStoragePermissions(this)) {
            requestPermissions()
        }
    }
    
    private fun requestPermissions() {
        requestPermissionLauncher.launch(PermissionUtils.getRequiredPermissions())
    }
    
    private fun showPermissionRationaleDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Storage Permission Required")
            .setMessage(getString(R.string.storage_permission_required))
            .setPositiveButton(getString(R.string.grant_permission)) { _, _ ->
                requestPermissions()
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
    
    private fun showAlbumOptionsDialog(album: Album) {
        val options = arrayOf("Open Album", "Delete Album")
        
        MaterialAlertDialogBuilder(this)
            .setTitle(album.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // Open Album
                        val intent = Intent(this, AlbumViewActivity::class.java).apply {
                            putExtra("album_id", album.id)
                            putExtra("album_name", album.name)
                        }
                        startActivity(intent)
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
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_change_password -> {
                // TODO: Implement change password functionality
                Toast.makeText(this, "Change password functionality", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_about -> {
                // TODO: Implement about dialog
                Toast.makeText(this, "PhotoVault Locker v1.0", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

