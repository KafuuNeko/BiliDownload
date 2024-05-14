package cc.kafuu.bilidownload.common.model

import android.graphics.drawable.Drawable
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.utils.CommonLibs

class LoadingStatus private constructor(
    val statusCode: Int,
    val visibility: Boolean = false,
    val icon: Drawable? = null,
    val message: String? = "",
    val loadAnimationVisible: Boolean = false
) {
    companion object {
        const val CODE_WAIT = 0
        const val CODE_DONE = 1
        const val CODE_EMPTY = 2
        const val CODE_LOADING = 3
        const val CODE_ERROR = 4

        fun waitStatus(
            visibility: Boolean = false,
            icon: Drawable? = null,
            message: String = ""
        ) = LoadingStatus(
            statusCode = CODE_WAIT,
            visibility = visibility,
            icon = icon,
            message = message,
            loadAnimationVisible = false
        )

        fun doneStatus(
            visibility: Boolean = false,
            icon: Drawable? = null,
            message: String = ""
        ) = LoadingStatus(
            statusCode = CODE_DONE,
            visibility = visibility,
            icon = icon,
            message = message,
            loadAnimationVisible = false
        )

        fun emptyStatus(
            visibility: Boolean = true,
            icon: Drawable? = CommonLibs.getDrawable(R.drawable.ic_list_item_empty),
            message: String = ""
        ) = LoadingStatus(
            statusCode = CODE_EMPTY,
            visibility = visibility,
            icon = icon,
            message = message,
            loadAnimationVisible = false
        )

        fun loadingStatus(
            visibility: Boolean = true,
            message: String = ""
        ) = LoadingStatus(
            statusCode = CODE_LOADING,
            visibility = visibility,
            icon = null,
            message = message,
            loadAnimationVisible = true
        )

        fun errorStatus(
            visibility: Boolean = true,
            icon: Drawable? = CommonLibs.getDrawable(R.drawable.ic_error),
            message: String = ""
        ) = LoadingStatus(
            statusCode = CODE_ERROR,
            visibility = visibility,
            icon = icon,
            message = message,
            loadAnimationVisible = false
        )
    }
}
