package cc.kafuu.bilidownload.common.adapter

import android.content.Context
import android.view.ViewGroup
import cc.kafuu.bilidownload.common.adapter.holder.ItemAVCodecHolder
import cc.kafuu.bilidownload.common.adapter.holder.ItemAVFormatHolder
import cc.kafuu.bilidownload.common.core.CoreRVAdapter
import cc.kafuu.bilidownload.common.core.CoreRVHolder
import cc.kafuu.bilidownload.common.model.av.AVCodec
import cc.kafuu.bilidownload.common.model.av.AVFormat
import cc.kafuu.bilidownload.viewmodel.dialog.ConvertViewModel

class ConvertSelectRVAdapter(viewModel: ConvertViewModel, context: Context) :
    CoreRVAdapter<ConvertViewModel>(viewModel, context) {

    companion object {
        private const val TYPE_AVFORMAT = 0
        private const val TYPE_AVCODEC = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItemData(position)) {
            is AVFormat -> TYPE_AVFORMAT
            is AVCodec -> TYPE_AVCODEC
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoreRVHolder<*> {
        return when (viewType) {
            TYPE_AVFORMAT -> ItemAVFormatHolder(parent)
            TYPE_AVCODEC -> ItemAVCodecHolder(parent)
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }
}