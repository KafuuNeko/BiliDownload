package cc.kafuu.bilidownload.common.core

interface IDataContainer {
    fun setDataList(list: List<Any>?): Unit
    fun getDataCount(): Int
}