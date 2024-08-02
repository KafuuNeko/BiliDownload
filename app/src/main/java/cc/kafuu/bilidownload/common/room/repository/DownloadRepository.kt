package cc.kafuu.bilidownload.common.room.repository

import android.database.sqlite.SQLiteConstraintException
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.constant.DashType
import cc.kafuu.bilidownload.common.constant.DownloadResourceType
import cc.kafuu.bilidownload.common.model.bili.BiliDashModel
import cc.kafuu.bilidownload.common.room.entity.DownloadDashEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import java.io.File

object DownloadRepository {
    private val mDownloadTaskDao by lazy { CommonLibs.requireAppDatabase().downloadTaskDao() }
    private val mDownloadDashDao by lazy { CommonLibs.requireAppDatabase().downloadDashDao() }
    private val mDownloadResourceDao by lazy {
        CommonLibs.requireAppDatabase().downloadResourceDao()
    }

    suspend fun update(downloadTask: DownloadTaskEntity) {
        mDownloadTaskDao.update(downloadTask)
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
    * 删除下载任务及其相关联的记录，同时删除关联的文件和目录
    * */
    suspend fun deleteDownloadTask(taskEntityId: Long) {
        CommonLibs.requireDownloadCacheDir(taskEntityId).let {
            it.deleteRecursively()
            it.delete()
        }
        queryResourcesByTaskEntityId(taskEntityId).forEach {
            File(it.file).delete()
        }
        mDownloadTaskDao.deleteTaskByTaskEntityId(taskEntityId)
        mDownloadDashDao.deleteTaskByTaskEntityId(taskEntityId)
        mDownloadResourceDao.deleteTaskByTaskEntityId(taskEntityId)
    }

    /**
     * 查询下载任务详情集LiveData，返回的信息包含此任务信息以及视频等相关信息
     */
    fun queryDownloadTasksDetailsLiveData(vararg statuses: Int) = run {
        mDownloadTaskDao.queryDownloadTasksDetailsLiveData(*statuses)
    }

    /**
     * 根据下载任务ID获取下载任务实体实例
     */
    suspend fun getDownloadTaskByDownloadTaskId(downloadTaskId: Long) = run {
        mDownloadTaskDao.getDownloadTaskByDownloadTaskId(downloadTaskId)
    }

    /**
     * 根据下载任务实体id获取下载详情信息LiveData, 包含此任务信息以及视频等相关信息
     */
    fun queryDownloadTaskDetailByEntityId(entityId: Long) = run {
        mDownloadTaskDao.queryDownloadTaskDetailByEntityId(entityId)
    }

    /**
     * 根据下载任务实体id查询和此任务有关的资源
     */
    fun queryResourcesLiveDataByTaskEntityId(entityId: Long) = run {
        mDownloadResourceDao.queryResourcesLiveDataByTaskEntityId(entityId)
    }

    /**
     * 根据下载任务实体id查询和此任务有关的资源（LiveData）
     */
    private suspend fun queryResourcesByTaskEntityId(taskEntityId: Long) = run {
        mDownloadResourceDao.queryResourcesByTaskEntityId(taskEntityId)
    }

    /**
     * 根据资源id获取资源记录实例
     */
    fun queryResourceLiveDataById(resourceId: Long) = run {
        mDownloadResourceDao.queryResourceLiveDataById(resourceId)
    }

    /**
     * 根据资源id删除某个资源
     */
    suspend fun deleteResourceById(resourceId: Long) = run {
        mDownloadResourceDao.deleteById(resourceId)
    }

    /**
     * 查询相关状态的任务实体集
     */
    suspend fun queryDownloadTaskDetailByEntityId(vararg statuses: Int) = run {
        mDownloadTaskDao.queryDownloadTask(*statuses)
    }

    /**
     * 根据下载任务的实体id取得此任务的实体实例
     */
    suspend fun getDownloadTaskById(entityId: Long) = run {
        mDownloadTaskDao.getDownloadTaskById(entityId)
    }
}