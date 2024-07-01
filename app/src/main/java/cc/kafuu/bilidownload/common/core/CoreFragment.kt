package cc.kafuu.bilidownload.common.core

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import cc.kafuu.bilidownload.common.core.listener.IAvailableActivity
import cc.kafuu.bilidownload.common.core.listener.ViewActionListener

/**
 * 本应用中所有Fragment的基类，提供了常用的数据绑定和视图模型设置功能。
 * 这个类继承自 [Fragment]，并加入了对 [ViewDataBinding] 和 [CoreViewModel] 的支持。
 *
 * @param V 继承自 [ViewDataBinding] 的数据绑定类。
 * @param VM 继承自 [CoreViewModel] 的视图模型类。
 * @property vmClass 视图模型类的 Class 对象，用于实例化视图模型。
 * @property layoutId 布局资源ID，用于指定Fragment使用的布局。
 * @property viewModelId 视图模型的变量ID，用于在布局中绑定视图模型。
 */
abstract class CoreFragment<V : ViewDataBinding, VM : CoreViewModel>(
    private val vmClass: Class<VM>,
    @LayoutRes private val layoutId: Int,
    private val viewModelId: Int
) : Fragment(), IAvailableActivity {
    private lateinit var mViewActionListener: ViewActionListener

    protected lateinit var mViewDataBinding: V
    protected lateinit var mViewModel: VM

    /**
     * 子类需要实现这个函数来初始化视图组件。
     */
    abstract fun initViews()

    override fun requireAvailableActivity() = requireActivity()

    /**
     * 完成数据绑定和视图模型的初始化工作，以及其他初始化操作。
     *
     * @param inflater 布局填充器，用于从 XML 文件加载布局。
     * @param container 父视图的容器。
     * @param savedInstanceState savedInstanceState
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mViewDataBinding = DataBindingUtil.inflate(inflater, layoutId, container, false)
        mViewModel = ViewModelProvider(this)[vmClass]
        if (viewModelId != 0) {
            mViewDataBinding.setVariable(viewModelId, mViewModel)
        }
        mViewDataBinding.lifecycleOwner = this
        mViewActionListener = ViewActionListener(this)
        initViewAction()
        initViews()
        return mViewDataBinding.root
    }

    /**
     * 初始化用于监听视图Action的监听器
     * 目前负责处理Activity跳转和消息弹窗Action
     */
    private fun initViewAction() {
        if (mViewModel.viewActionLiveData.hasObservers()) {
            return
        }

        mViewModel.viewActionLiveData.observe(viewLifecycleOwner) {
            mViewActionListener.onViewAction(it)
        }
    }
}