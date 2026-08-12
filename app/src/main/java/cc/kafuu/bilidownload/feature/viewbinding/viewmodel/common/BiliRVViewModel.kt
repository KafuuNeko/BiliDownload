package cc.kafuu.bilidownload.feature.viewbinding.viewmodel.common

/**
 * B 站列表页面的公共 ViewModel，只定义列表数据刷新入口。
 */
open class BiliRVViewModel : RVViewModel() {
    /** 刷新数据列表。 */
    open fun onRefreshData(
        onSucceeded: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null,
    ) = Unit

    /** 加载更多数据。 */
    open fun onLoadMoreData(
        onSucceeded: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null,
    ) = Unit
}
