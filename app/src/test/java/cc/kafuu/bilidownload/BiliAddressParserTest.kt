package cc.kafuu.bilidownload

import cc.kafuu.bilidownload.common.utils.BiliAddressParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliAddressParserTest {
    @Test
    fun extractsSupportedContentIdsFromSharedText() {
        assertEquals(
            "BV1xx411c7mD",
            BiliAddressParser.extractSupportedAddress(
                "【测试视频】 https://www.bilibili.com/video/BV1xx411c7mD?share_source=copy_web"
            )
        )
        assertEquals("AV170001", BiliAddressParser.extractSupportedAddress("av170001"))
        assertEquals("EP12345", BiliAddressParser.extractSupportedAddress("Ep12345"))
        assertEquals("SS67890", BiliAddressParser.extractSupportedAddress("ss67890"))
    }

    @Test
    fun extractsB23ShortLinkWithoutTrailingSharedText() {
        assertEquals(
            "https://b23.tv/AbCd123",
            BiliAddressParser.extractSupportedAddress("视频标题 https://b23.tv/AbCd123 复制打开")
        )
        assertEquals(
            "https://b23.tv/AbCd123",
            BiliAddressParser.extractSupportedAddress("https://b23.tv/AbCd123。")
        )
    }

    @Test
    fun rejectsUnsupportedOrMalformedText() {
        assertNull(BiliAddressParser.extractSupportedAddress("普通搜索关键字"))
        assertNull(BiliAddressParser.extractSupportedAddress("https://example.com/BV123"))
        assertNull(BiliAddressParser.extractSupportedAddress("https://fakeb23.tv/AbCd123"))
        assertNull(BiliAddressParser.extractSupportedAddress("BV1xx411c7mDextra"))
        assertFalse(BiliAddressParser.containsSupportedAddress(null))
        assertTrue(BiliAddressParser.containsSupportedAddress("https://m.b23.tv/AbCd123"))
    }
}
