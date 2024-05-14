package cc.kafuu.bilidownload.common.room.repository

import android.database.sqlite.SQLiteConstraintException
import cc.kafuu.bilidownload.common.model.DashType
import cc.kafuu.bilidownload.common.model.DownloadResourceType
import cc.kafuu.bilidownload.common.model.bili.BiliDashModel
import cc.kafuu.bilidownload.common.room.entity.DownloadDashEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.utils.CommonLibs
import java.io.File

object DownloadRepository {
    private val mDownloadTaskDao by lazy { CommonLibs.requireAppDatabase().downloadTaskDao() }
    private val mDownloadDashDao by lazy { CommonLibs.requireAppDatabase().downloadDashDao() }
    private val mDownloadResourceDao by lazy {
        CommonLibs.requireAppDatabase().downloadResourceDao()
    }

    @Throws(IllegalStateException::class, SQLiteConstraintException::class)
    suspend fun createNewRecord(
        bvid: String,
        cid: Long,
        resources: List<BiliDashModel>
    ): Long {
        val taskId = mDownloadTaskDao.insert(
            DownloadTaskEntity.createEntity(bvid, cid)
        ).also { if (it == -1L) throw IllegalStateException("Invalid download task id") }
        resources.map {
            DownloadDashEntity(
                dashId = it.dashId,
                taskEntityId = taskId,
                codecId = it.codecId,
                type = it.type,
                codecs = it.codecs,
                mimeType = it.mimeType
            )
        }.let { mDownloadDashDao.insertOrUpdate(*it.toTypedArray()) }
        return taskId
    }

    suspend fun queryDashList(entity: DownloadTaskEntity) =
        mDownloadDashDao.queryDashListByTaskEntityId(entity.id)

    /**
     * 为指定的任务登记资源
     * */
    suspend fun registerResource(
        downloadTaskEntity: DownloadTaskEntity,
        resourceName: String,
        @DownloadResourceType downloadResourceType: Int,
        resourceFile: File,
        mimeType: String
    ) = DownloadResourceEntity(
        taskEntityId = downloadTaskEntity.id,
        type = downloadResourceType,
        name = resourceName,
        mimeType = mimeType,
        storageSizeBytes = resourceFile.length(),
        creationTime = System.currentTimeMillis(),
        file = resourceFile.path
    ).also { mDownloadResourceDao.insert(it) }

    suspend fun registerResource(
        downloadTaskEntity: DownloadTaskEntity,
        downloadDashEntity: DownloadDashEntity,
    ): DownloadResourceEntity {
        val downloadResourceType = when (downloadDashEntity.type) {
            DashType.AUDIO -> DownloadResourceType.AUDIO
            DashType.VIDEO -> DownloadResourceType.VIDEO
            else -> throw IllegalArgumentException("Unknown download resource type")
        }
        val resourceName = if (downloadDashEntity.type == DashType.AUDIO) "AUDIO" else "VIDEO"
        return registerResource(
            downloadTaskEntity,
            resourceName,
            downloadResourceType,
            downloadDashEntity.getOutputFile(),
            downloadDashEntity.mimeType
        )
    }

    /*
    * 删除下载任务及其相关联的记录
    * */
    suspend fun deleteDownloadTask(taskEntityId: Long) {
        mDownloadTaskDao.deleteTaskByTaskEntityId(taskEntityId)
        mDownloadDashDao.deleteTaskByTaskEntityId(taskEntityId)
        mDownloadResourceDao.deleteTaskByTaskEntityId(taskEntityId)
    }
}