package cc.kafuu.bilidownload.common.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.adapter.holder.ItemDownloadRecordHolder
import cc.kafuu.bilidownload.common.core.viewbinding.CoreRVAdapter
import cc.kafuu.bilidownload.common.core.viewbinding.CoreRVHolder
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import cc.kafuu.bilidownload.common.utils.DensityUtils
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
        val isSelected = mSelectedIds.contains(data.downloadTask.id)
        bindSelectionState(recordHolder, isSelected)
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
        private const val SELECTED_STROKE_WIDTH_DP = 1.5f
        private const val DEFAULT_STROKE_WIDTH_DP = 1f
    }

    override fun onBindViewHolder(holder: CoreRVHolder<*>, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            val data = getItemData(position) as? DownloadTaskWithVideoDetails ?: return
            val recordHolder = holder as? ItemDownloadRecordHolder ?: return
            val isSelected = mSelectedIds.contains(data.downloadTask.id)
            bindSelectionState(recordHolder, isSelected)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun bindSelectionState(recordHolder: ItemDownloadRecordHolder, isSelected: Boolean) {
        val cardRoot = recordHolder.binding.cardRoot

        if (mIsMultiSelectMode && isSelected) {
            cardRoot.setCardBackgroundColor(
                ContextCompat.getColor(mContext, R.color.card_selected_background)
            )
            cardRoot.strokeColor = ContextCompat.getColor(mContext, R.color.card_selected_stroke)
            cardRoot.strokeWidth = DensityUtils.dpToPx(mContext, SELECTED_STROKE_WIDTH_DP)
        } else {
            cardRoot.setCardBackgroundColor(
                ContextCompat.getColor(mContext, R.color.general_item_background_color)
            )
            cardRoot.strokeColor = ContextCompat.getColor(mContext, R.color.card_unselected_stroke)
            cardRoot.strokeWidth = DensityUtils.dpToPx(mContext, DEFAULT_STROKE_WIDTH_DP)
        }
    }
}
