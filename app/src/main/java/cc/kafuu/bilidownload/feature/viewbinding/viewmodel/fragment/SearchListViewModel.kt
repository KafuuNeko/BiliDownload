package cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment

import android.util.Log
import cc.kafuu.bilidownload.common.constant.SearchType
import cc.kafuu.bilidownload.common.model.LoadingStatus
import cc.kafuu.bilidownload.common.model.bili.BiliMediaModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliSearchData
import cc.kafuu.bilidownload.common.network.model.BiliSearchMediaResultData
import cc.kafuu.bilidownload.common.network.model.BiliSearchVideoResultData
import cc.kafuu.bilidownload.common.network.model.BiliSeasonData
import cc.kafuu.bilidownload.common.network.model.BiliVideoData
import cc.kafuu.bilidownload.common.utils.BiliAddressParser
import cc.kafuu.bilidownload.common.utils.BvConvertUtils
import cc.kafuu.bilidownload.common.utils.NetworkUtils
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.common.BiliResourceRVViewModel
import java.util.Locale

/** 管理关键词检索与分享地址解析，详情解析失败时回退到普通搜索并维护分页状态。 */
class SearchListViewModel : BiliResourceRVViewModel() {
    var keyword: String? = null

    @SearchType
    var searchType: Int = SearchType.VIDEO

    private var mNextPage = 1

    companion object {
        private const val TAG = "SearchListViewModel"
        private val mBiliSearchRepository = NetworkManager.biliSearchRepository
        private val mBiliVideoRepository = NetworkManager.biliVideoRepository
    }

    /**
     * 刷新数据列表
     */
    override fun onRefreshData(onSucceeded: (() -> Unit)?, onFailed: (() -> Unit)?) {
        doSearch(
            loadingStatus = LoadingStatus.loadingStatus(visibility = false),
            loadMore = false,
            forceSearch = true,
            onSucceeded = onSucceeded, onFailed = onFailed
        )
    }

    /**
     * 加载更多数据
     */
    override fun onLoadMoreData(onSucceeded: (() -> Unit)?, onFailed: (() -> Unit)?) {
        doSearch(
            loadingStatus = LoadingStatus.loadingStatus(visibility = false),
            loadMore = true,
            forceSearch = true,
            onSucceeded = onSucceeded, onFailed = onFailed
        )
    }

    /**
     * 根据当前关键词检索或解析详情，加载中的请求由状态守卫拦截。
     *
     * @param loadMore 是否延续当前分页，为 false 时重置页码。
     * @param forceSearch 是否直接进行关键词检索，跳过链接与内容 ID 的解析。
     */
    fun doSearch(
        loadingStatus: LoadingStatus,
        loadMore: Boolean,
        forceSearch: Boolean = false,
        onSucceeded: (() -> Unit)? = null,
        onFailed: (() -> Unit)? = null
    ) {
        if (keyword == null || loadingStatusMessageMutableLiveData.value?.statusCode == LoadingStatus.CODE_LOADING) {
            if (keyword != null) Log.d(TAG, "doSearch: Search execution")
            return
        }
        val keyword = keyword ?: return

        setLoadingStatus(status = loadingStatus)

        // 非强制搜索的情况，尝试解析搜索内容
        if (!forceSearch) {
            // 搜索的内容是否包含链接，如果包含则解析链接
            if (!loadMore && NetworkUtils.containsUrl(text = keyword)) {
                doAnalysisUrl(text = keyword)
                return
            }
            // 尝试直接解析搜索内容中是否有视频id
            parseAddress(address = keyword)?.let {
                tryAnalysisId(id = it)
                return
            }
        }

        if (!loadMore) mNextPage = 1

        when (searchType) {
            SearchType.VIDEO -> mBiliSearchRepository.requestSearchVideo(
                keyword = keyword, page = mNextPage,
                callback = createSearchCallback(
                    onSucceeded = onSucceeded,
                    onFailed = onFailed,
                    loadMore = loadMore
                )
            )

            SearchType.MEDIA_BANGUMI -> mBiliSearchRepository.requestSearchMediaBangumi(
                keyword = keyword, page = mNextPage,
                callback = createSearchCallback(
                    onSucceeded = onSucceeded,
                    onFailed = onFailed,
                    loadMore = loadMore
                )
            )

            SearchType.MEDIA_FT -> mBiliSearchRepository.requestSearchMediaFt(
                keyword = keyword, page = mNextPage,
                callback = createSearchCallback(
                    onSucceeded = onSucceeded,
                    onFailed = onFailed,
                    loadMore = loadMore
                )
            )
        }
    }

    /**
     * 强制搜索
     */
    private fun forceSearch() {
        // 失败兜底路径需要绕过 doSearch 的"已在加载中"早返回守卫。
        // 否则前一次失败回调触发的兜底会被静默丢弃，导致 loading 状态永远不会被清除。
        setLoadingStatus(status = LoadingStatus.waitStatus())
        doSearch(
            loadingStatus = LoadingStatus.loadingStatus(),
            loadMore = false,
            forceSearch = true
        )
    }

