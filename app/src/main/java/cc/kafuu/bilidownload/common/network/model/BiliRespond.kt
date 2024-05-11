package cc.kafuu.bilidownload.common.network.model

data class BiliRespond<T> (
    val code: Int,
    val message: String,
    val ttl: Int,
    val data: T?,
    // Bili莫名其妙的返回，有时不适用data而使用result :(
    val result: T?
)