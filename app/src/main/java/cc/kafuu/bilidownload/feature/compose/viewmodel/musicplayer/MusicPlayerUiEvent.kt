package cc.kafuu.bilidownload.feature.compose.viewmodel.musicplayer

sealed class MusicPlayerUiEvent {
    data object Finish : MusicPlayerUiEvent()
    data class OpenWithOtherPlayer(
        val filePath: String,
        val title: String,
        val mimeType: String
    ) : MusicPlayerUiEvent()
}
