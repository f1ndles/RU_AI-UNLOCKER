package com.findle.ruaiunlocker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.findle.ruaiunlocker.ui.navigation.AppNavigation
import com.findle.ruaiunlocker.ui.theme.RUAIUnlockerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by mainViewModel.themeMode.collectAsState()

            RUAIUnlockerTheme(themeMode = themeMode) {
                AppNavigation()
            }
        }
    }
}
