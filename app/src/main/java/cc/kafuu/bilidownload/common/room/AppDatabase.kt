package cc.kafuu.bilidownload.common.room

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RenameColumn
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import cc.kafuu.bilidownload.common.room.dao.BiliVideoDao
import cc.kafuu.bilidownload.common.room.dao.DownloadDashDao
import cc.kafuu.bilidownload.common.room.dao.DownloadResourceDao
import cc.kafuu.bilidownload.common.room.dao.DownloadTaskDao
import cc.kafuu.bilidownload.common.room.dao.SearchRecordDao
import cc.kafuu.bilidownload.common.room.dao.ViewHistoryDao
import cc.kafuu.bilidownload.common.room.entity.BiliVideoMainEntity
import cc.kafuu.bilidownload.common.room.entity.BiliVideoPartEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadDashEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.room.entity.SearchRecordEntity
import cc.kafuu.bilidownload.common.room.entity.ViewHistoryEntity

@Database(
    entities =
        [
            BiliVideoMainEntity::class,
            BiliVideoPartEntity::class,
            DownloadTaskEntity::class,
            DownloadResourceEntity::class,
            DownloadDashEntity::class,
            SearchRecordEntity::class,
            ViewHistoryEntity::class
        ],
    exportSchema = true,
    version = 4,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = AutoMigrationSpecVersion1To2::class),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4)
    ]
)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        @Volatile
        private var mInstance: AppDatabase? = null

        fun requireInstance(context: Context) = mInstance ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "app.db"
            ).build()
            mInstance = instance
            instance
        }
    }

    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun downloadResourceDao(): DownloadResourceDao
    abstract fun biliVideoDao(): BiliVideoDao
    abstract fun downloadDashDao(): DownloadDashDao
    abstract fun searchRecordDao(): SearchRecordDao
    abstract fun viewHistoryDao(): ViewHistoryDao
}


@RenameColumn.Entries(
    RenameColumn("DownloadTask", "downloadTaskId", "groupId"),
    RenameColumn("DownloadResource", "taskEntityId", "taskId"),
    RenameColumn("DownloadDash", "taskEntityId", "taskId"),
)
class AutoMigrationSpecVersion1To2 : AutoMigrationSpec