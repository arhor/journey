package com.github.arhor.journey.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.usecase.SetExplorationTrackingCadenceUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotFragment
import org.godotengine.godot.GodotHost
import org.godotengine.godot.plugin.GodotPlugin
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity(), GodotHost {
    private var godotFragment: GodotFragment? = null
    internal var appPlugin: AppPlugin? = null

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

    fun createGodotFragment(): GodotFragment =
        GodotFragment().also { fragment ->
            godotFragment = fragment
        }

    fun clearGodotFragment(fragment: GodotFragment) {
        if (godotFragment === fragment) {
            godotFragment = null
        }
    }

    fun showGltf(glbFilepath: String) {
        appPlugin?.showGLTF(glbFilepath)
    }

    private fun initAppPluginIfNeeded(godot: Godot) {
        if (appPlugin == null) {
            appPlugin = AppPlugin(godot)
        }
    }

    override fun getActivity() = this

    override fun getGodot() = godotFragment?.godot

    override fun getHostPlugins(godot: Godot): Set<GodotPlugin> {
        initAppPluginIfNeeded(godot)

        return setOf(appPlugin!!)
    }
}
