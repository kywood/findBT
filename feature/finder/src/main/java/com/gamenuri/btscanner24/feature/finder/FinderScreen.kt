package com.gamenuri.btscanner24.feature.finder

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamenuri.btscanner24.core.ble.proximity.ProximityBand
import com.gamenuri.btscanner24.core.ble.proximity.ProximityTrend
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.core.model.DeviceLabel
import com.gamenuri.btscanner24.core.model.NameSource
import com.gamenuri.btscanner24.core.ui.text
import java.util.Locale
import kotlin.math.roundToInt

/** Entry point: owns the ViewModel, lifecycle, haptics and keep-screen-on. Visuals are in [FinderContent]. */
@Composable
fun FinderScreen(
    address: String,
    onClose: () -> Unit,
    viewModel: FinderViewModel = viewModel(key = "finder-$address", factory = FinderViewModel.factory(address)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Scan and read sensors only while the screen is visible.
    LifecycleStartEffect(viewModel) {
        viewModel.start()
        onStopOrDispose { viewModel.stop() }
    }

    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { playHaptic(context, it) }
    }

    // People walk around looking at the arrow; don't let the screen time out.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    FinderContent(
        state = state,
        actions = FinderActions(
            onClose = onClose,
            onFound = onClose,
            onToggleVibration = viewModel::toggleVibration,
            onSpinAgain = viewModel::spinAgain,
        ),
    )
}

data class FinderActions(
    val onClose: () -> Unit = {},
    val onFound: () -> Unit = {},
    val onToggleVibration: () -> Unit = {},
    val onSpinAgain: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinderContent(state: FinderUiState, actions: FinderActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(state.deviceLabel.text(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = actions.onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.finder_close))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StageText(state)
            Spacer(Modifier.height(24.dp))
            DirectionDial(state)
            Spacer(Modifier.height(24.dp))
            DistanceBlock(state)
            val canRespin = state.stage == FinderStage.WALK || state.stage == FinderStage.TURN_AROUND
            if (canRespin) {
                TextButton(onClick = actions.onSpinAgain) { Text(stringResource(R.string.finder_spin_again)) }
            }
            if (state.stage != FinderStage.WAITING && state.stage != FinderStage.SIGNAL_LOST) {
                // Names can be vague ("hiot0"); being able to confirm by touch matters more than the name (#46).
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.finder_confirm_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = state.vibrationOn,
                    onClick = actions.onToggleVibration,
                    label = { Text(stringResource(R.string.finder_vibration)) },
                    leadingIcon = if (state.vibrationOn) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                )
                Button(onClick = actions.onFound, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.finder_found))
                }
            }
            AdvancedSection(state.advanced)
        }
    }
}

