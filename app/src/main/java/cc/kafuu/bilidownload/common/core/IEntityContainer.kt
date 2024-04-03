package cc.kafuu.bilidownload.common.core

import cc.kafuu.bilidownload.common.core.CoreEntity

interface IEntityContainer {
    fun setEntityList(entity: List<CoreEntity>?): Unit
    fun getEntityCount(): Int
}