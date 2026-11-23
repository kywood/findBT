package com.gamenuri.btscanner24.feature.finder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gamenuri.btscanner24.core.ble.BleDependencies
import com.gamenuri.btscanner24.core.ble.BleScanner
import com.gamenuri.btscanner24.core.ble.DeviceRepository
import com.gamenuri.btscanner24.core.ble.ScanEvent
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
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val engine: FinderEngine = run {
        val now = clock()
        // After process death the device may no longer be in memory; start from its address only.
        val target = repository.devices.value[address]
            ?: BleDevice(address = address, name = null, rssi = -100, firstSeen = now, lastSeen = now)
        FinderEngine(target, startMs = now, hasCompass = motion.source != MotionSensor.Source.NONE)
    }

    private val vibrationOn = MutableStateFlow(true)
    private val _uiState = MutableStateFlow(engine.state(clock(), vibrationOn.value))
    val uiState: StateFlow<FinderUiState> = _uiState.asStateFlow()

    private val _events = Channel<FinderEvent>(Channel.BUFFERED)

    /** Haptic cues; the screen plays them only when vibration is on. */
    val events: Flow<FinderEvent> = _events.receiveAsFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            // Subscribe to sightings before the scan starts so no early advertisement is missed.
            launch { repository.sightings.collect { engine.onSighting(it, clock()) } }
            // Parsing runs off the main thread; the engine itself stays on Main with the sensor collector.
            launch(Dispatchers.Default) {
                scanner.scan().collect { event ->
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
    }

    fun toggleVibration() {
        vibrationOn.value = !vibrationOn.value
        publish()
    }

    fun spinAgain() {
        engine.restartSweep()
        publish()
    }

    private fun publish() {
        _uiState.value = engine.state(clock(), vibrationOn.value)
    }

    override fun onCleared() {
        stop()
    }

    companion object {
        private const val UI_REFRESH_MS = 200L

        fun factory(address: String) = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val ble = (app as BleDependencies.Provider).bleDependencies
                FinderViewModel(address, ble.scanner, ble.repository, MotionSensor(app))
            }
        }
    }
}
