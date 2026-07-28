package cc.kafuu.bilidownload

import cc.kafuu.bilidownload.common.model.TaskStatus
import cc.kafuu.bilidownload.common.room.repository.DownloadRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadTaskStatusPolicyTest {
    @Test
    fun activeTaskStatuses_includeOnlyInProgressStates() {
        val activeCodes = DownloadRepository.getActiveTaskStatusCodes().toSet()

        assertTrue(TaskStatus.PREPARE.code in activeCodes)
        assertTrue(TaskStatus.DOWNLOADING.code in activeCodes)
        assertTrue(TaskStatus.SYNTHESIS.code in activeCodes)
        assertTrue(TaskStatus.PUBLISHING.code in activeCodes)
        assertFalse(TaskStatus.DOWNLOAD_FAILED.code in activeCodes)
        assertFalse(TaskStatus.SYNTHESIS_FAILED.code in activeCodes)
        assertFalse(TaskStatus.PUBLISH_FAILED.code in activeCodes)
        assertFalse(TaskStatus.COMPLETED.code in activeCodes)
    }
}
