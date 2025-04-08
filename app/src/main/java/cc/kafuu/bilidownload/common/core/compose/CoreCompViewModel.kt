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
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.jvm.isAccessible

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class UiIntentObserver(val cla: KClass<*>)

abstract class CoreCompViewModel<I, S>(initStatus: S) : ViewModel() {
    // Ui State (Model -> View)
    protected val mUiStateFlow = MutableStateFlow<S>(initStatus)
    val uiStateFlow = mUiStateFlow.asStateFlow()

    // Ui Intent (View -> Model)
    private val mUiIntentFlow = MutableSharedFlow<I>()

    // 所有UI Intent观察者
    private val mUiIntentObserverList by lazy {
        this::class.memberFunctions.mapNotNull {
            val annotation = it.findAnnotation<UiIntentObserver>() ?: return@mapNotNull null
            it.isAccessible = true
            annotation.cla.starProjectedType to it
        }
    }

    // UiIntent于其观察者函数映射表
    private val mUiIntentObserverMap: MutableMap<KClass<*>, List<KFunction<*>>> = mutableMapOf()

    init {
        viewModelScope.launch { mUiIntentFlow.collect { onReceivedUiIntent(it) } }
    }

    fun emit(uiIntent: I) = viewModelScope.launch { mUiIntentFlow.emit(uiIntent) }

    private fun onReceivedUiIntent(uiIntent: I) {
        val clz = uiIntent?.let { it::class } ?: return
        if (!mUiIntentObserverMap.contains(clz)) {
            val targetClassType = clz.starProjectedType
            mUiIntentObserverMap[clz] = mUiIntentObserverList.filter {
                it.first.isSubtypeOf(targetClassType)
            }.map { it.second }
        }
        mUiIntentObserverMap[clz]?.forEach {
            when (val size = it.parameters.size) {
                1 -> it.call(this)
                2 -> it.call(this, uiIntent)
                else -> throw IllegalArgumentException("Unsupported number of parameters: $size")
            }
        }
    }

    protected fun <T> Flow<T>.stateInThis(): StateFlow<T?> {
        return stateIn(viewModelScope, SharingStarted.Lazily, null)
    }

    protected suspend inline fun <reified T> awaitUiStateOfType(): T {
        return mUiStateFlow.filterIsInstance<T>().first()
    }

    protected fun S.setup() {
        mUiStateFlow.value = this
    }
}