package com.lumix.estimator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.lumix.estimator.ui.nav.LumixNavHost
import com.lumix.estimator.ui.theme.LumixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LumixTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LumixNavHost(app = application as LumixApp)
                }
            }
        }
    }
}
