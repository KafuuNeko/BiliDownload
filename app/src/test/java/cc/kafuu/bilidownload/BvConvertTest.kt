package cc.kafuu.bilidownload

import cc.kafuu.bilidownload.common.utils.BvConvertUtils
import org.junit.Assert
import org.junit.Test

class BvConvertTest {
    @Test
    fun bv2av() {
        Assert.assertEquals(BvConvertUtils.bv2av("BV1e34y1V7EF"), "831778827")
    }
    @Test
    fun av2bv() {
        Assert.assertEquals(BvConvertUtils.av2bv("831778827"), "BV1e34y1V7EF")
    }
}