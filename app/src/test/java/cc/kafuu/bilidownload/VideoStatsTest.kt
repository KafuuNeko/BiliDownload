package cc.kafuu.bilidownload

import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.common.model.bili.VideoStats
import cc.kafuu.bilidownload.common.network.model.BiliFavoriteMedia
import cc.kafuu.bilidownload.common.network.model.BiliHistoryItem
import cc.kafuu.bilidownload.common.network.model.BiliLikeVideoData
import cc.kafuu.bilidownload.common.network.model.BiliSearchManuscriptVideo
import cc.kafuu.bilidownload.common.network.model.BiliSearchVideoResultData
import cc.kafuu.bilidownload.common.network.model.BiliVideoData
import cc.kafuu.bilidownload.common.network.model.BiliWatchLaterItem
import cc.kafuu.bilidownload.common.utils.VideoCountFormatter
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Locale

/** 验证不同接口的计数含义、未知值和跨页面参数兼容性。 */
class VideoStatsTest {
    private val gson = Gson()
    private val videoJson = """{
        "bvid":"BV1xx411c7mD", "title":"示例视频", "author":"示例作者",
        "owner":{"name":"示例作者"}, "upper":{"name":"示例作者"},
        "description":"简介", "desc":"简介", "intro":"简介",
        "pic":"//example.invalid/cover.png", "cover":"https://example.invalid/cover.png",
        "duration":60, "length":"01:00"
    }"""

    @Test
    fun search_preservesLargeCountsZeroAndMissingLikes() {
        val json = gson.fromJson(videoJson, com.google.gson.JsonObject::class.java).apply {
            addProperty("duration", "1:00")
            addProperty("play", 3_000_000_000L)
            addProperty("favorites", 0)
            addProperty("video_review", -1)
        }
        val source = gson.fromJson(json, BiliSearchVideoResultData::class.java)
        val stats = BiliVideoModel.create(source).stats
        assertEquals(3_000_000_000L, stats.view)
        assertEquals(0L, stats.favorite)
        assertNull(stats.like)
        assertNull(stats.danmaku)
        assertFalse(stats.isComplete)
    }

    @Test
    fun favorites_mapsCollectButDoesNotTrustPlaceholderReply() {
        val json = withField("cnt_info", """{"play":100,"collect":20,"danmaku":2,"reply":0}""")
        val stats = BiliVideoModel.create(gson.fromJson(json, BiliFavoriteMedia::class.java)).stats
        assertEquals(VideoStats(view = 100, favorite = 20, danmaku = 2), stats)
    }

    @Test
    fun detailLikesAndWatchLater_shareStatMapping() {
        val json = withField("stat", """{"view":100,"like":0,"favorite":20,"reply":5,"danmaku":2}""")
        val expected = VideoStats(100, 0, 20, 2, 5)
        assertEquals(expected, BiliVideoModel.create(gson.fromJson(json, BiliVideoData::class.java)).stats)
        assertEquals(expected, BiliVideoModel.create(gson.fromJson(json, BiliLikeVideoData::class.java)).stats)
        assertEquals(expected, BiliVideoModel.create(gson.fromJson(json, BiliWatchLaterItem::class.java))?.stats)
    }

    @Test
    fun manuscript_usesCommentInsteadOfReview() {
        val json = withField("comment", "12").apply { addProperty("review", 999) }
        val stats = BiliVideoModel.create(gson.fromJson(json, BiliSearchManuscriptVideo::class.java)).stats
        assertEquals(VideoStats(reply = 12), stats)
    }

    @Test
    fun history_favoriteFlagIsNotFavoriteCountAndKeepsPreferredPart() {
        val json = withField("history", """{"bvid":"BV1xx411c7mD","cid":100}""").apply {
            addProperty("long_title", "")
            addProperty("author_name", "示例作者")
            addProperty("is_fav", 1)
        }
        val video = BiliVideoModel.create(gson.fromJson(json, BiliHistoryItem::class.java))!!
        assertEquals(VideoStats(), video.stats)
        assertEquals(100L, video.preferredCid)
    }

    @Test
    fun missingNestedStats_keepsUnknownInsteadOfZeros() {
        assertEquals(VideoStats(), BiliVideoModel.create(gson.fromJson(videoJson, BiliVideoData::class.java)).stats)
        assertEquals(VideoStats(), BiliVideoModel.create(gson.fromJson(videoJson, BiliLikeVideoData::class.java)).stats)
        assertEquals(VideoStats(), BiliVideoModel.create(gson.fromJson(videoJson, BiliWatchLaterItem::class.java))?.stats)
        assertEquals(VideoStats(), BiliVideoModel.create(gson.fromJson(videoJson, BiliFavoriteMedia::class.java)).stats)
    }

    @Test
    fun fallback_fillsOnlyMissingFields() {
        assertEquals(
            VideoStats(100, 0, 8),
            VideoStats(view = 100, like = 0).withFallback(VideoStats(50, 10, 8)),
        )
    }

    @Test
    fun videoWithStats_canBeSerializedForActivityArguments() {
        val original = BiliVideoModel("示例", "", "", 0, "作者", "BV1xx411c7mD", "00:01:00",
            preferredCid = 100, stats = VideoStats(100, 0, 20))
        val bytes = ByteArrayOutputStream().also { output ->
            ObjectOutputStream(output).use { it.writeObject(original) }
        }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as BiliVideoModel }
        assertEquals(original.stats, restored.stats)
        assertEquals(original.preferredCid, restored.preferredCid)
        assertEquals(original.bvid, restored.bvid)
    }

    @Test
    fun formatting_handlesThresholdsUnknownAndLongRange() {
        val examples = mapOf(
            0L to "0", 9999L to "9999", 10000L to "1万", 123456L to "12.3万",
            99999999L to "9999.9万", 100000000L to "1亿", 123456789L to "1.2亿",
            Long.MAX_VALUE to "92233720368.5亿",
        )
        examples.forEach { (input, expected) ->
            assertEquals(expected, VideoCountFormatter.format(input, Locale.CHINA))
        }
        assertEquals("", VideoCountFormatter.format(null))
        assertEquals("", VideoCountFormatter.format(-1))
        assertEquals("12.3K", VideoCountFormatter.format(12345, Locale.US))
        assertEquals("1M", VideoCountFormatter.format(1000000, Locale.US))
        assertEquals("3B", VideoCountFormatter.format(3000000000, Locale.US))
    }

    private fun withField(name: String, value: String) =
        gson.fromJson(videoJson, com.google.gson.JsonObject::class.java).apply {
            add(name, gson.fromJson(value, com.google.gson.JsonElement::class.java))
        }
}
