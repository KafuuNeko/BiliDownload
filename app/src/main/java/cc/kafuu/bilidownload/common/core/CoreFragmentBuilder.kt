package cc.kafuu.bilidownload.common.core

import android.os.Bundle
import androidx.fragment.app.Fragment
import java.io.Serializable
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

class CoreFragmentBuilder<T : Fragment>(private val fragmentClass: KClass<T>) {
    var arguments: Bundle = Bundle()

    fun build(): T = fragmentClass.primaryConstructor?.let {
        it.call().apply {
            arguments = this@CoreFragmentBuilder.arguments
        }
    } ?: throw IllegalArgumentException("Fragment class must have a primary constructor")

    inline fun <reified V> putArgument(key: String, value: V) {
        when (value) {
            is Int -> arguments.putInt(key, value)
            is Long -> arguments.putLong(key, value)
            is Boolean -> arguments.putBoolean(key, value)
            is String -> arguments.putString(key, value)
            is Array<*> -> if (value.isArrayOf<String>()) {
                arguments.putStringArray(key, value as Array<String>)
            }

            is Serializable -> arguments.putSerializable(key, value)
            is Bundle -> arguments.putBundle(key, value)
            else -> putExtraTypes(key, value)
        }
    }

    inline fun <reified V> putExtraTypes(key: String, value: V) {
        when (value) {
            is Float -> arguments.putFloat(key, value)
            is Double -> arguments.putDouble(key, value)
            is Short -> arguments.putShort(key, value)
            is Byte -> arguments.putByte(key, value)
            is Char -> arguments.putChar(key, value)
            is IntArray -> arguments.putIntArray(key, value)
            is LongArray -> arguments.putLongArray(key, value)
            is BooleanArray -> arguments.putBooleanArray(key, value)
            else -> throw IllegalArgumentException("Unsupported argument type")
        }
    }
}