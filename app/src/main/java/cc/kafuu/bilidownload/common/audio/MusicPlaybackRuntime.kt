package cc.kafuu.bilidownload.common.audio

import cc.kafuu.bilidownload.common.audio.spectrum.RealtimeSpectrumAnalyzer

/** 保存播放服务与前台频谱界面共享的线程安全运行时组件。 */
object MusicPlaybackRuntime {
    val realtimeSpectrumAnalyzer = RealtimeSpectrumAnalyzer()
}
