package cc.kafuu.bilidownload.model

import android.graphics.drawable.Drawable

data class LoadingStatus(
    val visibility: Boolean = false,
    val icon: Drawable? = null,
    val message: String? = "",
    val loadAnimationVisible: Boolean = false
)
