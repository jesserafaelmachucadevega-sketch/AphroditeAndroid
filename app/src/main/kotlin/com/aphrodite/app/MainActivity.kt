package com.aphrodite.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single activity.
 *
 * Extends [FragmentActivity] rather than ComponentActivity so the biometric prompt, which needs
 * a fragment host, can be shown directly without an extra wrapper activity.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AphroditeApp()
        }
    }
}
