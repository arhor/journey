package com.github.arhor.journey.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.github.arhor.journey.R
import com.github.arhor.journey.ui.GLTFContent.GLTFItem

/**
 * [RecyclerView.Adapter] that can display a [GLTFItem].
 */
class GLTFItemRecyclerViewAdapter(
    private val context: Context,
    private val values: List<GLTFItem>,
    private val onItemSelected: (GLTFItem) -> Unit,
) : RecyclerView.Adapter<GLTFItemRecyclerViewAdapter.ViewHolder>() {

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
