package com.gamenuri.btscanner24.feature.finder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gamenuri.btscanner24.core.ble.AlertSupport
import com.gamenuri.btscanner24.core.ble.BleDependencies
import com.gamenuri.btscanner24.core.ble.BleScanner
import com.gamenuri.btscanner24.core.ble.DeviceRepository
import com.gamenuri.btscanner24.core.ble.ImmediateAlert
import com.gamenuri.btscanner24.core.ble.RingResult
import com.gamenuri.btscanner24.core.ble.ScanEvent
import com.gamenuri.btscanner24.core.ble.scanContinuously
import com.gamenuri.btscanner24.core.model.BleDevice
import com.gamenuri.btscanner24.core.sensors.MotionSensor
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
    private val scanner: BleScanner,
    private val repository: DeviceRepository,
    private val motion: MotionSensor,
    private val alert: ImmediateAlert? = null,
    private val pairedAlias: String? = null,
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

    // "Play sound" (#28): support is learned once (advertised 0x1802 or a short GATT probe) and sticks.
    private var support: AlertSupport? = null
    private var probeJob: Job? = null
    private var probeAttempts = 0
    private var lastProbeMs = 0L
    private var soundJob: Job? = null
    private val sound = MutableStateFlow(SoundState.UNAVAILABLE)

    fun start() {
        if (job?.isActive == true) return
        engine.restartWaiting(clock())
        job = viewModelScope.launch {
            // Subscribe to sightings before the scan starts so no early advertisement is missed.
            launch { repository.sightings.collect { engine.onSighting(it, clock()) } }
            // Parsing runs off the main thread; the engine itself stays on Main with the sensor collector.
            launch(Dispatchers.Default) {
                // Coming from a list, this is often the sixth scan start within 30 s, which Android
                // accepts but never answers: the screen would wait for a signal forever (#117).
                scanner.scanContinuously().collect { event ->
                    if (event is ScanEvent.Result) repository.onScanResult(event.result)
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
        probeJob?.cancel()
        // Never leave a tracker ringing when the screen goes away.
        soundJob?.cancel()
    }

    /** Starts ringing, or stops it while it rings. */
    fun toggleSound() {
        val alert = alert ?: return
        if (soundJob?.isActive == true) {
            soundJob?.cancel()
            return
        }
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

    /** Decides whether to offer "Play sound" once the target is in range. */
    private fun checkSoundSupport(state: FinderUiState) {
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
        val state = engine.state(clock(), vibrationOn.value)
        checkSoundSupport(state)
        _uiState.value = state.copy(sound = sound.value)
    }

    override fun onCleared() {
        stop()
    }

    companion object {
        private const val UI_REFRESH_MS = 200L
        private const val SOUND_DURATION_MS = 20_000L
        private const val MAX_PROBES = 3
        private const val PROBE_RETRY_MS = 10_000L
        private const val NOTE_VISIBLE_MS = 5_000L

        fun factory(address: String) = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val ble = (app as BleDependencies.Provider).bleDependencies
                val alias = ble.bondedDevices.list().firstOrNull { it.address == address }?.name
                FinderViewModel(
                    address,
                    ble.scanner,
                    ble.repository,
                    MotionSensor(app),
                    alert = ImmediateAlert(app),
                    pairedAlias = alias,
                )
            }
        }
    }
}
