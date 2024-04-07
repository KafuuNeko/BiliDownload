package cc.kafuu.bilidownload.common.utils

import java.math.BigInteger


object BvConvertUtils {
    private var mBvCode = "fZodR9XQDSUm21yCkr6zBqiveYah8bt4xsWpHnJE7jL5VG3guMTKNPAwcF"
    private var mBvIntegerMap: MutableMap<Char, Int> = hashMapOf<Char, Int>().apply {
        for (i in mBvCode.indices)
            put(mBvCode[i], i)
    }

    private val mBvBase = BigInteger.valueOf(58)
    private val mBvXor = BigInteger.valueOf(177451812L)
    private val mBvOffset = BigInteger.valueOf(8728348608L)
    private val mBvBitMap = intArrayOf(4, 2, 6, 1, 8, 9)

    /**
     * bv转为av
     *
     * @param bv Bv号，固定长度为12位(例如：BV1A4411N7Kb)
     *
     * @return 此Bv所对应的Av号，如果Bv号长度不等于12将返回null
     *
     */
    fun bv2av(bv: String): String? {
        if (bv.length != 12) {
            return null
        }
        var av = BigInteger.valueOf(0)
        for (index in mBvBitMap) {
            val inc = BigInteger.valueOf(
                mBvIntegerMap[bv[index + 2]]!!.toLong()
            )
            av = av.multiply(mBvBase).add(inc)
        }
        return av.subtract(mBvOffset).xor(mBvXor).toString()
    }

    /**
     * av转bv
     */
    fun av2bv(av: String): String {
        val bv = "BV1xx4x1x7xx".toCharArray()
        var x = BigInteger(av)
        x = x.xor(mBvXor).add(mBvOffset)
        for (i in mBvBitMap.indices.reversed()) {
            bv[mBvBitMap[i] + 2] = mBvCode[x.mod(mBvBase).toInt()]
            x = x.divide(mBvBase)
        }
        return String(bv)
    }

}
