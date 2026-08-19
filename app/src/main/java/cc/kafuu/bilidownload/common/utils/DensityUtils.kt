package cc.kafuu.bilidownload.common.utils

import android.content.Context
import android.util.TypedValue
import kotlin.math.roundToInt

/** 与屏幕密度相关的通用尺寸转换。 */
object DensityUtils {
    fun dpToPx(context: Context, dp: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        context.resources.displayMetrics,
    ).roundToInt()
}
