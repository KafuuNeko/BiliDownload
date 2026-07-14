package cc.kafuu.bilidownload

import cc.kafuu.bilidownload.common.model.DownloadPathMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证持久化的下载路径编码可以安全恢复。 */
class DownloadPathModeTest {
    /** 验证外部媒体库模式使用稳定编码 2。 */
    @Test
    fun externalMediaCodeCanBeRestoredFromPreferences() {
        assertEquals(DownloadPathMode.EXTERNAL_MEDIA, DownloadPathMode.fromCode(2))
    }

    /** 验证未知编码不会意外启用公共存储。 */
    @Test
    fun unknownCodeFallsBackToInternalStorage() {
        assertEquals(DownloadPathMode.INTERNAL, DownloadPathMode.fromCode(Int.MAX_VALUE))
    }
}
