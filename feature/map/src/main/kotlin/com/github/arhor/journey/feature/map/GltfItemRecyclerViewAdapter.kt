package com.github.arhor.journey.feature.map

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.arhor.journey.feature.map.GltfContent.GltfItem

class GltfItemRecyclerViewAdapter(
    private val context: Context,
    private val values: List<GltfItem>,
    private val onItemSelected: (GltfItem) -> Unit,
) : RecyclerView.Adapter<GltfItemRecyclerViewAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.item_gltf_selection,
                parent,
                false,
            ),
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = values[position]
        val screenshotDrawableId = item.getScreenshotDrawableId(context)
        if (screenshotDrawableId != 0) {
            holder.screenshotView.setImageResource(screenshotDrawableId)
        } else {
            holder.screenshotView.setImageDrawable(null)
        }
        holder.contentView.text = item.name
        holder.itemView.setOnClickListener {
            onItemSelected(item)
        }
    }

    override fun getItemCount(): Int = values.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val screenshotView: ImageView = view.findViewById(R.id.contentScreenshot)
        val contentView: TextView = view.findViewById(R.id.contentName)

        override fun toString(): String {
            return super.toString() + " '" + contentView.text + "'"
        }
    }
}
