package cc.kafuu.bilidownload.common.adapter.holder

import android.view.LayoutInflater
import android.view.ViewGroup
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.common.core.CoreRVHolder
import cc.kafuu.bilidownload.common.event.DownloadStatusChangeEvent
import cc.kafuu.bilidownload.common.room.dto.DownloadTaskWithVideoDetails
import cc.kafuu.bilidownload.databinding.ItemHistoryBinding
import cc.kafuu.bilidownload.viewmodel.fragment.HistoryViewModel
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe


class ItemHistoryHolder(parent: ViewGroup, private val mViewModel: HistoryViewModel) :
    CoreRVHolder<ItemHistoryBinding>(
        ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    ) {

    private var mEntity: DownloadTaskWithVideoDetails? = null

    override fun getDataVariableId(): Int = BR.data

    override fun getVMVariableId(): Int = BR.viewModel

    override fun onBinding(data: Any, position: Int) {
        mEntity = data as DownloadTaskWithVideoDetails

    }

    override fun onViewAttachedToWindow() {
        super.onViewAttachedToWindow()
//        EventBus.getDefault().register(this)
    }

    override fun onViewDetachedFromWindow() {
        super.onViewDetachedFromWindow()
//        EventBus.getDefault().unregister(this)
    }

//    @Subscribe
//    fun onMessageEvent(event: DownloadStatusChangeEvent) {
//
//    }
}