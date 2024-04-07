package cc.kafuu.bilidownload.common.core

import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView

abstract class CoreRVAdapter<V : ViewDataBinding, VM : CoreViewModel>(
    protected val mViewModel: VM? = null
) : IDataContainer, RecyclerView.Adapter<CoreRVHolder>() {
    private var mDataList: List<Any>? = null

    protected fun getItemData(position: Int): Any {
        val list = mDataList ?: throw IllegalStateException("Entity list is not initialized.")
        if (position < 0 || position >= list.size) {
            throw IndexOutOfBoundsException("Position $position is out of bounds for list size ${list.size}.")
        }
        return list[position]
    }

    override fun getDataCount(): Int = mDataList?.size ?: 0

    override fun setDataList(list: List<Any>?) {
        mDataList = list
    }

    override fun onBindViewHolder(holder: CoreRVHolder, position: Int) {
        if (holder.getVMVariableId() != 0 && mViewModel != null) {
            holder.binding.setVariable(holder.getVMVariableId(), mViewModel)
        }
        if (holder.getDataVariableId() != 0) {
            holder.binding.setVariable(holder.getDataVariableId(), getItemData(position))
        }
    }

    override fun getItemId(position: Int) = position.toLong()

    override fun getItemCount(): Int = getDataCount()
}