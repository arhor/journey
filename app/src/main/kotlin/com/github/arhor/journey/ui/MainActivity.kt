package com.github.arhor.journey.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.usecase.SetExplorationTrackingCadenceUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var setExplorationTrackingCadence: SetExplorationTrackingCadenceUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            setExplorationTrackingCadence(ExplorationTrackingCadence.FOREGROUND)
        }
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch {
            setExplorationTrackingCadence(ExplorationTrackingCadence.BACKGROUND)
        }
    }
}
