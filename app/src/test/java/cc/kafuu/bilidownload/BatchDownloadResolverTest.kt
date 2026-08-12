package cc.kafuu.bilidownload

import cc.kafuu.bilidownload.common.download.BatchDownloadResolver
import cc.kafuu.bilidownload.common.network.model.BiliPlayDolby
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamDash
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
    fun selectCompatibleStream_doesNotChooseHigherQuality() {
        val result = BatchDownloadResolver.selectCompatibleStream(
            preferred = resource(id = 64, codecId = 7),
            available = listOf(
                resource(id = 80, codecId = 7),
                resource(id = 112, codecId = 12)
            )
        )

        assertNull(result)
    }

    @Test
    fun selectExactStreams_returnsResourcesFromCurrentPart() {
        val currentVideo = resource(id = 80, codecId = 7, urlSuffix = "current")
        val currentAudio = resource(id = 30250, codecId = 0, urlSuffix = "current")
        val result = BatchDownloadResolver.selectExactStreams(
            preferredVideo = resource(id = 80, codecId = 7, urlSuffix = "preferred"),
            preferredAudio = resource(id = 30250, codecId = 0, urlSuffix = "preferred"),
            dash = dash(
                video = listOf(currentVideo),
                audio = null,
                dolbyAudio = listOf(currentAudio)
            )
        )

        assertEquals(currentVideo, result?.videoStream)
        assertEquals(currentAudio, result?.audioStream)
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

    private fun resource(
        id: Long,
        codecId: Long,
        urlSuffix: String = "default"
    ) = BiliPlayStreamResource(
        id = id,
        baseUrl = "https://example.com/$id/$codecId/$urlSuffix",
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

    private fun dash(
        video: List<BiliPlayStreamResource>?,
        audio: List<BiliPlayStreamResource>?,
        dolbyAudio: List<BiliPlayStreamResource>? = null
    ) = BiliPlayStreamDash(
        duration = 0,
        minBufferTime = 0.0,
        video = video,
        audio = audio,
        supportFormats = emptyList(),
        dolby = BiliPlayDolby(type = 0, audio = dolbyAudio),
        flac = null
    )
}
