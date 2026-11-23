package com.gamenuri.btscanner24.feature.finder

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.gamenuri.btscanner24.core.designsystem.theme.FindStateColors
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTextStyles
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.core.model.DeviceLabel
import com.gamenuri.btscanner24.core.model.NameSource
import com.gamenuri.btscanner24.core.ui.distanceText
import com.gamenuri.btscanner24.core.ui.text
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Entry point: owns the ViewModel, lifecycle, haptics and keep-screen-on. Visuals are in [FinderContent]. */
@Composable
fun FinderScreen(
    address: String,
    onClose: () -> Unit,
    viewModel: FinderViewModel = viewModel(key = "finder-$address", factory = FinderViewModel.factory(address)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // "I found it" swaps in the Found screen; leaving this branch stops the scan, sensors and haptics.
    var foundAfterSeconds by rememberSaveable { mutableStateOf<Int?>(null) }
    foundAfterSeconds?.let { seconds ->
        FoundContent(label = state.deviceLabel, elapsedSeconds = seconds, onDone = onClose)
        return
    }

    // Scan and read sensors only while the screen is visible.
    LifecycleStartEffect(viewModel) {
        viewModel.start()
        onStopOrDispose { viewModel.stop() }
    }

    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { playHaptic(context, it) }
    }
    // Proximity rhythm (design v3): faster as you get closer, one long buzz when it is right here.
    val rhythm = rhythmFor(state)
    LaunchedEffect(rhythm, state.vibrationOn) {
        if (!state.vibrationOn) return@LaunchedEffect
        playRhythm(context, rhythm)
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
            onFound = { foundAfterSeconds = state.advanced.elapsedSeconds },
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
    val palette = findColorsFor(state)
    val background by animateColorAsState(palette.background, tween(COLOR_FADE_MS), label = "findBackground")
    val content by animateColorAsState(palette.content, tween(COLOR_FADE_MS), label = "findContent")
    Scaffold(
        modifier = modifier,
        containerColor = background,
        contentColor = content,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = content,
                    navigationIconContentColor = content,
                    actionIconContentColor = content,
                ),
                title = { Text(state.deviceLabel.text(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = actions.onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.finder_close))
                    }
                },
                // "⋯" opens the raw numbers (design v3: top-right is details/advanced), so nothing sits
                // under the navigation bar at the bottom of a long screen.
                actions = { AdvancedMenu(state.advanced, tint = content) },
            )
        },
        // The action row is pinned above the navigation bar and never scrolls away (design review #55).
        bottomBar = { ActionRow(state, actions, content, background) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StageText(state)
            // The dial takes what is left and shrinks on short screens (Flip3 cover, small phones).
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                DirectionDial(state, Modifier.heightIn(min = DIAL_MIN))
            }
            DistanceBlock(state)
            val canRespin = state.stage == FinderStage.WALK || state.stage == FinderStage.TURN_AROUND
            if (canRespin) {
                TextButton(onClick = actions.onSpinAgain) {
                    Text(stringResource(R.string.finder_spin_again), color = content)
                }
            }
            if (state.showConfirmHint) {
                // Names can be vague ("hiot0"); confirming by touch matters more than the name (#46).
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.finder_confirm_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = MUTED_ALPHA),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(state: FinderUiState, actions: FinderActions, content: Color, background: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            colors = FilterChipDefaults.filterChipColors(
                labelColor = content,
                iconColor = content,
                selectedContainerColor = content.copy(alpha = 0.18f),
                selectedLabelColor = content,
                selectedLeadingIconColor = content,
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = state.vibrationOn,
                borderColor = content.copy(alpha = 0.5f),
            ),
            selected = state.vibrationOn,
            onClick = actions.onToggleVibration,
            label = { Text(stringResource(R.string.finder_vibration)) },
            leadingIcon = if (state.vibrationOn) {
                { Icon(Icons.Filled.Check, contentDescription = null) }
            } else {
                null
            },
        )
        // Inverted colours keep the main button readable on every state background.
        Button(
            onClick = actions.onFound,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = content, contentColor = background),
        ) {
            Text(stringResource(R.string.finder_found))
        }
    }
}

