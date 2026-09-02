package cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer

sealed class MediaPlayerUiEvent {
    data object RequestPictureInPicture : MediaPlayerUiEvent()
    data class SetFullScreen(val isFullScreen: Boolean) : MediaPlayerUiEvent()
    data class OpenWithOtherPlayer(
        val filePath: String,
        val title: String,
        val mimeType: String,
        val contentUri: String?
    ) : MediaPlayerUiEvent()
}
