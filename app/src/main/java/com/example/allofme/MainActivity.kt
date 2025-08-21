package com.example.allofme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.allofme.ui.theme.AllofMeTheme
import com.example.allofme.screens.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AllofMeTheme {
                // Pasamos el contexto actual a AppNavigation
                AppNavigation(context = this)
            }
        }
    }
}
