package cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer

sealed class MediaPlayerUiEvent {
    data object Finish : MediaPlayerUiEvent()
    data class SetFullScreen(val isFullScreen: Boolean) : MediaPlayerUiEvent()
}
