package org.streamx.base

import androidx.recyclerview.widget.DiffUtil

@Suppress("UNCHECKED_CAST")
class BaseDiffUtil<X, Y>(
    private val newList: List<X>,
    private val oldList: List<Y>
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int {
        return oldList.size
    }

    override fun getNewListSize(): Int {
        return newList.size
    }

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return when {
            else -> newList[newItemPosition] == oldList[oldItemPosition]
        }
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return true
    }
}