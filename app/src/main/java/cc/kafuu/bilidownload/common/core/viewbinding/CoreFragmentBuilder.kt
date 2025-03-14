package cc.kafuu.bilidownload.common.core.viewbinding

import android.os.Bundle
import androidx.fragment.app.Fragment
import cc.kafuu.bilidownload.common.ext.putArgument

abstract class CoreFragmentBuilder<out T : Fragment> {
    var arguments: Bundle = Bundle()

    fun build() = onMallocFragment().apply {
        onPreparationArguments()
        this.arguments = this@CoreFragmentBuilder.arguments
    }

    protected abstract fun onMallocFragment(): Fragment

    protected open fun onPreparationArguments() = Unit

    inline fun <reified V> putArgument(key: String, value: V?): CoreFragmentBuilder<T> {
        arguments.putArgument(key, value)
        return this
    }
}