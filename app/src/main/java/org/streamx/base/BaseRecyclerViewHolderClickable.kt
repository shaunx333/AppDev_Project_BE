package org.streamx.base

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.streamx.base.adapters.BaseRecyclerViewAdapter


open class BaseRecyclerViewHolderClickable(
    view: View,
    onItemClick: BaseRecyclerViewAdapter.OnItemClickI? = null
) : RecyclerView.ViewHolder(view) {
    init {
        if (onItemClick != null) {
            itemView.setOnClickListener {
                onItemClick.click(adapterPosition, it)
            }
        }
    }
}