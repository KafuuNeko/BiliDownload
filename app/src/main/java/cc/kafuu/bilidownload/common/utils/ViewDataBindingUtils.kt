package cc.kafuu.bilidownload.common.utils

import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import cc.kafuu.bilidownload.common.core.CoreEntity
import cc.kafuu.bilidownload.common.core.CoreRecyclerViewAdapter

@BindingAdapter(value = ["bindEntityData"])
fun bindEntityData(recyclerView: RecyclerView, data: List<CoreEntity>?) {
    (recyclerView.adapter as? CoreRecyclerViewAdapter<*, *>)?.setEntityList(data)
}
