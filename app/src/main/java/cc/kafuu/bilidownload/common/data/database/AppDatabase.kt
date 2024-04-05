package cc.kafuu.bilidownload.common.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import cc.kafuu.bilidownload.common.data.dao.DownloadHistoryDao
import cc.kafuu.bilidownload.common.data.entity.BiliVideoMainEntity
import cc.kafuu.bilidownload.common.data.entity.BiliVideoPartEntity
import cc.kafuu.bilidownload.common.data.entity.DownloadHistoryEntity
import cc.kafuu.bilidownload.common.data.entity.ResourceEntity

@Database(
    entities =
    [
        BiliVideoMainEntity::class,
        BiliVideoPartEntity::class,
        DownloadHistoryEntity::class,
        ResourceEntity::class
    ],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadHistoryDao(): DownloadHistoryDao
}