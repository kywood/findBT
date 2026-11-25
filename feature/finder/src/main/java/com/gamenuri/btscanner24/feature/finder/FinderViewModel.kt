package com.gamenuri.btscanner24.feature.finder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gamenuri.btscanner24.core.ble.AlertSupport
import com.gamenuri.btscanner24.core.ble.BleDependencies
import com.gamenuri.btscanner24.core.ble.ConnectedDevices
import com.gamenuri.btscanner24.core.ble.DeviceRepository
import com.gamenuri.btscanner24.core.ble.ImmediateAlert
import com.gamenuri.btscanner24.core.ble.MediaFindTone
import com.gamenuri.btscanner24.core.ble.RingResult
import com.gamenuri.btscanner24.core.ble.ScanSession
import com.gamenuri.btscanner24.core.ble.watch
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.model.PairedDevice
import com.gamenuri.btscanner24.core.sensors.MotionSensor
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Find mode for one device. Runs its own low-latency scan and the motion sensors only while the
 * screen is started; all logic lives in [FinderEngine], which is only touched on the main thread.
 */
class FinderViewModel(
    address: String,
    private val scan: ScanSession,
    private val repository: DeviceRepository,
    private val motion: MotionSensor,
    private val alert: ImmediateAlert? = null,
    private val pairedAlias: String? = null,
    /** Set together with [paired] when the target is the user's own paired device (#119). */
    private val connectedDevices: ConnectedDevices? = null,
    private val paired: PairedDevice? = null,
    /** Plays the find tone through the target when it is the phone's media output (#153). */
    private val tone: MediaFindTone? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val engine: FinderEngine = run {
        val now = clock()
        // After process death the device may no longer be in memory; start from its address only.
        val target = repository.devices.value[address]
            ?: BleDevice(address = address, name = null, rssi = -100, firstSeen = now, lastSeen = now)
        FinderEngine(
            target,
            startMs = now,
            pairedAlias = pairedAlias,
            hasCompass = motion.source != MotionSensor.Source.NONE,
        )
    }

    private val vibrationOn = MutableStateFlow(true)
    private val _uiState = MutableStateFlow(engine.state(clock(), vibrationOn.value))
    val uiState: StateFlow<FinderUiState> = _uiState.asStateFlow()

    private val _events = Channel<FinderEvent>(Channel.BUFFERED)

    /** Haptic cues; the screen plays them only when vibration is on. */
    val events: Flow<FinderEvent> = _events.receiveAsFlow()

    private var job: Job? = null

    /** This screen's hold on the shared scan (#177); null while it is not on screen. */
    private var scanHold: AutoCloseable? = null

    /** The target is connected to this phone right now; written by the connection watcher, read on Main. */
    @Volatile
    private var isConnected = false

    /** Media played now comes out of the target (connected earbuds): the sound goes that way (#153). */
    @Volatile
    private var viaPhone = false

    /** Connected for audio, but media goes somewhere else: the user has to switch the output first (#153). */
    @Volatile
    private var needsOutput = false

    // "Play sound" (#28): support is learned once (advertised 0x1802 or a short GATT probe) and sticks.
    private var support: AlertSupport? = null
    private var probeJob: Job? = null
    private var probeAttempts = 0
    private var lastProbeMs = 0L
    private var soundJob: Job? = null
    private var offeredViaPhone = false

    /** "Louder" taps during the tone; back to none for every new tone (#153). */
    private val louderSteps = AtomicInteger(0)
    private val sound = MutableStateFlow(SoundState.UNAVAILABLE)

    fun start() {
        if (job?.isActive == true) return
        engine.restartWaiting(clock())
        job = viewModelScope.launch {
            // Subscribe to sightings before asking for the scan so no early advertisement is missed.
            launch { repository.sightings.collect { engine.onSighting(it, clock()) } }
            // The list this screen was opened from usually still holds the shared scan, so it simply
            // carries on: no sixth start within 30 s, which Android accepts but never answers (#117).
            // If it had stopped on a failure it is started again.
            scanHold?.close()
            scanHold = scan.acquire()
            // The user's own device may be connected to the phone and silent: read the link instead (#119).
            if (connectedDevices != null && paired != null) {
                launch(Dispatchers.Default) {
                    connectedDevices.watch(repository, { listOf(paired) }) {
                        isConnected = it.isNotEmpty()
                        viaPhone = isConnected && tone?.isOutput(paired.address, connectedDevices) == true
                        needsOutput = tone != null && !viaPhone && paired.address in connectedDevices.audioAddresses()
                    }
                }
            }
            launch {
                motion.samples().collect { sample ->
                    engine.onMotion(sample, clock())?.let { if (vibrationOn.value) _events.trySend(it) }
                }
            }
            while (isActive) {
                publish()
                delay(UI_REFRESH_MS)
            }
        }
    }

    /** Called when the screen stops: never scan or read sensors in the background. */
    fun stop() {
        job?.cancel()
        job = null
        scanHold?.close()
        scanHold = null
        probeJob?.cancel()
        // Never leave a tracker ringing when the screen goes away.
        soundJob?.cancel()
    }

    /** Starts ringing, or stops it while it rings. */
    fun toggleSound() {
        if (soundJob?.isActive == true) {
            soundJob?.cancel()
            return
        }
        val tone = tone
        if (viaPhone && tone != null) {
            playTone(tone)
            return
        }
        val alert = alert ?: return
        val address = engine.state(clock(), vibrationOn.value).advanced.address
        probeJob?.cancel()
        sound.value = SoundState.CONNECTING
        publish()
        soundJob = viewModelScope.launch {
            var result = RingResult.FAILED
            try {
                result = alert.ring(address, SOUND_DURATION_MS) {
                    sound.value = SoundState.PLAYING
                    publish()
                }
            } finally {
                if (result == RingResult.NOT_SUPPORTED) support = AlertSupport.NOT_SUPPORTED
                sound.value = when (result) {
                    // Only reached after a tap, so say why the button goes away.
                    RingResult.NOT_SUPPORTED -> SoundState.NOT_SUPPORTED
                    // Stopped by the user or the screen, or the link dropped mid-ring: just offer it again.
                    RingResult.FAILED -> if (!isActive || sound.value == SoundState.PLAYING) {
                        SoundState.READY
                    } else {
                        SoundState.FAILED
                    }
                    RingResult.DONE -> SoundState.READY
                }
                publish()
                if (sound.value == SoundState.NOT_SUPPORTED) {
                    viewModelScope.launch {
                        delay(NOTE_VISIBLE_MS)
                        sound.value = SoundState.UNAVAILABLE
                        publish()
                    }
                }
            }
        }
    }

    /** The find tone as media audio through the connected target; the screen asked about ears first (#153). */
    private fun playTone(tone: MediaFindTone) {
        probeJob?.cancel()
        louderSteps.set(0)
        sound.value = SoundState.PLAYING
        publish()
        soundJob = viewModelScope.launch {
            try {
                tone.play(TONE_DURATION_MS) { louderSteps.get() }
            } finally {
                sound.value = SoundState.READY
                publish()
            }
        }
    }

    /** Decides whether to offer "Play sound" once the target is in range. */
    private fun checkSoundSupport(state: FinderUiState) {
        if (soundJob?.isActive == true) return
        if (viaPhone) {
            // Connected earbuds can always be made to sound, with or without a signal to measure.
            offeredViaPhone = true
            if (sound.value == SoundState.UNAVAILABLE) sound.value = SoundState.READY
            return
        }
        if (offeredViaPhone) {
            // No longer the media output (disconnected, or the user switched): back to what the device itself can do.
            offeredViaPhone = false
            sound.value = if (support == AlertSupport.SUPPORTED) SoundState.READY else SoundState.UNAVAILABLE
        }
        val alert = alert ?: return
        // Only while the screen is started (job), and never two GATT connections at once.
        if (job?.isActive != true || support != null) return
        if (probeJob?.isActive == true || soundJob?.isActive == true) return
        val noSignal = state.stage == FinderStage.WAITING ||
            state.stage == FinderStage.NOT_FOUND ||
            state.stage == FinderStage.SIGNAL_LOST
        if (noSignal) return
        if (!alert.hasPermission()) return
        val address = state.advanced.address
        if (repository.devices.value[address]?.serviceUuids16?.contains(ImmediateAlert.SERVICE_UUID_16) == true) {
            onSupport(AlertSupport.SUPPORTED)
            return
        }
        if (pairedAlias == null) {
            // Never connect to someone else's device on our own (#63): offer the button, and only a tap
            // connects. If it turns out it can't ring, the screen says so.
            onSupport(AlertSupport.SUPPORTED)
            return
        }
        // The user's own (paired) device: a short check so the button only shows when it can ring.
        val now = clock()
        if (probeAttempts >= MAX_PROBES || now - lastProbeMs < PROBE_RETRY_MS) return
        probeAttempts++
        lastProbeMs = now
        probeJob = viewModelScope.launch {
            val result = alert.probe(address)
            if (result != AlertSupport.UNREACHABLE) onSupport(result)
        }
    }

    private fun onSupport(result: AlertSupport) {
        support = result
        if (result == AlertSupport.SUPPORTED && sound.value == SoundState.UNAVAILABLE) sound.value = SoundState.READY
    }

    /** One step louder than the tone gets by itself; only the user's own tap does this (#153). */
    fun louder() {
        if (soundJob?.isActive == true) louderSteps.incrementAndGet()
    }

    fun toggleVibration() {
        vibrationOn.value = !vibrationOn.value
        publish()
    }

    /** "Try again" when no signal came or it was lost: a fresh scan and a fresh wait (#117). */
    fun retry() {
        stop()
        start()
        publish()
    }

    fun spinAgain() {
        engine.restartSweep()
        publish()
    }

    private fun publish() {
        val failure = scan.failure.value
        val state = engine.state(clock(), vibrationOn.value).let {
            // Without a scan there is nothing to point at: no arrow, and the reason instead of "no signal".
            if (failure == null) {
                it
            } else {
                it.copy(stage = FinderStage.NOT_FOUND, arrowDegrees = null, scanFailure = failure)
            }
        }
        checkSoundSupport(state)
        _uiState.value = state.copy(
            sound = sound.value,
            isConnected = isConnected,
            soundViaPhone = offeredViaPhone,
            // Only when the device has no sound of its own to offer.
            soundNeedsOutput = needsOutput && sound.value == SoundState.UNAVAILABLE,
        )
    }

    override fun onCleared() {
        stop()
    }

    companion object {
        private const val UI_REFRESH_MS = 200L
        private const val SOUND_DURATION_MS = 20_000L
        private const val TONE_DURATION_MS = 30_000L
        private const val MAX_PROBES = 3
        private const val PROBE_RETRY_MS = 10_000L
        private const val NOTE_VISIBLE_MS = 5_000L

        fun factory(address: String) = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val ble = (app as BleDependencies.Provider).bleDependencies
                val paired = ble.bondedDevices.list().firstOrNull { it.address == address }
                FinderViewModel(
                    address,
                    ble.scanSession,
                    ble.repository,
                    MotionSensor(app),
                    alert = ImmediateAlert(app),
                    pairedAlias = paired?.name,
                    connectedDevices = ble.connectedDevices,
                    paired = paired,
                    tone = ble.mediaFindTone,
                )
            }
        }
    }
}
