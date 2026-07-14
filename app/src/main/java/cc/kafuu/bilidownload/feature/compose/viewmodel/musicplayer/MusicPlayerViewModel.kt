package cc.kafuu.bilidownload.feature.compose.viewmodel.musicplayer

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.audio.MediaPlayerFactory
import cc.kafuu.bilidownload.common.audio.spectrum.AudioSpectrumAnalyzer
import cc.kafuu.bilidownload.common.audio.spectrum.MusicSpectrumBitmapRenderer
import cc.kafuu.bilidownload.common.audio.spectrum.MusicSpectrumBitmapTile
import cc.kafuu.bilidownload.common.audio.spectrum.RealtimeAudioRenderersFactory
import cc.kafuu.bilidownload.common.audio.spectrum.RealtimeSpectrumAnalyzer
import cc.kafuu.bilidownload.common.core.compose.CoreCompViewModelWithEvent
import cc.kafuu.bilidownload.common.core.compose.UiIntentObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

class MusicPlayerViewModel :
    CoreCompViewModelWithEvent<MusicPlayerUiIntent, MusicPlayerUiState, MusicPlayerUiEvent>(
        MusicPlayerUiState.None
    ) {

    private var mPlayer: ExoPlayer? = null
    private var mAppContext: Context? = null
    private var mProgressJob: Job? = null
    private var mSpectrumJob: Job? = null
    private var mSpectrogramTileJob: Job? = null
    private var mRealtimeSpectrumJob: Job? = null
    private var mSelectedPlaybackSpeed = 1.0f
    private val mSpectrumAnalyzer = AudioSpectrumAnalyzer()
    private val mRealtimeSpectrumAnalyzer = RealtimeSpectrumAnalyzer()
    private val mSpectrumBitmapRenderer = MusicSpectrumBitmapRenderer()
    private val mSpectrogramTileCache =
        object : LinkedHashMap<Long, MusicSpectrumBitmapTile>(MAX_TILE_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<Long, MusicSpectrumBitmapTile>?
            ): Boolean {
                return size > MAX_TILE_CACHE_SIZE
            }
        }

    private fun createPlayerListener() = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    val player = mPlayer ?: return
                    getOrNull<MusicPlayerUiState.Playing>()?.copy(
                        duration = player.duration.coerceAtLeast(0L),
                        isPlaying = player.isPlaying
                    )?.setup()
                    if (player.isPlaying) {
                        startProgressUpdate()
                        startRealtimeSpectrumUpdate()
                    }
                }

                Player.STATE_ENDED -> {
                    getOrNull<MusicPlayerUiState.Playing>()?.copy(
                        isPlaying = false,
                        currentPosition = getOrNull<MusicPlayerUiState.Playing>()?.duration ?: 0L
                    )?.setup()
                    stopProgressUpdate()
                    stopRealtimeSpectrumUpdate()
                }

                else -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            getOrNull<MusicPlayerUiState.Playing>()?.copy(isPlaying = isPlaying)?.setup()
            if (isPlaying) {
                startProgressUpdate()
                startRealtimeSpectrumUpdate()
            } else {
                stopProgressUpdate()
                stopRealtimeSpectrumUpdate()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            MusicPlayerUiState.Error(
                error.localizedMessage
                    ?: mAppContext?.getString(R.string.error_unknown)
                    .orEmpty()
            ).setup()
        }
    }

    @OptIn(UnstableApi::class)
    @UiIntentObserver(MusicPlayerUiIntent.Init::class)
    fun onInit(intent: MusicPlayerUiIntent.Init) {
        if (!isStateOf<MusicPlayerUiState.None>()) return
        val appContext = intent.context.applicationContext
        mAppContext = appContext
        mRealtimeSpectrumAnalyzer.clear()

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(appContext, extractorsFactory)

        val player = MediaPlayerFactory.configure(
            ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setRenderersFactory(
                RealtimeAudioRenderersFactory(
                    context = appContext,
                    analyzer = mRealtimeSpectrumAnalyzer
                )
            )
        )
            .build()
            .also {
                mPlayer = it
                it.addListener(createPlayerListener())
            }

        MusicPlayerUiState.Playing(
            title = intent.title,
            filePath = intent.filePath,
            contentUri = intent.contentUri,
            mimeType = intent.mimeType,
            player = player
        ).setup()

        val uri = intent.contentUri?.let(Uri::parse) ?: Uri.fromFile(File(intent.filePath))
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = true

        startSpectrumAnalysis(intent.context.applicationContext, intent.filePath)
    }

    @UiIntentObserver(MusicPlayerUiIntent.TogglePlayPause::class)
    fun onTogglePlayPause() {
        val player = mPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    @UiIntentObserver(MusicPlayerUiIntent.SetPlaybackSpeed::class)
    fun onSetPlaybackSpeed(intent: MusicPlayerUiIntent.SetPlaybackSpeed) {
        val speed = intent.speed
        mSelectedPlaybackSpeed = speed
        mPlayer?.setPlaybackSpeed(speed)
        getOrNull<MusicPlayerUiState.Playing>()?.copy(
            playbackSpeed = speed,
            selectedPlaybackSpeed = speed
        )?.setup()
    }

    @UiIntentObserver(MusicPlayerUiIntent.SetSpectrumMode::class)
    fun onSetSpectrumMode(intent: MusicPlayerUiIntent.SetSpectrumMode) {
        getOrNull<MusicPlayerUiState.Playing>()?.copy(spectrumMode = intent.mode)?.setup()
        if (mPlayer?.isPlaying == true) {
            startRealtimeSpectrumUpdate()
        }
    }

    @UiIntentObserver(MusicPlayerUiIntent.SetSpectrogramWindow::class)
    fun onSetSpectrogramWindow(intent: MusicPlayerUiIntent.SetSpectrogramWindow) {
        val state = getOrNull<MusicPlayerUiState.Playing>() ?: return
        val nextState = state.copy(
            spectrogramWindowStartMs = clampSpectrogramWindowStart(intent.startMs, state.duration),
            isSpectrogramFollowingPlayback = intent.followPlayback
        )
        nextState.setup()
        requestSpectrogramTiles(nextState)
    }

    @UiIntentObserver(MusicPlayerUiIntent.SeekTo::class)
    fun onSeekTo(intent: MusicPlayerUiIntent.SeekTo) {
        val state = getOrNull<MusicPlayerUiState.Playing>() ?: return
        val nextState = state.copy(
            currentPosition = intent.position,
            realtimeSpectrumFrame = state.realtimeSpectrumFrame,
            spectrogramWindowStartMs = if (intent.syncSpectrogramWindow) {
                followSpectrogramWindowStart(
                    positionMs = intent.position,
                    durationMs = state.duration,
                    currentStartMs = state.spectrogramWindowStartMs
                )
            } else {
                state.spectrogramWindowStartMs
            },
            isSpectrogramFollowingPlayback = if (intent.syncSpectrogramWindow) {
                true
            } else {
                state.isSpectrogramFollowingPlayback
            }
        )
        nextState.setup()
        requestSpectrogramTiles(nextState)
    }

    @UiIntentObserver(MusicPlayerUiIntent.SeekBarDragStart::class)
    fun onSeekBarDragStart() {
        getOrNull<MusicPlayerUiState.Playing>()?.copy(isSeekBarDragging = true)?.setup()
    }

    @UiIntentObserver(MusicPlayerUiIntent.SeekBarDragEnd::class)
    fun onSeekBarDragEnd(intent: MusicPlayerUiIntent.SeekBarDragEnd) {
        mPlayer?.seekTo(intent.position)
        mRealtimeSpectrumAnalyzer.clear()
        val state = getOrNull<MusicPlayerUiState.Playing>() ?: return
        val nextState = state.copy(
            currentPosition = intent.position,
            isSeekBarDragging = false,
            spectrogramWindowStartMs = if (intent.syncSpectrogramWindow) {
                followSpectrogramWindowStart(
                    positionMs = intent.position,
                    durationMs = state.duration,
                    currentStartMs = state.spectrogramWindowStartMs
                )
            } else {
                state.spectrogramWindowStartMs
            },
            isSpectrogramFollowingPlayback = if (intent.syncSpectrogramWindow) {
                true
            } else {
                state.isSpectrogramFollowingPlayback
            }
        )
        nextState.setup()
        requestSpectrogramTiles(nextState)
    }

    @UiIntentObserver(MusicPlayerUiIntent.OpenWithOtherPlayer::class)
    fun onOpenWithOtherPlayer() = viewModelScope.launch {
        val state = getOrNull<MusicPlayerUiState.Playing>() ?: return@launch
        MusicPlayerUiEvent.OpenWithOtherPlayer(
            filePath = state.filePath,
            title = state.title,
            mimeType = state.mimeType,
            contentUri = state.contentUri
        ).send()
    }

    @UiIntentObserver(MusicPlayerUiIntent.GoBack::class)
    fun onGoBack() = viewModelScope.launch {
        MusicPlayerUiEvent.Finish.send()
    }

    @UiIntentObserver(MusicPlayerUiIntent.UpdateProgress::class)
    fun onUpdateProgress(intent: MusicPlayerUiIntent.UpdateProgress) {
        val state = getOrNull<MusicPlayerUiState.Playing>() ?: return
        if (!state.isSeekBarDragging) {
            val nextState = state.copy(
                currentPosition = intent.position,
                duration = intent.duration,
                spectrogramWindowStartMs = if (state.isSpectrogramFollowingPlayback) {
                    followSpectrogramWindowStart(
                        positionMs = intent.position,
                        durationMs = intent.duration,
                        currentStartMs = state.spectrogramWindowStartMs
                    )
                } else {
                    clampSpectrogramWindowStart(state.spectrogramWindowStartMs, intent.duration)
                }
            )
            nextState.setup()
            if (nextState.spectrogramWindowStartMs != state.spectrogramWindowStartMs) {
                requestSpectrogramTiles(nextState)
            }
        }
    }

    private fun startSpectrumAnalysis(context: Context, filePath: String) {
        mSpectrumJob?.cancel()
        mSpectrumJob = viewModelScope.launch {
            getOrNull<MusicPlayerUiState.Playing>()?.copy(
                isAnalyzingSpectrum = true,
                spectrumProgress = 0f,
                spectrumError = null
            )?.setup()

            try {
                val spectrum = withContext(Dispatchers.Default) {
                    mSpectrumAnalyzer.analyze(filePath) { progress ->
                        viewModelScope.launch {
                            getOrNull<MusicPlayerUiState.Playing>()?.copy(
                                spectrumProgress = progress,
                                isAnalyzingSpectrum = progress < 1f
                            )?.setup()
                        }
                    }
                }

                val spectrumReadyState = getOrNull<MusicPlayerUiState.Playing>()?.copy(
                    spectrumData = spectrum,
                    isAnalyzingSpectrum = true,
                    spectrumProgress = 1f,
                    spectrumError = null
                ) ?: return@launch
                spectrumReadyState.setup()
                requestSpectrogramTiles(spectrumReadyState)

                val overviewBitmap = withContext(Dispatchers.Default) {
                    mSpectrumBitmapRenderer.renderOverview(spectrum)
                }

                val nextState = getOrNull<MusicPlayerUiState.Playing>()?.copy(
                    spectrumOverviewBitmap = overviewBitmap,
                    isAnalyzingSpectrum = false,
                    spectrumProgress = 1f,
                    spectrumError = null
                ) ?: return@launch
                nextState.setup()
                requestSpectrogramTiles(nextState)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                getOrNull<MusicPlayerUiState.Playing>()?.copy(
                    isAnalyzingSpectrum = false,
                    spectrumError = exception.localizedMessage
                        ?: context.getString(R.string.music_spectrum_analysis_failed)
                )?.setup()
            }
        }
    }

    private fun startProgressUpdate() {
        stopProgressUpdate()
        mProgressJob = viewModelScope.launch {
            while (true) {
                val player = mPlayer ?: break
                emit(
                    MusicPlayerUiIntent.UpdateProgress(
                        position = player.currentPosition,
                        duration = player.duration.coerceAtLeast(0)
                    )
                )
                delay(PROGRESS_UPDATE_INTERVAL_MS.milliseconds)
            }
        }
    }

    private fun stopProgressUpdate() {
        mProgressJob?.cancel()
        mProgressJob = null
    }

    private fun startRealtimeSpectrumUpdate() {
        if (mRealtimeSpectrumJob?.isActive == true) return
        mRealtimeSpectrumJob = viewModelScope.launch {
            while (true) {
                val player = mPlayer ?: break
                val state = getOrNull<MusicPlayerUiState.Playing>() ?: break
                val mode = state.spectrumMode
                if (mode == MusicSpectrumMode.Spectrogram) {
                    delay(REALTIME_FRAME_INTERVAL_MS.milliseconds)
                    continue
                }
                val position = player.currentPosition
                val frame = withContext(Dispatchers.Default) {
                    mRealtimeSpectrumAnalyzer.createFrame(
                        positionMs = position,
                        includeSpectrum = mode != MusicSpectrumMode.Waveform,
                        includeWaveform = mode == MusicSpectrumMode.Waveform
                    )
                }
                getOrNull<MusicPlayerUiState.Playing>()?.copy(
                    realtimeSpectrumFrame = frame
                )?.setup()
                delay(REALTIME_FRAME_INTERVAL_MS.milliseconds)
            }
        }
    }

    private fun stopRealtimeSpectrumUpdate() {
        mRealtimeSpectrumJob?.cancel()
        mRealtimeSpectrumJob = null
    }

    private fun requestSpectrogramTiles(state: MusicPlayerUiState.Playing) {
        val spectrum = state.spectrumData ?: return
        val tileStarts = mSpectrumBitmapRenderer.requiredTileStarts(
            windowStartMs = state.spectrogramWindowStartMs,
            windowDurationMs = spectrogramWindowDuration(state.duration),
            durationMs = state.duration
        )

        if (state.spectrogramTiles.map { it.startMs } == tileStarts) return

        val cachedTiles = tileStarts.mapNotNull { mSpectrogramTileCache[it] }
        if (cachedTiles.size == tileStarts.size) {
            state.copy(spectrogramTiles = cachedTiles.sortedBy { it.startMs }).setup()
            return
        }

        mSpectrogramTileJob?.cancel()
        val missingStarts = tileStarts.filterNot { mSpectrogramTileCache.containsKey(it) }
        mSpectrogramTileJob = viewModelScope.launch {
            val renderedTiles = withContext(Dispatchers.Default) {
                missingStarts.map { startMs ->
                    mSpectrumBitmapRenderer.renderTile(spectrum, startMs)
                }
            }

            renderedTiles.forEach { tile -> mSpectrogramTileCache[tile.startMs] = tile }
            val currentState = getOrNull<MusicPlayerUiState.Playing>() ?: return@launch
            if (currentState.spectrumData !== spectrum) return@launch

            val currentTileStarts = mSpectrumBitmapRenderer.requiredTileStarts(
                windowStartMs = currentState.spectrogramWindowStartMs,
                windowDurationMs = spectrogramWindowDuration(currentState.duration),
                durationMs = currentState.duration
            )
            val tiles = currentTileStarts
                .mapNotNull { mSpectrogramTileCache[it] }
                .sortedBy { it.startMs }
            if (tiles.isNotEmpty()) {
                currentState.copy(spectrogramTiles = tiles).setup()
            }
        }
    }

    private fun followSpectrogramWindowStart(
        positionMs: Long,
        durationMs: Long,
        currentStartMs: Long
    ): Long {
        val windowDuration = spectrogramWindowDuration(durationMs)
        if (windowDuration !in 1..<durationMs) return 0L

        val currentEndMs = currentStartMs + windowDuration
        val nextStartMs = when {
            positionMs < currentStartMs -> positionMs - windowDuration / 3L
            positionMs > currentEndMs -> positionMs - windowDuration * 2L / 3L
            else -> currentStartMs
        }
        return clampSpectrogramWindowStart(nextStartMs, durationMs)
    }

    private fun clampSpectrogramWindowStart(startMs: Long, durationMs: Long): Long {
        val maxStart = (durationMs - spectrogramWindowDuration(durationMs)).coerceAtLeast(0L)
        return startMs.coerceIn(0L, maxStart)
    }

    private fun spectrogramWindowDuration(durationMs: Long): Long {
        if (durationMs <= 0L) return SPECTROGRAM_WINDOW_DURATION_MS
        return durationMs.coerceAtMost(SPECTROGRAM_WINDOW_DURATION_MS)
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressUpdate()
        mSpectrumJob?.cancel()
        mSpectrumJob = null
        mSpectrogramTileJob?.cancel()
        mSpectrogramTileJob = null
        stopRealtimeSpectrumUpdate()
        mSpectrogramTileCache.clear()
        mPlayer?.release()
        mPlayer = null
        mAppContext = null
    }

    companion object {
        private const val PROGRESS_UPDATE_INTERVAL_MS = 250L
        private const val REALTIME_FRAME_INTERVAL_MS = 40L
        private const val SPECTROGRAM_WINDOW_DURATION_MS = 60_000L
        private const val MAX_TILE_CACHE_SIZE = 8
    }
}
