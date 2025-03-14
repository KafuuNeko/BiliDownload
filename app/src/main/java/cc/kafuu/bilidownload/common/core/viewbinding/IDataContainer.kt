package cc.kafuu.bilidownload.common.core.viewbinding

interface IDataContainer {
    fun setDataList(list: List<Any>?): Unit
    fun getDataCount(): Int
}