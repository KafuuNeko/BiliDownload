package cc.kafuu.bilidownload.common.model

import java.io.Serializable

/**
 * 下载任务的持久化生命周期状态。
 *
 * [code] 已写入数据库，新增状态应使用新值，不能改变既有状态的编码。
 */
enum class TaskStatus(val code: Int): Serializable {
    PREPARE(0),
    DOWNLOADING(1),
    DOWNLOAD_FAILED(-1),
    SYNTHESIS(2),
    SYNTHESIS_FAILED(-2),
    COMPLETED(3),

    /** 下载已完成，正在把工作文件发布到公共媒体库。 */
    PUBLISHING(4),

    /** 发布失败，保留工作文件并允许从发布阶段重试。 */
    PUBLISH_FAILED(-3);
}
