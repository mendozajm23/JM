package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.data.remote.configureAppCheck
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.NominaProTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Step 1: Configure App Check with launch intent before rendering UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            NominaProTheme {
                AppNavigation()
            }
        }
    }
}
