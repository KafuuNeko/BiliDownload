package cc.kafuu.bilidownload.common.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import cc.kafuu.bilidownload.common.data.dao.DownloadTaskDao
import cc.kafuu.bilidownload.common.data.entity.BiliVideoMainEntity
import cc.kafuu.bilidownload.common.data.entity.BiliVideoPartEntity
import cc.kafuu.bilidownload.common.data.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.data.entity.ResourceEntity

@Database(
    entities =
    [
        BiliVideoMainEntity::class,
        BiliVideoPartEntity::class,
        DownloadTaskEntity::class,
        ResourceEntity::class
    ],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadHistoryDao(): DownloadTaskDao
}