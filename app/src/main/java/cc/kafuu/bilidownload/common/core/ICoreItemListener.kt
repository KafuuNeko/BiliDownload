package cc.kafuu.bilidownload.common.core

import cc.kafuu.bilidownload.common.core.CoreEntity

interface ICoreItemListener {
    fun onItemClick(position: Int, entity: CoreEntity): Boolean
}