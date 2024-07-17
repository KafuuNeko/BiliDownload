package cc.kafuu.bilidownload.common.ext

import android.content.Intent
import android.os.Build
import java.io.Serializable

inline fun <reified T : Serializable> Intent.getSerializableByClass(name: String): T? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSerializableExtra(name, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        when (val serializable = getSerializableExtra(name)) {
            is T -> serializable
            else -> null
        }
    }
}