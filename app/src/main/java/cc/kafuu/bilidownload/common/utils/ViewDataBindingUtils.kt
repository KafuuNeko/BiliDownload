package cc.kafuu.bilidownload.common.utils

import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import cc.kafuu.bilidownload.common.core.CoreRVAdapter

@BindingAdapter(value = ["bindDataList"])
fun bindDataList(recyclerView: RecyclerView, data: List<Any>?) {
    (recyclerView.adapter as? CoreRVAdapter<*, *>)?.setDataList(data)
}