    /**
     * 尝试通过分析搜索文本中的链接直接跳转到视频详情页
     */
    private fun doAnalysisUrl(text: String) {
        // 如果是分享短链接
        BiliAddressParser.findB23ShortUrl(text = text)?.let { shortUrl ->
            object : IServerCallback<String> {
                override fun onSuccess(httpCode: Int, code: Int, message: String, data: String) {
                    doAnalysisUrl(text = data)
                }

                override fun onFailure(httpCode: Int, code: Int, message: String) {
                    Log.e(TAG, "onFailure: $message")
                    forceSearch()
                }
            }.also {
                Log.d(TAG, "doAnalysisUrl: $text")
                NetworkUtils.redirection(url = shortUrl, callback = it)
            }
            return
        }
        // 尝试从链接中提取BVID或AV、EP、SS
        val id = parseAddress(address = text) ?: run {
            forceSearch()
            return
        }
        tryAnalysisId(id = id)
    }

    /**
     * 尝试直接解析视频id
     */
    private fun tryAnalysisId(id: String) = try {
        doAnalysisId(id = id)
    } catch (e: Exception) {
        e.printStackTrace()
        forceSearch()
    }

    /**
     * 根据ID调用其相对应的请求
     */
    private fun doAnalysisId(id: String) {
        if (id.length <= 2) {
            forceSearch()
            return
        }

        when (id.substring(0, 2).uppercase(Locale.ROOT)) {
            "BV" -> mBiliVideoRepository.requestVideoDetail(
                bvid = id,
                callback = createVideoDetailCallback()
            )

            "AV" -> mBiliVideoRepository.requestVideoDetail(
                bvid = BvConvertUtils.av2bv(av = id.substring(2)),
                callback = createVideoDetailCallback()
            )

            "SS" -> mBiliVideoRepository.requestSeasonDetailBySeasonId(
                seasonId = id.substring(2).toLong(),
                callback = createSeasonDetailCallback()
            )

            "EP" -> mBiliVideoRepository.requestSeasonDetailByEpId(
                epId = id.substring(2).toLong(),
                callback = createSeasonDetailCallback()
            )

            else -> forceSearch()
        }
    }

    /**
     * 获取视频详情回调
     */
    private fun createVideoDetailCallback() = object : IServerCallback<BiliVideoData> {
        override fun onSuccess(httpCode: Int, code: Int, message: String, data: BiliVideoData) {
            enterDetails(element = BiliVideoModel.create(data = data))
            postLoadingStatus(status = LoadingStatus.doneStatus())
        }

        override fun onFailure(httpCode: Int, code: Int, message: String) {
            Log.e(TAG, "onFailure: $message")
            forceSearch()
        }
    }

    /**
     * 获取电视剧详情回调
     */
    private fun createSeasonDetailCallback() = object : IServerCallback<BiliSeasonData> {
        override fun onSuccess(httpCode: Int, code: Int, message: String, data: BiliSeasonData) {
            enterDetails(element = BiliMediaModel.create(data = data))
            postLoadingStatus(status = LoadingStatus.doneStatus())
        }

        override fun onFailure(httpCode: Int, code: Int, message: String) {
            Log.e(TAG, "onFailure: $message")
            forceSearch()
        }
    }

    /**
     * 创建搜索结果回调接口
     */
    private fun <T> createSearchCallback(
        onSucceeded: (() -> Unit)?,
        onFailed: (() -> Unit)?,
        loadMore: Boolean
    ) = object : IServerCallback<BiliSearchData<T>> {
        override fun onSuccess(
            httpCode: Int,
            code: Int,
            message: String,
            data: BiliSearchData<T>
        ) {
            onSucceeded?.invoke()
            onLoadingCompleted(data = data, loadMore = loadMore)
        }

        override fun onFailure(httpCode: Int, code: Int, message: String) {
            onFailed?.invoke()
            LoadingStatus.errorStatus(visibility = !loadMore, message = message).let {
                postLoadingStatus(status = it)
            }
        }
    }

    /**
     * 搜索结果加载完成
     * 将搜索结果解析为列表数据
     */
    private fun onLoadingCompleted(data: BiliSearchData<*>, loadMore: Boolean) {
        Log.d(TAG, "onLoadingCompleted: $data")
        val searchData: MutableList<Any> = if (loadMore) {
            listMutableLiveData.value ?: mutableListOf()
        } else {
            mutableListOf()
        }
        searchData.addAll(data.result.orEmpty().mapNotNull { result ->
            when (result) {
                is BiliSearchVideoResultData -> if (result.type == "video") disposeResult(element = result) else null
                is BiliSearchMediaResultData -> disposeResult(element = result)
                else -> throw IllegalStateException("Unknown result from $result")
            }
        })
        updateList(newList = searchData)
        mNextPage++
    }

    /**
     * 将BiliSearchVideoResultData解析为BiliVideoModel
     */
    private fun disposeResult(element: BiliSearchVideoResultData) =
        BiliVideoModel.create(data = element)

    /**
     * 将BiliSearchMediaResultData解析为BiliMediaModel
     */
    private fun disposeResult(element: BiliSearchMediaResultData) =
        BiliMediaModel.create(data = element)

    /**
     * 根据url地址视频的BV号或AV号
     * @param address 要解析的视频地址
     */
    private fun parseAddress(address: String): String? {
        return BiliAddressParser.findContentId(text = address)
    }
}
