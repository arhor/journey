package com.github.arhor.journey.feature.map

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

interface GodotPoiViewerHost {
    val godotFragmentManager: FragmentManager

    fun createGodotFragment(): Fragment

    fun clearGodotFragment(fragment: Fragment)

    fun showGltf(glbFilepath: String)
}
