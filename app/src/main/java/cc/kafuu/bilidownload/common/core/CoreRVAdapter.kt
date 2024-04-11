package cc.kafuu.bilidownload.common.core

import android.annotation.SuppressLint
import android.content.Context
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView

/**
 * [RecyclerView] 适配器实现的抽象基类，通过支持数据绑定和可选的视图模型绑定，
 * 提供了一种标准化方式来管理和展示列表数据，子类一般情况下只负责根据情况分配Holder
 *
 * @param VM 继承自 [CoreViewModel] 的视图模型类。
 * @property mViewModel 可选的视图模型实例，可用于数据绑定。
 */
abstract class CoreRVAdapter<VM : CoreViewModel>(
    protected val mViewModel: VM? = null,
    protected val mContext: Context
) : IDataContainer, RecyclerView.Adapter<CoreRVHolder<*>>() {
    protected var mDataList: List<Any>? = null

    /**
     * 根据[position]获取列表项数据。
     *
     * @param position 列表项的位置。
     * @return 返回指定位置的数据。
     * @throws IllegalStateException 如果数据列表未初始化。
     * @throws IndexOutOfBoundsException 如果给定位置超出数据列表范围。
     */
    protected fun getItemData(position: Int): Any {
        val list = mDataList ?: throw IllegalStateException("Entity list is not initialized.")
        if (position < 0 || position >= list.size) {
            throw IndexOutOfBoundsException("Position $position is out of bounds for list size ${list.size}.")
        }
        return list[position]
    }

    override fun getDataCount(): Int = mDataList?.size ?: 0

    @SuppressLint("NotifyDataSetChanged")
    override fun setDataList(list: List<Any>?) {
        mDataList = list
        notifyDataSetChanged()
    }

    /**
     * 绑定数据和ViewModel到 ViewHolder。
     *
     * @param holder 用于展示列表项的 ViewHolder。
     * @param position 列表项的位置。
     */
    override fun onBindViewHolder(holder: CoreRVHolder<*>, position: Int) {
        if (holder.getVMVariableId() != 0 && mViewModel != null) {
            holder.binding.setVariable(holder.getVMVariableId(), mViewModel)
        }
        if (holder.getDataVariableId() != 0) {
            holder.binding.setVariable(holder.getDataVariableId(), getItemData(position))
        }
        holder.onBinding(getItemData(position), position)
    }

    override fun onViewAttachedToWindow(holder: CoreRVHolder<*>) {
        super.onViewAttachedToWindow(holder)
        holder.onViewAttachedToWindow()
    }

    override fun onViewDetachedFromWindow(holder: CoreRVHolder<*>) {
        super.onViewDetachedFromWindow(holder)
        holder.onViewDetachedFromWindow()
    }

    override fun getItemId(position: Int) = position.toLong()

    override fun getItemCount(): Int = getDataCount()
}