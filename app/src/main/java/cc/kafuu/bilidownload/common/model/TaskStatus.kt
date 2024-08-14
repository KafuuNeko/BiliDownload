package cc.kafuu.bilidownload.common.model

import java.io.Serializable

enum class TaskStatus(val code: Int): Serializable {
    PREPARE(0),
    DOWNLOADING(1),
    DOWNLOAD_FAILED(-1),
    SYNTHESIS(2),
    SYNTHESIS_FAILED(-2),
    COMPLETED(3);
}