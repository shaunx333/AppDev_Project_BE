package org.streamx.base.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import org.streamx.R
import org.streamx.base.BaseDiffUtil
import org.streamx.base.BaseRecyclerViewHolderClickable
import java.text.SimpleDateFormat
import java.util.*

class BaseRecyclerViewAdapter<X, Y : ViewBinding>(
    private val bindingFactory: (LayoutInflater) -> Y,
    private var list: List<X>,
    private val onClickI: OnItemClickI? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private lateinit var recBinding: Y

    private val simpleDateFormat by lazy { SimpleDateFormat("dd/MM", Locale.getDefault()) }
    private val simpleHourFormat by lazy { SimpleDateFormat("hh:mmaaa", Locale.getDefault()) }
    var lastPosition = -1
    lateinit var context: Context

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        context = recyclerView.context
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        recBinding = bindingFactory(LayoutInflater.from(context))
        return BaseRecyclerViewHolderClickable(recBinding.root, onClickI)
    }

    override fun getItemViewType(position: Int): Int {
        return position
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        setAnimation(holder.itemView, position)
    }


    fun update(newList: List<X>?) {
        if (newList == null)
            return
        val m = BaseDiffUtil(newList, list)
        list = newList
        DiffUtil.calculateDiff(m).dispatchUpdatesTo(this)
    }

    private fun setAnimation(viewToAnimate: View, position: Int) {
        // If the bound view wasn't previously displayed on screen, it's animated
        if (position > lastPosition) {
            val animation: Animation =
                AnimationUtils.loadAnimation(context, R.anim.fade_in_1000)
            viewToAnimate.startAnimation(animation)
            lastPosition = position
        }
    }

    override fun getItemCount(): Int = list.size

    interface OnItemClickI {
        fun click(pos: Int, v: View)
    }
}