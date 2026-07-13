package cc.kafuu.bilidownload.common.room.repository

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.constant.DashType
import cc.kafuu.bilidownload.common.constant.DownloadResourceType
import cc.kafuu.bilidownload.common.model.AppModel
import cc.kafuu.bilidownload.common.model.DownloadPathMode
import cc.kafuu.bilidownload.common.model.TaskStatus
import cc.kafuu.bilidownload.common.model.bili.BiliDashModel
import cc.kafuu.bilidownload.common.room.entity.DownloadDashEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadResourceEntity
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.storage.ResourceStorage
import cc.kafuu.bilidownload.common.utils.DownloadFileNameUtils
import cc.kafuu.bilidownload.common.utils.MimeTypeUtils
import java.io.File

object DownloadRepository {
    private const val TAG = "DownloadRepository"

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
        id: Long = 0L,
        downloadTaskId: Long,
        resourceName: String,
        @DownloadResourceType downloadResourceType: Int,
        resourceFile: File,
        mimeType: String
    ): DownloadResourceEntity {
        val entity = DownloadResourceEntity(
            id = id,
            taskId = downloadTaskId,
            type = downloadResourceType,
            name = resourceName,
            mimeType = mimeType,
            storageSizeBytes = resourceFile.length(),
            creationTime = System.currentTimeMillis(),
            file = resourceFile.path
        )
        val id = mDownloadResourceDao.insert(entity)
        return entity.copy(id = id)
    }

    suspend fun registerResource(
        downloadTaskEntity: DownloadTaskEntity,
        downloadDashEntity: DownloadDashEntity,
    ): DownloadResourceEntity {
        val downloadResourceType = when (downloadDashEntity.type) {
            DashType.AUDIO -> DownloadResourceType.AUDIO
            DashType.VIDEO -> DownloadResourceType.VIDEO
            else -> throw IllegalArgumentException("Unknown download resource type")
        }
        val sourceFile = downloadDashEntity.getOutputFile()
        val resourceFile = prepareDownloadResourceFile(
            downloadTaskEntity = downloadTaskEntity,
            downloadResourceType = downloadResourceType,
            sourceFile = sourceFile,
            mimeType = downloadDashEntity.mimeType
        )
        val resourceName = if (isExternalResourceFileNameEnabled()) {
            resourceFile.nameWithoutExtension
        } else if (downloadDashEntity.type == DashType.AUDIO) {
            "AUDIO"
        } else {
            "VIDEO"
        }
        return registerResource(
            downloadTaskId = downloadTaskEntity.id,
            resourceName = resourceName,
            downloadResourceType = downloadResourceType,
            resourceFile = resourceFile,
            mimeType = downloadDashEntity.mimeType
        )
    }

    suspend fun buildMixedResourceOutputFile(
        downloadTaskEntity: DownloadTaskEntity,
        mimeType: String,
        fallbackBaseName: String
    ): File {
        if (!isExternalResourceFileNameEnabled()) {
            val suffix = MimeTypeUtils.getExtensionFromMimeType(mimeType) ?: "mkv"
            return File(CommonLibs.requireResourceWorkingDir(), "$fallbackBaseName.$suffix")
        }

        return buildExternalResourceFile(
            downloadTaskEntity = downloadTaskEntity,
            downloadResourceType = DownloadResourceType.MIXED,
            mimeType = mimeType,
            fallbackBaseName = fallbackBaseName
        )
    }

    fun getResourceNameForFile(
        @DownloadResourceType downloadResourceType: Int,
        resourceFile: File,
        fallbackName: String
    ): String {
        val isDownloadResourceType = when (downloadResourceType) {
            DownloadResourceType.AUDIO,
            DownloadResourceType.VIDEO,
            DownloadResourceType.MIXED -> true
            else -> false
        }
        return if (isExternalResourceFileNameEnabled() && isDownloadResourceType) {
            resourceFile.nameWithoutExtension
        } else {
            fallbackName
        }
    }

    private suspend fun prepareDownloadResourceFile(
        downloadTaskEntity: DownloadTaskEntity,
        @DownloadResourceType downloadResourceType: Int,
        sourceFile: File,
        mimeType: String
    ): File {
        if (!isExternalResourceFileNameEnabled()) return sourceFile

        val targetFile = buildExternalResourceFile(
            downloadTaskEntity = downloadTaskEntity,
            downloadResourceType = downloadResourceType,
            mimeType = mimeType,
            fallbackBaseName = sourceFile.nameWithoutExtension
        )
        if (sourceFile.absolutePath == targetFile.absolutePath || !sourceFile.exists()) {
            return sourceFile
        }
        moveFile(sourceFile, targetFile)
        return targetFile
    }

    private suspend fun buildExternalResourceFile(
        downloadTaskEntity: DownloadTaskEntity,
        @DownloadResourceType downloadResourceType: Int,
        mimeType: String,
        fallbackBaseName: String
    ): File {
        val fallbackSuffix = if (downloadResourceType == DownloadResourceType.MIXED) {
            "mkv"
        } else {
            "bin"
        }
        val suffix = MimeTypeUtils.getExtensionFromMimeType(mimeType) ?: fallbackSuffix
        return DownloadFileNameUtils.buildUniqueFile(
            directory = CommonLibs.requireResourceWorkingDir(),
            template = getTemplateForResourceType(downloadResourceType),
            context = getFileNameTemplateContext(downloadTaskEntity),
            extension = suffix,
            fallbackBaseName = fallbackBaseName
        )
    }

    private suspend fun getFileNameTemplateContext(
        downloadTaskEntity: DownloadTaskEntity
    ): DownloadFileNameUtils.TemplateContext {
        val details = mDownloadTaskDao.queryDownloadTaskDetailsByTaskId(downloadTaskEntity.id)
        return DownloadFileNameUtils.TemplateContext(
            videoName = details?.title ?: downloadTaskEntity.biliBvid,
            partName = details?.partTitle ?: downloadTaskEntity.biliCid.toString()
        )
    }

    private fun getTemplateForResourceType(
        @DownloadResourceType downloadResourceType: Int
    ) = when (downloadResourceType) {
        DownloadResourceType.AUDIO -> AppModel.audioResourceFileNameTemplate
        DownloadResourceType.VIDEO -> AppModel.videoResourceFileNameTemplate
        DownloadResourceType.MIXED -> AppModel.mixedResourceFileNameTemplate
        else -> DownloadFileNameUtils.DEFAULT_MIXED_TEMPLATE
    }

    private fun isExternalResourceFileNameEnabled(): Boolean {
        return AppModel.downloadPathMode == DownloadPathMode.EXTERNAL
    }

    private fun moveFile(source: File, target: File) {
        target.parentFile?.mkdirs()
        if (source.renameTo(target)) return

        source.inputStream().use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        source.delete()
    }

    /**
     * 插入或更新资源实体
     */
    suspend fun updateOrInsertResource(
        entity: DownloadResourceEntity
    ): DownloadResourceEntity {
        val id = mDownloadResourceDao.insert(entity)
        return entity.copy(id = id)
    }

    suspend fun publishResourceIfNeeded(
        entity: DownloadResourceEntity
    ): DownloadResourceEntity {
        val published = ResourceStorage.publishIfNeeded(entity)
        return if (published == entity) entity else updateOrInsertResource(published)
    }

    suspend fun publishResourcesIfNeeded(taskId: Long): Boolean {
        var success = true
        queryResourcesByTaskId(taskId).forEach { resource ->
            try {
                publishResourceIfNeeded(resource)
            } catch (e: Exception) {
                success = false
                Log.e(TAG, "Unable to publish resource ${resource.id}", e)
            }
        }
        return success
    }

    /**
     * @brief 删除下载任务及其相关联的记录，同时删除关联的文件和目录
     */
    suspend fun deleteDownloadTask(taskId: Long): Boolean {
        var success = CommonLibs.requireDownloadCacheDir(taskId).deleteRecursively()
        queryResourcesByTaskId(taskId).forEach { resource ->
            if (!ResourceStorage.delete(resource)) success = false
        }
        mDownloadDashDao.queryDashListByTaskEntityId(taskId).forEach { dash ->
            if (!ResourceStorage.deleteFile(dash.getOutputFile(), dash.mimeType)) success = false
            if (!ResourceStorage.deleteFile(dash.getLegacyOutputFile(), dash.mimeType)) {
                success = false
            }
        }
        if (!success) return false

        mDownloadTaskDao.deleteTaskByTaskId(taskId)
        mDownloadDashDao.deleteTaskByTaskId(taskId)
        mDownloadResourceDao.deleteTaskByTaskId(taskId)
        return true
    }

    /**
     * 删除某个任务所有合成类型的资源
     */
    suspend fun deleteDownloadTaskMixedResource(taskId: Long): Boolean {
        var success = true
        mDownloadResourceDao.queryResourceByTaskIdAndResourceType(
            taskId, DownloadResourceType.MIXED
        ).forEach { resource ->
            if (ResourceStorage.delete(resource)) {
                mDownloadResourceDao.deleteById(resource.id)
            } else {
                success = false
            }
        }
        return success
    }

    /**
     * 删除某个任务的音频和视频源文件及其资源记录
     */
    suspend fun deleteDownloadTaskSourceResources(taskId: Long): Boolean {
        var success = true
        listOf(DownloadResourceType.VIDEO, DownloadResourceType.AUDIO).forEach { type ->
            mDownloadResourceDao.queryResourceByTaskIdAndResourceType(taskId, type)
                .forEach { resource ->
                if (ResourceStorage.delete(resource)) {
                    mDownloadResourceDao.deleteById(resource.id)
                } else {
                    success = false
                }
            }
        }
        return success
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
    suspend fun deleteResource(resource: DownloadResourceEntity): Boolean {
        if (!ResourceStorage.delete(resource)) return false
        mDownloadResourceDao.deleteById(resource.id)
        return true
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

    suspend fun queryResourcesForExport(taskId: Long) = run {
        mDownloadResourceDao.queryResourcesByTaskId(taskId)
    }
}
