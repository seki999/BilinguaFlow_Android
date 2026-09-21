package com.seki999.bilinguaflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.seki999.bilinguaflow.ui.SpeechScreen
import com.seki999.bilinguaflow.ui.theme.BilinguaFlowTheme
import com.seki999.bilinguaflow.viewmodel.SpeechViewModel

/** Single-Activity host for BilinguaFlow's one screen. */
class MainActivity : ComponentActivity() {

    private val speechViewModel: SpeechViewModel by viewModels { SpeechViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BilinguaFlowTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SpeechScreen(viewModel = speechViewModel)
                }
            }
        }
    }
}
