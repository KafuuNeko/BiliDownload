package cc.kafuu.bilidownload.common.room.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import cc.kafuu.bilidownload.common.room.dao.BiliVideoDao
import cc.kafuu.bilidownload.common.room.dao.DownloadDashDao
import cc.kafuu.bilidownload.common.room.dao.DownloadResourceDao
import cc.kafuu.bilidownload.common.room.dao.DownloadTaskDao
import cc.kafuu.bilidownload.common.room.entity.BiliVideoMainEntity
import cc.kafuu.bilidownload.common.room.entity.BiliVideoPartEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadDashEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity

@Database(
    entities =
    [
        BiliVideoMainEntity::class,
        BiliVideoPartEntity::class,
        DownloadTaskEntity::class,
        DownloadResourceEntity::class,
        DownloadDashEntity::class
    ],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        @Volatile
        private var mInstance: AppDatabase? = null

        fun requireInstance(context: Context) = mInstance ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "app.db" // 数据库名称
            ).build()
            mInstance = instance
            // 返回实例
            instance
        }
    }

    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun downloadResourceDao(): DownloadResourceDao
    abstract fun biliVideoDao(): BiliVideoDao
    abstract fun downloadDashDao(): DownloadDashDao
}