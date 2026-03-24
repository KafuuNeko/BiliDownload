package cc.kafuu.bilidownload.common.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import cc.kafuu.bilidownload.common.adapter.holder.ItemDownloadRecordHolder
import cc.kafuu.bilidownload.common.core.viewbinding.CoreRVAdapter
import cc.kafuu.bilidownload.common.core.viewbinding.CoreRVHolder
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment.HistoryViewModel

class DownloadHistoryRVAdapter(viewModel: HistoryViewModel, context: Context) :
    CoreRVAdapter<HistoryViewModel>(viewModel, context) {

    private var mIsMultiSelectMode = false
    private var mSelectedIds: Set<Long> = emptySet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoreRVHolder<*> {
        return ItemDownloadRecordHolder(parent)
    }

    override fun onBindViewHolder(holder: CoreRVHolder<*>, position: Int) {
        super.onBindViewHolder(holder, position)
        val data = getItemData(position) as? DownloadTaskWithVideoDetails ?: return
        val recordHolder = holder as? ItemDownloadRecordHolder ?: return
        val checkBox = recordHolder.binding.cbSelect
        if (mIsMultiSelectMode) {
            checkBox.visibility = View.VISIBLE
            checkBox.isChecked = mSelectedIds.contains(data.downloadTask.id)
        } else {
            checkBox.visibility = View.GONE
            checkBox.isChecked = false
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateMultiSelectState(isMultiSelectMode: Boolean, selectedIds: Set<Long>) {
        val modeChanged = mIsMultiSelectMode != isMultiSelectMode
        mIsMultiSelectMode = isMultiSelectMode
        mSelectedIds = selectedIds
        if (modeChanged) {
            notifyDataSetChanged()
        } else {
            notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        }
    }

    companion object {
        private const val PAYLOAD_SELECTION = "selection"
    }

    override fun onBindViewHolder(holder: CoreRVHolder<*>, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            val data = getItemData(position) as? DownloadTaskWithVideoDetails ?: return
            val recordHolder = holder as? ItemDownloadRecordHolder ?: return
            val checkBox = recordHolder.binding.cbSelect
            checkBox.isChecked = mSelectedIds.contains(data.downloadTask.id)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }
}