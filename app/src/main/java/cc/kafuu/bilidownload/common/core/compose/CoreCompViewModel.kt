package cc.kafuu.bilidownload.common.core.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class UiIntentObserver(val cla: KClass<*>)

abstract class CoreCompViewModel<I : Any, S>(initStatus: S) : ViewModel() {
    // Ui State (Model -> View)
    protected val mUiStateFlow = MutableStateFlow(initStatus)
    val uiStateFlow = mUiStateFlow.asStateFlow()

    // Ui Intent (View -> Model)
    private val mUiIntentFlow = MutableSharedFlow<I>(extraBufferCapacity = 64)

    // UiIntent 于其观察者函数映射缓存表
    private val mUiIntentObserverMap: MutableMap<KClass<*>, List<KFunction<*>>> = mutableMapOf()

    /**
     * 此构造函数将扫描所有UiIntent观察者并缓存后启动UiIntent收集
     */
    init {
        doCacheUiIntentObservers()
        viewModelScope.launch { mUiIntentFlow.collect { onReceivedUiIntent(it) } }
    }

    /**
     * 扫描并缓存所有被UiIntentObserver注解的成员函数并存储
     */
    private fun doCacheUiIntentObservers() {
        val allFunctions = this::class.memberFunctions
        val observerMap: MutableMap<KClass<*>, MutableList<KFunction<*>>> = mutableMapOf()

        for (func in allFunctions) {
            val annotation = func.findAnnotation<UiIntentObserver>() ?: continue
            func.isAccessible = true
            observerMap.getOrPut(annotation.cla) { mutableListOf() }.add(func)
        }

        mUiIntentObserverMap.putAll(observerMap)
    }

    /**
     * 发送一个UI意图
     */
    fun emit(uiIntent: I) {
        mUiIntentFlow.tryEmit(uiIntent)
    }

    /**
     * 接收到ui意图并处理
     */
    private fun onReceivedUiIntent(uiIntent: I) {
        val clazz = uiIntent::class
        val observers = mUiIntentObserverMap[clazz] ?: return
        for (func in observers) {
            when (val size = func.parameters.size) {
                1 -> func.call(this)
                2 -> func.call(this, uiIntent)
                else -> throw IllegalArgumentException("Unsupported number of parameters: $size")
            }
        }
    }

    /**
     * 将Flow转换成StateFlow，并默认在viewModelScope中启动收集
     */
    protected fun <T> Flow<T>.stateInThis(): StateFlow<T?> {
        return this.stateIn(viewModelScope, SharingStarted.Lazily, null)
    }

    /**
     * 等待uiState变更为指定状态类型后返回
     */
    protected suspend inline fun <reified T : S> awaitUiStateOfType(): T {
        return mUiStateFlow.filterIsInstance<T>().first()
    }

    /**
     * 将某个UiState设置为当前状态
     */
    protected fun S.setup() {
        mUiStateFlow.value = this
    }
}