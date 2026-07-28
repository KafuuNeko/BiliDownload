package cc.kafuu.bilidownload

import cc.kafuu.bilidownload.common.download.BatchDownloadResolver
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamResource
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamSegmentBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatchDownloadResolverTest {
    @Test
    fun selectCompatibleStream_prefersExactResource() {
        val preferred = resource(id = 80, codecId = 7)
        val exact = resource(id = 80, codecId = 7)
        val result = BatchDownloadResolver.selectCompatibleStream(
            preferred,
            listOf(resource(id = 64, codecId = 7), exact)
        )

        assertEquals(exact, result)
    }

    @Test
    fun selectCompatibleStream_fallsBackToNearestLowerQualityWithSameCodec() {
        val result = BatchDownloadResolver.selectCompatibleStream(
            preferred = resource(id = 80, codecId = 7),
            available = listOf(
                resource(id = 32, codecId = 7),
                resource(id = 64, codecId = 7),
                resource(id = 80, codecId = 12)
            )
        )

        assertEquals(64L, result?.id)
        assertEquals(7L, result?.codecId)
    }

    @Test
    fun selectCompatibleStream_usesSameQualityWhenPreferredCodecIsUnavailable() {
        val result = BatchDownloadResolver.selectCompatibleStream(
            preferred = resource(id = 80, codecId = 7),
            available = listOf(
                resource(id = 64, codecId = 12),
                resource(id = 80, codecId = 12)
            )
        )

        assertEquals(80L, result?.id)
    }

    @Test
    fun selectCompatibleStream_returnsNullForMissingSelectionOrResources() {
        assertNull(BatchDownloadResolver.selectCompatibleStream(null, emptyList()))
        assertNull(
            BatchDownloadResolver.selectCompatibleStream(
                resource(id = 80, codecId = 7),
                emptyList()
            )
        )
    }

    private fun resource(id: Long, codecId: Long) = BiliPlayStreamResource(
        id = id,
        baseUrl = "https://example.com/$id/$codecId",
        backupUrl = emptyList(),
        bandwidth = 0,
        mimeType = "video/mp4",
        codecs = "test",
        width = 0,
        height = 0,
        frameRate = "0",
        sar = "1:1",
        startWithSap = 0,
        segmentBase = BiliPlayStreamSegmentBase("", ""),
        codecId = codecId
    )
}
