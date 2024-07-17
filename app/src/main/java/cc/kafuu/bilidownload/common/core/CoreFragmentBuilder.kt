package cc.kafuu.bilidownload.common.core

import android.os.Bundle
import androidx.fragment.app.Fragment
import cc.kafuu.bilidownload.common.ext.putArgument
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

class CoreFragmentBuilder<T : Fragment>(private val fragmentClass: KClass<T>) {
    var arguments: Bundle = Bundle()

    fun build(): T = fragmentClass.primaryConstructor?.let {
        it.call().apply {
            arguments = this@CoreFragmentBuilder.arguments
        }
    } ?: throw IllegalArgumentException("Fragment class must have a primary constructor")

    inline fun <reified V> putArgument(key: String, value: V?): CoreFragmentBuilder<T> {
        arguments.putArgument(key, value)
        return this
    }
}