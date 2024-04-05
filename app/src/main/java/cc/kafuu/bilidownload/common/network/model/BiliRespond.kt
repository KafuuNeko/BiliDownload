package cc.kafuu.bilidownload.common.network.model

data class BiliRespond<T> (
    val code: Int,
    val message: String,
    val ttl: Int,
    val data: T?
)