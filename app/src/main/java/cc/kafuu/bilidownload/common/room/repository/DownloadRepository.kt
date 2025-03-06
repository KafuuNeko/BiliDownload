package cc.kafuu.bilidownload.common.room.repository

import android.database.sqlite.SQLiteConstraintException
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.constant.DashType
import cc.kafuu.bilidownload.common.constant.DownloadResourceType
import cc.kafuu.bilidownload.common.model.TaskStatus
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
                taskId = taskId,
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
     * @brief 为指定的任务登记资源
     * */
    suspend fun registerResource(
        downloadTaskId: Long,
        resourceName: String,
        @DownloadResourceType downloadResourceType: Int,
        resourceFile: File,
        mimeType: String
    ) = DownloadResourceEntity(
        taskId = downloadTaskId,
        type = downloadResourceType,
        name = resourceName,
        mimeType = mimeType,
        storageSizeBytes = resourceFile.length(),
        creationTime = System.currentTimeMillis(),
        file = resourceFile.path
    ).let { mDownloadResourceDao.insert(it) }

    suspend fun registerResource(
        downloadTaskEntity: DownloadTaskEntity,
        downloadDashEntity: DownloadDashEntity,
    ): Long {
        val downloadResourceType = when (downloadDashEntity.type) {
            DashType.AUDIO -> DownloadResourceType.AUDIO
            DashType.VIDEO -> DownloadResourceType.VIDEO
            else -> throw IllegalArgumentException("Unknown download resource type")
        }
        val resourceName = if (downloadDashEntity.type == DashType.AUDIO) "AUDIO" else "VIDEO"
        return registerResource(
            downloadTaskEntity.id,
            resourceName,
            downloadResourceType,
            downloadDashEntity.getOutputFile(),
            downloadDashEntity.mimeType
        )
    }

    /**
     * @brief 删除下载任务及其相关联的记录，同时删除关联的文件和目录
     */
    suspend fun deleteDownloadTask(taskId: Long) {
        CommonLibs.requireDownloadCacheDir(taskId).let {
            it.deleteRecursively()
            it.delete()
        }
        queryResourcesByTaskId(taskId).forEach {
            File(it.file).delete()
        }
        mDownloadTaskDao.deleteTaskByTaskId(taskId)
        mDownloadDashDao.deleteTaskByTaskId(taskId)
        mDownloadResourceDao.deleteTaskByTaskId(taskId)
    }

    /**
     * @brief 查询下载任务详情集LiveData，返回的信息包含此任务信息以及视频等相关信息
     */
    fun queryDownloadTasksDetailsLiveData(status: List<TaskStatus>) = run {
        mDownloadTaskDao.queryDownloadTasksDetailsLiveData(*status.map { it.code }.toIntArray())
    }

    /**
     * @brief 查询下载任务详情集LiveData，返回的信息包含此任务信息以及视频等相关信息
     */
    suspend fun queryDownloadTasksDetails(status: List<TaskStatus>) = run {
        mDownloadTaskDao.queryDownloadTasksDetails(*status.map { it.code }.toIntArray())
    }

    /**
     * @brief 根据下载任务组ID获取下载任务实体实例
     */
    suspend fun getDownloadTaskByGroupId(groupId: Long) = run {
        mDownloadTaskDao.getDownloadTaskByGroupId(groupId)
    }

    /**
     * @brief 根据下载任务实体id获取下载详情信息LiveData, 包含此任务信息以及视频等相关信息
     */
    fun queryDownloadTaskDetailByTaskId(entityId: Long) = run {
        mDownloadTaskDao.queryDownloadTaskDetailByTaskId(entityId)
    }

    /**
     * @brief 根据下载任务实体id查询和此任务有关的资源
     */
    fun queryResourcesLiveDataByTaskId(entityId: Long) = run {
        mDownloadResourceDao.queryResourcesLiveDataByTaskId(entityId)
    }

    /**
     * @brief 根据下载任务实体id查询和此任务有关的资源（LiveData）
     */
    private suspend fun queryResourcesByTaskId(taskId: Long) = run {
        mDownloadResourceDao.queryResourcesByTaskId(taskId)
    }

    /**
     * @brief 根据资源id获取资源记录实例
     */
    fun queryResourceLiveDataById(resourceId: Long) = run {
        mDownloadResourceDao.queryResourceLiveDataById(resourceId)
    }

    /**
     * @brief 根据资源id删除某个资源
     */
    suspend fun deleteResourceById(resourceId: Long) = run {
        mDownloadResourceDao.deleteById(resourceId)
    }

    /**
     * @brief 查询相关状态的任务实体集
     */
    suspend fun queryDownloadTaskDetailByTaskId(vararg statuses: TaskStatus) = run {
        mDownloadTaskDao.queryDownloadTask(*(statuses.map { it.code }.toIntArray()))
    }

    /**
     * @brief 根据下载任务的实体id取得此任务的实体实例
     */
    suspend fun getDownloadTaskByTaskId(id: Long) = run {
        mDownloadTaskDao.getDownloadTaskByTaskId(id)
    }
}