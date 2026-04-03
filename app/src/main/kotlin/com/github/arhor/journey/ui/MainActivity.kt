package com.github.arhor.journey.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.common.resolveMessage
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
        setContent {
            App(
                onOpenMiniGame = ::openMiniGame,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            updateTrackingCadence(ExplorationTrackingCadence.FOREGROUND)
        }
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch {
            updateTrackingCadence(ExplorationTrackingCadence.BACKGROUND)
        }
    }

    private suspend fun updateTrackingCadence(cadence: ExplorationTrackingCadence) {
        when (val result = setExplorationTrackingCadence(cadence)) {
            is Output.Success -> {
                Log.d(
                    TAG,
                    "Exploration tracking cadence updated: $cadence",
                )
            }

            is Output.Failure -> {
                Log.w(
                    TAG,
                    result.error.resolveMessage("Failed to update exploration tracking cadence"),
                    result.error.cause,
                )
            }
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }

    private fun openMiniGame() {
        startActivity(Intent(this, MiniGameActivity::class.java))
    }
}
