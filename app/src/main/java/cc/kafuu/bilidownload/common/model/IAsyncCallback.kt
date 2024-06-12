package cc.kafuu.bilidownload.common.model

interface IAsyncCallback<D, E> {
    fun onSuccess(data: D)
    fun onFailure(exception: E)
}