package com.thanhmich.veil.vpn

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.thanhmich.veil.vpn.features.main.MainContent

class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppWrapperWithWelcomeBack {
                App()
            }
        }
    }
}
