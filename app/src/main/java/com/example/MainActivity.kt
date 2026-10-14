
package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.MusicDatabase
import com.example.data.TrackRepository
import com.example.player.MusicViewModel
import com.example.player.MusicViewModelFactory
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val database by lazy { MusicDatabase.getDatabase(applicationContext) }
    private val repository by lazy { TrackRepository(applicationContext, database.trackDao()) }
    
    private val viewModel: MusicViewModel by viewModels {
        MusicViewModelFactory(applicationContext, repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        requestBatteryOptimization()

        setContent {
            val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
            MyApplicationTheme(appTheme = appTheme) {
                MainScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent()
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }

    // Throttle foreground rescans: a full SAF crawl on every onResume caused an
    // IO/CPU spike and visible stutter right after returning to the app.
    private var lastRescanElapsed = 0L

    override fun onResume() {
        super.onResume()
        val currentUriStr = viewModel.selectedFolderUri.value ?: return
        if (viewModel.isScanning.value) return

        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastRescanElapsed < RESCAN_THROTTLE_MS) return
        lastRescanElapsed = now

        try {
            val uri = android.net.Uri.parse(currentUriStr)
            viewModel.scanDirectory(uri)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private companion object {
        const val RESCAN_THROTTLE_MS = 10_000L
    }
}

// dummy change
