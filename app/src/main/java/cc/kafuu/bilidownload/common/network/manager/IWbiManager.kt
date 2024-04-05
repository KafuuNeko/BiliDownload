package cc.kafuu.bilidownload.common.network.manager

interface IWbiManager {
    fun generateSignature(urlPath: String, params: Map<String, Any>?): String?
}