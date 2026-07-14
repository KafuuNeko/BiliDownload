package cc.kafuu.bilidownload

import cc.kafuu.bilidownload.common.model.DownloadPathMode
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadPathModeTest {
    @Test
    fun externalMediaCodeCanBeRestoredFromPreferences() {
        assertEquals(DownloadPathMode.EXTERNAL_MEDIA, DownloadPathMode.fromCode(2))
    }

    @Test
    fun unknownCodeFallsBackToInternalStorage() {
        assertEquals(DownloadPathMode.INTERNAL, DownloadPathMode.fromCode(Int.MAX_VALUE))
    }
}
