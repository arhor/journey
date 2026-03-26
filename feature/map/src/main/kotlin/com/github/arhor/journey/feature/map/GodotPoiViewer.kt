package com.github.arhor.journey.feature.map

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.compose.ui.res.stringResource

@Composable
fun GodotPoiViewer(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val host = remember(context) { context.findGodotPoiViewerHost() }
    val godotContainerId = remember { View.generateViewId() }
    val fragment = remember(host) { host.createGodotFragment() }

    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.poi_details_godot_viewer_title),
                style = MaterialTheme.typography.titleMedium,
            )

            AndroidView(
                factory = { viewContext ->
                    FragmentContainerView(viewContext).apply {
                        id = godotContainerId
                    }
                },
                update = {
                    it.scheduleGodotFragmentAttach(host = host, fragment = fragment)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
            )

            DisposableEffect(host, fragment) {
                onDispose {
                    if (fragment.isAdded) {
                        host.godotFragmentManager.beginTransaction()
                            .remove(fragment)
                            .commitNowAllowingStateLoss()
                    }
                    host.clearGodotFragment(fragment)
                }
            }

            AndroidView(
                factory = { viewContext ->
                    RecyclerView(viewContext).apply {
                        layoutManager = LinearLayoutManager(
                            viewContext,
                            LinearLayoutManager.HORIZONTAL,
                            false,
                        )
                        adapter = GltfItemRecyclerViewAdapter(
                            context = viewContext,
                            values = GltfContent.items,
                        ) { item ->
                            host.showGltf(item.glbFilepath)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
            )
        }
    }
}

private fun Context.findGodotPoiViewerHost(): GodotPoiViewerHost {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is GodotPoiViewerHost) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    error("GodotPoiViewer must be hosted in a GodotPoiViewerHost")
}

private fun FragmentContainerView.scheduleGodotFragmentAttach(
    host: GodotPoiViewerHost,
    fragment: Fragment,
) {
    post {
        if (!isAttachedToWindow) {
            return@post
        }

        val existingFragment = host.godotFragmentManager.findFragmentByTag(GODOT_FRAGMENT_TAG)
        if (existingFragment === fragment && fragment.isAdded && fragment.id == id) {
            return@post
        }

        host.godotFragmentManager.beginTransaction()
            .replace(id, fragment, GODOT_FRAGMENT_TAG)
            .commitNowAllowingStateLoss()
    }
}

private const val GODOT_FRAGMENT_TAG = "godot:poi-viewer"
