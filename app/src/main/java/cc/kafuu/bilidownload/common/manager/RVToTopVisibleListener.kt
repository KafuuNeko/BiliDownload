package cc.kafuu.bilidownload.common.manager

import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class RVToTopVisibleListener(private var mIvTop: ImageView? = null) : RecyclerView.OnScrollListener() {
    private var firstViewPosition = 0
    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        super.onScrollStateChanged(recyclerView, newState)
        val manager = recyclerView.layoutManager as LinearLayoutManager?
            ?: return
        val firstVisibleItemPosition = manager.findFirstVisibleItemPosition()
        mIvTop?.visibility = if (
            newState == RecyclerView.SCROLL_STATE_IDLE &&
            firstVisibleItemPosition > firstViewPosition
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }
}