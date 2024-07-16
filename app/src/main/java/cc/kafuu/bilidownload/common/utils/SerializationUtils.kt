package cc.kafuu.bilidownload.common.utils

import android.content.Intent
import android.os.Build
import android.os.Bundle
import java.io.Serializable

object SerializationUtils {
    inline fun <reified T : Serializable> Intent.getSerializableByClass(name: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSerializableExtra(name, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            when(val serializable = getSerializableExtra(name)) {
                is T -> serializable
                else -> null
            }
        }
    }

    inline fun <reified T : Serializable> Bundle.getSerializableByClass(name: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSerializable(name, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            when(val serializable = getSerializable(name)) {
                is T -> serializable
                else -> null
            }
        }
    }

}