package cc.kafuu.bilidownload.common.core.dialog

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.clearFragmentResult
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.withStarted
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.ext.getSerializableByClass
import cc.kafuu.bilidownload.common.model.ResultWrapper
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Serializable
import kotlin.coroutines.resume


abstract class CoreBasicsDialog<V : ViewDataBinding, RS : Serializable>(
    @LayoutRes private val layoutId: Int
) : DialogFragment() {
    companion object {
        const val KEY_RESULT = "result"

        //界面重建时是否自动关闭dialog
        private const val ARG_DISMISS_ON_RECREATE = "dismiss_on_recreate"
    }

    private val mRequestKey = "#${javaClass.simpleName}_${layoutId}"

    protected lateinit var mViewDataBinding: V
    protected abstract fun initViews()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_Dialog)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let {
            val width = CommonLibs.requireContext().resources.displayMetrics.widthPixels * 0.9f
            it.setLayout(
                width.toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            it.setGravity(Gravity.CENTER)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mViewDataBinding = DataBindingUtil.inflate(inflater, layoutId, container, false)
        return mViewDataBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
    }

    protected fun dismissWithResult(result: RS? = null) {
        val bundle = Bundle().apply {
            result?.let {
                putSerializable(KEY_RESULT, it)
            }
        }
        setFragmentResult(mRequestKey, bundle)
        dismissAllowingStateLoss()
    }

    suspend fun showAndWaitResult(
        lifecycleOwner: LifecycleOwner,
        dialogTag: String = this.javaClass.simpleName,
        waitWhenInvisible: Boolean = false
    ): ResultWrapper<RS, Exception> {
        if (waitWhenInvisible) {
            lifecycleOwner.lifecycle.withStarted { }
        } else if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            return ResultWrapper.Error(IllegalStateException("Lifecycle state is not at least STARTED"))
        }
        val fragmentManager = when (lifecycleOwner) {
            is FragmentActivity -> lifecycleOwner.supportFragmentManager
            is Fragment -> lifecycleOwner.childFragmentManager
            else -> return ResultWrapper.Error(IllegalStateException("lifecycleOwner must be a FragmentActivity or Fragment"))
        }
        return doShowAndWaiting(fragmentManager, dialogTag)
    }

    private suspend fun doShowAndWaiting(
        fragmentManager: FragmentManager,
        dialogTag: String
    ) = suspendCancellableCoroutine { continuation ->
        arguments = (arguments ?: Bundle()).apply {
            putBoolean(ARG_DISMISS_ON_RECREATE, true)
        }
        show(fragmentManager, dialogTag)
        clearFragmentResult(mRequestKey)
        setFragmentResultListener(mRequestKey) { _, result ->
            if (!continuation.isActive) return@setFragmentResultListener
            if (result.isEmpty) {
                continuation.resume(ResultWrapper.Error(DialogCancelledException("Cancelled")))
                return@setFragmentResultListener
            }
            continuation.resume(result.getSerializableByClass<Serializable>(KEY_RESULT)?.let {
                try {
                    @Suppress("UNCHECKED_CAST")
                    ResultWrapper.Success(it as RS)
                } catch (e: ClassCastException) {
                    ResultWrapper.Error(e)
                }
            }
                ?: ResultWrapper.Error(IllegalStateException("Result is null or not of expected type")))
        }
    }

}

class DialogCancelledException(message: String) : Exception(message)