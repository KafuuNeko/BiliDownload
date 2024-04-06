package cc.kafuu.bilidownload.common.core

interface IServerCallback<T> {
    fun onSuccess(httpCode: Int, code: Int, message: String, data: T)
    fun onFailure(httpCode: Int, code: Int, message: String)
}