package cc.kafuu.bilidownload.common.core

import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView

abstract class CoreRecyclerViewAdapter<V : ViewDataBinding, VM : CoreViewModel>(
    protected val mViewModel: VM? = null
) : IEntityContainer, RecyclerView.Adapter<CoreRecyclerViewHolder>() {
    private var mEntityList: List<CoreEntity>? = null

    protected fun getItemEntity(position: Int): CoreEntity {
        val list = mEntityList ?: throw IllegalStateException("Entity list is not initialized.")
        if (position < 0 || position >= list.size) {
            throw IndexOutOfBoundsException("Position $position is out of bounds for list size ${list.size}.")
        }
        return list[position]
    }

    override fun getEntityCount(): Int = mEntityList?.size ?: 0

    override fun setEntityList(entity: List<CoreEntity>?) {
        mEntityList = entity
    }

    override fun onBindViewHolder(holder: CoreRecyclerViewHolder, position: Int) {
        if (holder.getVMVariableId() != 0 && mViewModel != null) {
            holder.binding.setVariable(holder.getVMVariableId(), mViewModel)
        }
        if (holder.getDataVariableId() != 0) {
            holder.binding.setVariable(holder.getDataVariableId(), getItemEntity(position))
        }
    }

    override fun getItemId(position: Int) = position.toLong()

    override fun getItemCount(): Int = getEntityCount()
}