@Composable
private fun StageText(state: FinderUiState) {
    val (title, body) = when (state.stage) {
        FinderStage.WAITING -> R.string.finder_waiting_title to R.string.finder_waiting_body
        FinderStage.SPIN -> R.string.finder_spin_title to R.string.finder_spin_body
        FinderStage.SPIN_AGAIN -> R.string.finder_spin_again_title to R.string.finder_spin_again_body
        FinderStage.WALK -> R.string.finder_walk_title to R.string.finder_walk_body
        FinderStage.LOCKED -> R.string.finder_locked_title to R.string.finder_locked_body
        FinderStage.TURN_AROUND -> R.string.finder_turn_around_title to R.string.finder_turn_around_body
        FinderStage.RIGHT_HERE -> R.string.finder_right_here_title to R.string.finder_right_here_body
        FinderStage.SIGNAL_LOST -> R.string.finder_signal_lost_title to R.string.finder_signal_lost_body
        FinderStage.NO_COMPASS -> R.string.finder_no_compass_title to R.string.finder_no_compass_body
    }
    if (state.arrowDegrees != null) {
        Text(
            text = stringResource(R.string.finder_direction_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(
        text = if (state.stage == FinderStage.SIGNAL_LOST) {
            stringResource(body, state.secondsSinceSeen)
        } else {
            stringResource(body)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun DistanceBlock(state: FinderUiState) {
    val meters = state.distanceMeters ?: return
    if (state.stage == FinderStage.SIGNAL_LOST || state.stage == FinderStage.WAITING) return

    if (state.stage != FinderStage.RIGHT_HERE) {
        state.band?.let { Text(stringResource(it.labelRes()), style = MaterialTheme.typography.titleMedium) }
    }
    Text(
        text = stringResource(R.string.finder_distance_value, formatMeters(meters)),
        style = MaterialTheme.typography.displaySmall,
    )
    Text(
        text = stringResource(R.string.finder_distance_label),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    // Hot-cold gauge: left is cold (far), right is hot (right here).
    LinearProgressIndicator(
        progress = { state.heat },
        modifier = Modifier.fillMaxWidth(0.8f),
    )
    Spacer(Modifier.height(8.dp))
    val trend = when (state.trend) {
        ProximityTrend.CLOSER -> R.string.finder_trend_closer
        ProximityTrend.FARTHER -> R.string.finder_trend_farther
        ProximityTrend.STEADY -> R.string.finder_trend_steady
    }
    Text(stringResource(trend), style = MaterialTheme.typography.labelLarge)
}

/** Raw numbers for testers; collapsed by default. */
@Composable
private fun AdvancedSection(info: AdvancedInfo) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.finder_advanced)) }
    if (expanded) {
        Text(
            text = stringResource(
                R.string.finder_advanced_values,
                info.address,
                info.rawRssi?.toString() ?: NO_VALUE,
                info.smoothedRssi?.let { "%.1f".format(Locale.ROOT, it) } ?: NO_VALUE,
                info.headingDegrees?.roundToInt()?.toString() ?: NO_VALUE,
                info.bearingDegrees?.roundToInt()?.toString() ?: NO_VALUE,
                info.addressSwitches,
                info.elapsedSeconds,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun ProximityBand.labelRes(): Int = when (this) {
    ProximityBand.RIGHT_HERE -> R.string.finder_band_right_here
    ProximityBand.VERY_CLOSE -> R.string.finder_band_very_close
    ProximityBand.NEAR -> R.string.finder_band_near
    ProximityBand.FAR -> R.string.finder_band_far
}

/** One decimal under 2 m ("~0.8 m"), whole metres above ("~6 m"): more precision would be false. */
private fun formatMeters(meters: Double): String =
    if (meters < 2.0) String.format(Locale.getDefault(), "%.1f", meters) else meters.roundToInt().toString()

private fun playHaptic(context: Context, event: FinderEvent) {
    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    if (vibrator?.hasVibrator() != true) return
    val effect = when (event) {
        FinderEvent.DIRECTION_FOUND, FinderEvent.DIRECTION_CHANGED ->
            VibrationEffect.createOneShot(HAPTIC_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        // Two short pulses for "locked".
        FinderEvent.LOCKED -> VibrationEffect.createWaveform(longArrayOf(0, HAPTIC_MS, 90, HAPTIC_MS), -1)
    }
    vibrator.vibrate(effect)
}

private const val HAPTIC_MS = 40L
private const val NO_VALUE = "–"

@Preview(name = "Walk")
@Composable
private fun FinderWalkPreview() {
    NearoTheme {
        FinderContent(
            state = FinderUiState(
                deviceLabel = PREVIEW_LABEL,
                stage = FinderStage.WALK,
                arrowDegrees = 30f,
                confidence = ArrowConfidence.MEDIUM,
                distanceMeters = 3.2,
                band = ProximityBand.NEAR,
                heat = 0.6f,
                trend = ProximityTrend.CLOSER,
            ),
            actions = FinderActions(),
        )
    }
}

@Preview(name = "Spin")
@Composable
private fun FinderSpinPreview() {
    NearoTheme {
        FinderContent(
            state = FinderUiState(deviceLabel = PREVIEW_LABEL, stage = FinderStage.SPIN, sweepProgress = 0.35f),
            actions = FinderActions(),
        )
    }
}

private val PREVIEW_LABEL = DeviceLabel.Named("Galaxy Buds2 Pro", NameSource.ADVERTISED, suffix = "3F2A")
