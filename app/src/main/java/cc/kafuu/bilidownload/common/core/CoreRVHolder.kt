package cc.kafuu.bilidownload.common.core

import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView

abstract class CoreRVHolder(var binding: ViewDataBinding) :
    RecyclerView.ViewHolder(binding.root) {
    abstract fun getDataVariableId(): Int
    abstract fun getVMVariableId(): Int
}