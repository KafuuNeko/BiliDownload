package cc.kafuu.bilidownload

import cc.kafuu.bilidownload.common.model.BatchQualityMismatchMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证批量画质不一致策略的持久化编码可以安全恢复。 */
class BatchQualityMismatchModeTest {
    @Test
    fun persistedCodesCanBeRestored() {
        assertEquals(
            BatchQualityMismatchMode.AUTO_FALLBACK,
            BatchQualityMismatchMode.fromCode(0)
        )
        assertEquals(BatchQualityMismatchMode.ASK, BatchQualityMismatchMode.fromCode(1))
        assertEquals(BatchQualityMismatchMode.SKIP, BatchQualityMismatchMode.fromCode(2))
    }

    @Test
    fun unknownCodeFallsBackToAutomaticDowngrade() {
        assertEquals(
            BatchQualityMismatchMode.AUTO_FALLBACK,
            BatchQualityMismatchMode.fromCode(Int.MAX_VALUE)
        )
    }
}
