package cc.kafuu.bilidownload.feature.compose.viewmodel.downloadsource

sealed class DownloadSourceUiEvent
// 每次每段用户或者助手消息都应该再补充一个system消息，表明消息发送的时间（格式化后的）