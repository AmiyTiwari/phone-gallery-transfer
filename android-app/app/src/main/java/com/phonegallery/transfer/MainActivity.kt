package com.phonegallery.transfer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phonegallery.transfer.ui.screens.GalleryScreen
import com.phonegallery.transfer.ui.screens.LaptopScreen
import com.phonegallery.transfer.ui.theme.AppTheme
import com.phonegallery.transfer.viewmodel.GalleryViewModel

private data class NavItem(val label: String, val icon: ImageVector)

private val NAV_ITEMS = listOf(
    NavItem("Gallery", Icons.Filled.Image),
    NavItem("Laptop", Icons.Filled.Laptop),
)

class MainActivity : ComponentActivity() {

    private lateinit var galleryViewModel: GalleryViewModel

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) galleryViewModel.loadPhotos()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Init before setContent so permissionLauncher callback never hits uninitialised ref
        galleryViewModel = ViewModelProvider(this)[GalleryViewModel::class.java]

        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        setContent {
            AppTheme {
                val galleryViewModel = viewModel<GalleryViewModel>()
                var selectedTab by remember { mutableIntStateOf(0) }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            NAV_ITEMS.forEachIndexed { index, item ->
                                NavigationBarItem(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    icon = { Icon(item.icon, item.label) },
                                    label = { Text(item.label) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    when (selectedTab) {
                        0 -> GalleryScreen(modifier = Modifier.padding(padding), vm = galleryViewModel)
                        1 -> LaptopScreen(modifier = Modifier.padding(padding))
                    }
                }
            }
        }

        permissionLauncher.launch(permissions)
    }
}
