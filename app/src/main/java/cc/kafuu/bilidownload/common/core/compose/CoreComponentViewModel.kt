package cc.kafuu.bilidownload.common.core.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

abstract class CoreComponentViewModel<I, S, E> : ViewModel() {
    // Ui State (Model -> View)
    private val mUiStateFlow = MutableStateFlow<S?>(null)
    val uiState = mUiStateFlow.asStateFlow()

    // Ui Intent (View -> Model)
    private val mUiIntentFlow = MutableSharedFlow<I>()

    // Single Event (Model -> View)
    private val mSingleEventFlow = MutableSharedFlow<E>()
    val singleEventFlow = mSingleEventFlow.asSharedFlow()

    init {
        viewModelScope.launch {
            mUiIntentFlow.collect {
                onCollectedIntent(it)
            }
        }
    }

    fun emit(uiIntent: I) {
        viewModelScope.launch {
            mUiIntentFlow.emit(uiIntent)
        }
    }

    protected fun dispatchingEvent(event: E) {
        viewModelScope.launch {
            mSingleEventFlow.emit(event)
        }
    }

    protected abstract fun onCollectedIntent(uiIntent: I)

    protected fun <T> Flow<T>.stateInThis(): StateFlow<T?> {
        return stateIn(viewModelScope, SharingStarted.Lazily, null)
    }

    protected fun S.setup() {
        mUiStateFlow.value = this
    }
}