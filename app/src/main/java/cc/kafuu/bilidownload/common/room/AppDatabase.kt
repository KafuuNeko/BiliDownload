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
    exportSchema = true,
    version = 2,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = AutoMigrationSpecVersion1To2::class),
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
}


@RenameColumn.Entries(
    RenameColumn("DownloadTask", "downloadTaskId", "groupId"),
    RenameColumn("DownloadResource", "taskEntityId", "taskId"),
    RenameColumn("DownloadDash", "taskEntityId", "taskId"),
)
class AutoMigrationSpecVersion1To2 : AutoMigrationSpec