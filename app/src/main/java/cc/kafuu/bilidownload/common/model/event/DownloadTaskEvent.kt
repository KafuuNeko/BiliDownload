package cc.kafuu.bilidownload.common.model.event

import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity

abstract class DownloadTaskEvent(val entity: DownloadTaskEntity)
