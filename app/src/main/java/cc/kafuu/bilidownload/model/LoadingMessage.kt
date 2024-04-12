package cc.kafuu.bilidownload.model

import android.graphics.drawable.Drawable

data class LoadingMessage(
    val visibility: Boolean = false,
    val icon: Drawable? = null,
    val message: String = ""
)
