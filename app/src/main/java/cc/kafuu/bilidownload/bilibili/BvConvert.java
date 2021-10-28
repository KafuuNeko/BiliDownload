package cc.kafuu.bilidownload.bilibili;

import androidx.annotation.NonNull;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * Bilibili BV-AV转换工具
 *
 * @author kafuu(kafuuneko@gmail.com)
 *
 * @version 1.0.0
 * */
public class BvConvert {

    final static String mBvCode;
    final static Map<Character, Integer> mBvIntegerMap;
    static {
        mBvCode = "fZodR9XQDSUm21yCkr6zBqiveYah8bt4xsWpHnJE7jL5VG3guMTKNPAwcF";
        mBvIntegerMap = new HashMap<>();
        for (int i = 0; i < mBvCode.length(); ++i) {
            mBvIntegerMap.put(mBvCode.charAt(i), i);
        }
    }

    final static BigInteger mBvBase = BigInteger.valueOf(58);
    final static BigInteger mBvXor = BigInteger.valueOf(177451812L);
    final static BigInteger mBvOffset = BigInteger.valueOf(8728348608L);

    final static int[] mBvBitMap = new int[] {4, 2, 6, 1, 8, 9};

    /**
     * bv转为av
     *
     * @param bv Bv号，固定长度为12位(例如：BV1A4411N7Kb)
     *
     * @return 此Bv所对应的Av号，如果Bv号长度不等于12将返回null
     *
     * */
    public static String bv2av(@NonNull String bv) {
        if (bv.length() != 12) {
            return null;
        }

        BigInteger av = BigInteger.valueOf(0);
        for (int index : mBvBitMap) {
            BigInteger inc = BigInteger.valueOf(mBvIntegerMap.get(bv.charAt(index + 2)));
            av = av.multiply(mBvBase).add(inc);
        }
        return av.subtract(mBvOffset).xor(mBvXor).toString();
    }

    /**
     * av转bv
     * */
    public static String av2bv(@NonNull String av) {

        char[] bv = "BV1xx4x1x7xx".toCharArray();

        BigInteger x = new BigInteger(av);
        x = x.xor(mBvXor).add(mBvOffset);

        for (int i = mBvBitMap.length - 1; i >=0 ; --i) {
            bv[mBvBitMap[i] + 2] = mBvCode.charAt(x.mod(mBvBase).intValue());
            x = x.divide(mBvBase);
        }

        return new String(bv);
    }
}