@Composable
private fun StageText(state: FinderUiState) {
    val (title, body) = when (state.stage) {
        FinderStage.WAITING -> R.string.finder_waiting_title to R.string.finder_waiting_body
        FinderStage.SPIN -> R.string.finder_spin_title to R.string.finder_spin_body
        FinderStage.SPIN_AGAIN -> R.string.finder_spin_again_title to R.string.finder_spin_again_body
        FinderStage.WALK -> if (state.band == ProximityBand.VERY_CLOSE || state.band == ProximityBand.RIGHT_HERE) {
            // Under 1.5 m the arrow is less reliable; looking around beats following it (v3.3).
            R.string.finder_very_close_title to R.string.finder_very_close_body
        } else {
            R.string.finder_walk_title to R.string.finder_walk_body
        }
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
            color = LocalContentColor.current.copy(alpha = MUTED_ALPHA),
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
        color = LocalContentColor.current.copy(alpha = MUTED_ALPHA),
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
    Text(text = distanceText(meters), style = NearoTextStyles.findDistance, maxLines = 1)
    Text(
        text = stringResource(R.string.finder_distance_label),
        style = MaterialTheme.typography.labelMedium,
        color = LocalContentColor.current.copy(alpha = MUTED_ALPHA),
    )
    Spacer(Modifier.height(16.dp))
    HeatGauge(state.heat, Modifier.fillMaxWidth(0.8f))
    // No "Keep going" while the user is asked to spin in place.
    val spinning = state.stage == FinderStage.SPIN || state.stage == FinderStage.SPIN_AGAIN
    if (state.stage != FinderStage.RIGHT_HERE && !spinning) {
        Spacer(Modifier.height(12.dp))
        TrendChip(state.trend)
    }
}

/**
 * Hot-cold gauge: cold (far) on the left, hot (right here) on the right. The ❄/🔥 ends and the dot's
 * position carry the meaning, so it does not depend on colour.
 */
@Composable
private fun HeatGauge(heat: Float, modifier: Modifier = Modifier) {
    val content = LocalContentColor.current
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.finder_gauge_cold), style = MaterialTheme.typography.titleMedium)
        BoxWithConstraints(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(GAUGE_DOT),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(GAUGE_TRACK)
                    .background(content.copy(alpha = 0.3f), RoundedCornerShape(GAUGE_TRACK)),
            )
            Box(
                Modifier
                    .offset(x = (maxWidth - GAUGE_DOT) * heat.coerceIn(0f, 1f))
                    .size(GAUGE_DOT)
                    .background(content, CircleShape),
            )
        }
        Text(stringResource(R.string.finder_gauge_hot), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun TrendChip(trend: ProximityTrend) {
    val content = LocalContentColor.current
    val text = when (trend) {
        ProximityTrend.CLOSER -> R.string.finder_trend_closer
        ProximityTrend.FARTHER -> R.string.finder_trend_farther
        ProximityTrend.STEADY -> R.string.finder_trend_steady
    }
    Surface(shape = RoundedCornerShape(50), color = content.copy(alpha = 0.16f), contentColor = content) {
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/** Background and text colour for the current state (design v3 state table). */
@Composable
private fun findColorsFor(state: FinderUiState): FindStateColors {
    val colors = NearoTheme.colors
    return when (state.stage) {
        FinderStage.WAITING -> colors.findSearching
        FinderStage.SIGNAL_LOST -> colors.findLost
        FinderStage.RIGHT_HERE -> colors.findHere
        else -> when (state.band) {
            ProximityBand.FAR, null -> colors.findFar
            ProximityBand.NEAR -> colors.findClose
            // Under 0.5 m but not yet held for 2 s still reads "very close".
            ProximityBand.VERY_CLOSE, ProximityBand.RIGHT_HERE -> colors.findVeryClose
        }
    }
}

/** Raw numbers for testers, behind the "⋯" button in the top bar. */
@Composable
private fun AdvancedMenu(info: AdvancedInfo, tint: Color) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.finder_advanced), tint = tint)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                text = stringResource(R.string.finder_advanced),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 280.dp),
            )
        }
    }
}

private fun ProximityBand.labelRes(): Int = when (this) {
    ProximityBand.RIGHT_HERE -> R.string.finder_band_right_here
    ProximityBand.VERY_CLOSE -> R.string.finder_band_very_close
    ProximityBand.NEAR -> R.string.finder_band_near
    ProximityBand.FAR -> R.string.finder_band_far
}

private fun playHaptic(context: Context, event: FinderEvent) {
    val vibrator = vibrator(context) ?: return
    val effect = when (event) {
        FinderEvent.DIRECTION_FOUND, FinderEvent.DIRECTION_CHANGED ->
            VibrationEffect.createOneShot(HAPTIC_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        // Two short pulses for "locked".
        FinderEvent.LOCKED -> VibrationEffect.createWaveform(longArrayOf(0, HAPTIC_MS, 90, HAPTIC_MS), -1)
    }
    vibrator.vibrate(effect)
}

/** Vibration rhythm per proximity state (design v3): pulses per beat and the gap between beats. */
internal enum class Rhythm(val pulses: Int, val periodMs: Long) {
    NONE(0, 0),
    FAR(1, 2_000),
    CLOSE(1, 1_000),
    VERY_CLOSE(2, 500),
    RIGHT_HERE(1, 0),
}

private fun rhythmFor(state: FinderUiState): Rhythm = when (state.stage) {
    FinderStage.WAITING, FinderStage.SIGNAL_LOST -> Rhythm.NONE
    FinderStage.RIGHT_HERE -> Rhythm.RIGHT_HERE
    else -> when (state.band) {
        ProximityBand.FAR, null -> Rhythm.FAR
        ProximityBand.NEAR -> Rhythm.CLOSE
        ProximityBand.VERY_CLOSE, ProximityBand.RIGHT_HERE -> Rhythm.VERY_CLOSE
    }
}

/** Runs until the rhythm changes (the caller's LaunchedEffect restarts it). */
private suspend fun playRhythm(context: Context, rhythm: Rhythm) {
    val vibrator = vibrator(context) ?: return
    when (rhythm) {
        Rhythm.NONE -> Unit
        // One long buzz: "found it", then quiet so it does not nag while the user picks it up.
        Rhythm.RIGHT_HERE -> vibrator.vibrate(
            VibrationEffect.createOneShot(LONG_HAPTIC_MS, VibrationEffect.DEFAULT_AMPLITUDE),
        )
        else -> while (true) {
            val effect = if (rhythm.pulses == 2) {
                VibrationEffect.createWaveform(longArrayOf(0, HAPTIC_MS, 90, HAPTIC_MS), -1)
            } else {
                VibrationEffect.createOneShot(HAPTIC_MS, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vibrator.vibrate(effect)
            delay(rhythm.periodMs)
        }
    }
}

private fun vibrator(context: Context): Vibrator? {
    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    return vibrator?.takeIf { it.hasVibrator() }
}

private const val HAPTIC_MS = 40L
private const val LONG_HAPTIC_MS = 400L
private const val COLOR_FADE_MS = 400
private const val MUTED_ALPHA = 0.78f
private val GAUGE_TRACK = 6.dp
private val DIAL_MIN = 180.dp
private val GAUGE_DOT = 16.dp
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
