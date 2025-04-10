package cc.kafuu.bilidownload.common.core.compose

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

abstract class CoreViewModelWithEvent<I : Any, S, E>(initStatus: S) :
    CoreCompViewModel<I, S>(initStatus) {
    // Single Event (Model -> View)
    private val mSingleEventChannel = Channel<E>(Channel.BUFFERED)
    val singleEventFlow = mSingleEventChannel.receiveAsFlow()

    protected fun dispatchingEvent(event: E) {
        viewModelScope.launch {
            mSingleEventChannel.send(event)
        }
    }
}

inline fun <I : Any, S, E> CoreCompActivity.attachEventListener(
    viewModel: CoreViewModelWithEvent<I, S, E>,
    crossinline onSingleEvent: (event: E) -> Unit
) = lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.singleEventFlow.collect { it?.run { onSingleEvent(this) } }
    }
}