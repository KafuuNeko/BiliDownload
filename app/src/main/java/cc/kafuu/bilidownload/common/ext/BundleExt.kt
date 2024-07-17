package cc.kafuu.bilidownload.common.ext

import android.os.Build
import android.os.Bundle
import java.io.Serializable

inline fun <reified V> Bundle.putArgument(key: String, value: V?): Bundle {
    when (value) {
        null -> putSerializable(key, null)
        is Double -> putDouble(key, value)
        is Float -> putFloat(key, value)
        is Long -> putLong(key, value)
        is Int -> putInt(key, value)
        is Short -> putShort(key, value)
        is Boolean -> putBoolean(key, value)
        is Byte -> putByte(key, value)
        is Char -> putChar(key, value)
        is String -> putString(key, value)
        is LongArray -> putLongArray(key, value)
        is IntArray -> putIntArray(key, value)
        is BooleanArray -> putBooleanArray(key, value)
        is Bundle -> putBundle(key, value)
        is Serializable -> putSerializable(key, value)
        else -> throw IllegalArgumentException("Unsupported argument type")
    }
    return this
}

fun Bundle.putArguments(vararg pairs: Pair<String, Any?>): Bundle {
    pairs.forEach {
        putArgument(it.first, it.second)
    }
    return this
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