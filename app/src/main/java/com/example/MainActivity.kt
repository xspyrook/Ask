package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.navigation.HalaSualNavGraph
import com.example.ui.theme.HalaSualTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val prefsState by viewModel.prefsState.collectAsStateWithLifecycle()

            HalaSualTheme(themeMode = prefsState.themeMode) {
                HalaSualNavGraph(viewModel = viewModel)
            }
        }
    }
}
