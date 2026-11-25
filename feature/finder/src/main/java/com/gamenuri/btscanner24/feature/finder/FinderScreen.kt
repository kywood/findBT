package com.gamenuri.btscanner24.feature.finder

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamenuri.btscanner24.core.ble.ScanFailure
import com.gamenuri.btscanner24.core.ble.proximity.ProximityBand
import com.gamenuri.btscanner24.core.ble.proximity.ProximityTrend
import com.gamenuri.btscanner24.core.designsystem.component.WATER_RISE_MS
import com.gamenuri.btscanner24.core.designsystem.component.waterRise
import com.gamenuri.btscanner24.core.designsystem.theme.FindStateColors
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTextStyles
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.core.model.DeviceLabel
import com.gamenuri.btscanner24.core.model.NameSource
import com.gamenuri.btscanner24.core.ui.R as UiR
import com.gamenuri.btscanner24.core.ui.distanceText
import com.gamenuri.btscanner24.core.ui.text
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Entry point: owns the ViewModel, lifecycle, haptics and keep-screen-on. Visuals are in [FinderContent]. */
@Composable
fun FinderScreen(
    address: String,
    /** Leaves the screen; [Brush] is the background on screen now, for the drain over Home (#101). */
    onClose: (cover: Brush) -> Unit,
    /** "Done" on the Found screen, right before [onClose]; the app may show its full-screen ad then (#81). */
    onFoundDone: () -> Unit = {},
    /** Water level of the entry from Home (#90), 0..1 over the press and the rise; 1 shows it fully. */
    entryProgress: () -> Float = { 1f },
    viewModel: FinderViewModel = viewModel(key = "finder-$address", factory = FinderViewModel.factory(address)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // "I found it" swaps in the Found screen; leaving this branch stops the scan, sensors and haptics.
    var foundAfterSeconds by rememberSaveable { mutableStateOf<Int?>(null) }
    val found = NearoTheme.colors.found
    val cover = if (foundAfterSeconds != null) {
        Brush.verticalGradient(listOf(found.backgroundTop, found.backgroundBottom))
    } else {
        findColorsFor(state).brush()
    }
    val close by rememberUpdatedState { onClose(cover) }
    // System back drains the same way as ✕ and Done.
    BackHandler { close() }
    foundAfterSeconds?.let { seconds ->
        FoundRise(
            state,
            seconds,
            onDone = {
                onFoundDone()
                close()
            },
        )
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
        entryProgress = entryProgress,
        actions = FinderActions(
            onClose = { close() },
            onFound = { foundAfterSeconds = state.advanced.elapsedSeconds },
            onToggleVibration = viewModel::toggleVibration,
            onSpinAgain = viewModel::spinAgain,
            onToggleSound = viewModel::toggleSound,
            onRetry = viewModel::retry,
        ),
    )
}

data class FinderActions(
    val onClose: () -> Unit = {},
    val onFound: () -> Unit = {},
    val onToggleVibration: () -> Unit = {},
    val onSpinAgain: () -> Unit = {},
    val onToggleSound: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/**
 * Found it (#90 v2): the Found screen rises over a still picture of the find screen, its content fades
 * in from 45 %, then the check pops once.
 */
@Composable
private fun FoundRise(state: FinderUiState, seconds: Int, onDone: () -> Unit) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, tween(WATER_RISE_MS, easing = LinearEasing)) }
    val colors = NearoTheme.colors.found
    Box(Modifier.fillMaxSize()) {
        if (rise.value < 1f) FinderContent(state, FinderActions())
        FoundContent(
            label = state.deviceLabel,
            category = state.category,
            elapsedSeconds = seconds,
            onDone = onDone,
            popCheckAfterMs = WATER_RISE_MS,
            modifier = Modifier.waterRise(
                progress = { rise.value },
                cover = Brush.verticalGradient(listOf(colors.backgroundTop, colors.backgroundBottom)),
                contentFrom = FOUND_CONTENT_FROM,
                pressMs = 0,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinderContent(
    state: FinderUiState,
    actions: FinderActions,
    modifier: Modifier = Modifier,
    entryProgress: () -> Float = { 1f },
) {
    val palette = findColorsFor(state)
    val background = palette.background
    val stage = rememberStageRise(palette)
    val content by animateColorAsState(palette.content, tween(COLOR_FADE_MS), label = "findContent")
    val accent by animateColorAsState(palette.accent, tween(COLOR_FADE_MS), label = "findAccent")
    // The 3D scene (#76) is a layer behind the whole screen, not a box in the layout; without
    // working GL the dial draws the Canvas arrow in its place.
    var sceneFailed by remember { mutableStateOf(false) }
    val scene3d = LocalArFindScene.current && !sceneFailed && !LocalInspectionMode.current
    val dialSlot = remember { SceneSlot() }
    // A new stage colour rises like water over the old one (#90); the content stays where it is.
    Box(
        modifier
            .fillMaxSize()
            .waterRise(progress = entryProgress, cover = palette.brush())
            .background(stage.previous.brush()),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .waterRise(progress = { stage.progress.value }, glint = STAGE_GLINT, pressMs = 0)
                .background(palette.brush()),
        )
        if (scene3d && state.showsScene()) {
            // Lower than the slot's middle (design find-flow v1.4): the ring sits just over the distance
            // block, which leaves the far step and the device marker the room above it.
            FindSceneLayer(
                dialSlot,
                fadeOutsideSlot = true,
                centerBias = SCENE_CENTER_BIAS,
            ) { centerY, topFade, bottomFade ->
                CompositionLocalProvider(LocalContentColor provides accent) {
                    val fades = SceneFades(topFade, bottomFade)
                    ArrowScene(state, centerY, fades, onUnavailable = { sceneFailed = true })
                }
            }
        }
        FinderScaffold(state, actions, content, accent, background, scene3d, dialSlot)
    }
}

private fun FindStateColors.brush(): Brush = Brush.verticalGradient(listOf(background, backgroundBottom))

/** Background colour of the last stage and the rise of the new one; quick back-and-forth just snaps. */
private class StageRise(initial: FindStateColors) {
    var previous by mutableStateOf(initial)
    val progress = Animatable(1f)
}

@Composable
private fun rememberStageRise(target: FindStateColors): StageRise {
    val rise = remember { StageRise(target) }
    var shown by remember { mutableStateOf(target) }
    var lastRiseAt by remember { mutableStateOf(0L) }
    LaunchedEffect(target) {
        // Only the background matters: a mint accent switching on is not a new water level.
        if (target.background == shown.background && target.backgroundBottom == shown.backgroundBottom) {
            shown = target
            return@LaunchedEffect
        }
        val now = System.currentTimeMillis()
        rise.previous = shown
        shown = target
        if (now - lastRiseAt < STAGE_RISE_GAP_MS) {
            rise.progress.snapTo(1f)
        } else {
            lastRiseAt = now
            rise.progress.snapTo(0f)
            rise.progress.animateTo(1f, tween(WATER_RISE_MS, easing = LinearEasing))
        }
        rise.previous = target
    }
    return rise
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinderScaffold(
    state: FinderUiState,
    actions: FinderActions,
    content: Color,
    accent: Color,
    background: Color,
    sceneBehind: Boolean,
    dialSlot: SceneSlot,
) {
    Scaffold(
        containerColor = Color.Transparent,
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
                actions = { AdvancedMenu(state.advanced, tint = content, background = background) },
            )
        },
        // The action row is pinned above the navigation bar and never scrolls away (design review #55).
        bottomBar = { ActionRow(state, actions, content, background) },
    ) { padding ->
        CompositionLocalProvider(LocalFindAccent provides accent) {
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
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .sceneSlot(dialSlot),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides accent) {
                        DirectionDial(state, Modifier.heightIn(min = DIAL_MIN), sceneBehind)
                    }
                }
                DistanceBlock(state)
                // One fixed-height slot under the gauge for the confirm hint when very close (#94). It
                // always takes its space, so the rows above keep their place (#60).
                Box(Modifier.fillMaxWidth().heightIn(min = BOTTOM_SLOT), contentAlignment = Alignment.Center) {
                    val noSignal = state.stage == FinderStage.NOT_FOUND || state.stage == FinderStage.SIGNAL_LOST
                    if (noSignal) {
                        // Never a dead end (#117): scan afresh, or leave for the list.
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = actions.onRetry) {
                                Text(stringResource(R.string.finder_retry), color = content)
                            }
                            TextButton(onClick = actions.onClose) {
                                Text(stringResource(R.string.finder_back_to_list), color = content)
                            }
                        }
                    } else if (state.showConfirmHint) {
                        // Names can be vague ("hiot0"); confirming by touch matters more than the name (#46).
                        Text(
                            text = stringResource(R.string.finder_confirm_hint),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = content.copy(alpha = HINT_ALPHA),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(state: FinderUiState, actions: FinderActions, content: Color, background: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // "Spin again" shares the sound button's row, half each (design find-flow v1.4, #138).
        // Also while locked: a lock can be wrong, and the design keeps the way back to a new sweep.
        val canRespin = state.stage == FinderStage.WALK ||
            state.stage == FinderStage.TURN_AROUND ||
            state.stage == FinderStage.LOCKED
        val respin: (@Composable (Modifier) -> Unit)? = if (canRespin) {
            { modifier -> SpinAgainButton(actions.onSpinAgain, content, modifier) }
        } else {
            null
        }
        if (state.showSoundButton) {
            SoundButton(state.sound, actions.onToggleSound, content, respin)
        } else {
            respin?.invoke(Modifier.fillMaxWidth())
        }
        MainActions(state, actions, content, background)
    }
}

@Composable
private fun SpinAgainButton(onClick: () -> Unit, content: Color, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = content),
        border = BorderStroke(1.dp, content.copy(alpha = 0.5f)),
    ) {
        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.finder_spin_again), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Only offered while the device is in range, or while a sound is already going (#28). */
private val FinderUiState.showSoundButton: Boolean
    get() = when (sound) {
        SoundState.UNAVAILABLE -> false
        SoundState.CONNECTING, SoundState.PLAYING, SoundState.NOT_SUPPORTED -> true
        SoundState.READY, SoundState.FAILED ->
            stage != FinderStage.WAITING && stage != FinderStage.NOT_FOUND && stage != FinderStage.SIGNAL_LOST
    }

@Composable
private fun SoundButton(
    sound: SoundState,
    onClick: () -> Unit,
    content: Color,
    /** A second button for the same row, given the modifier that makes it share the width. */
    beside: (@Composable (Modifier) -> Unit)? = null,
) {
    if (sound == SoundState.NOT_SUPPORTED) {
        Text(
            text = stringResource(R.string.finder_sound_not_supported),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = content.copy(alpha = MUTED_ALPHA),
            textAlign = TextAlign.Center,
        )
        beside?.invoke(Modifier.fillMaxWidth())
        return
    }
    if (sound == SoundState.FAILED) {
        Text(
            text = stringResource(R.string.finder_sound_failed),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = content.copy(alpha = MUTED_ALPHA),
            textAlign = TextAlign.Center,
        )
    }
    val label = when (sound) {
        SoundState.CONNECTING -> R.string.finder_sound_connecting
        SoundState.PLAYING -> R.string.finder_sound_stop
        SoundState.FAILED -> R.string.finder_sound_retry
        else -> R.string.finder_sound_play
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onClick,
            // Tapping while connecting cancels, same as stop.
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (sound == SoundState.PLAYING) content.copy(alpha = 0.18f) else Color.Transparent,
                contentColor = content,
            ),
            border = BorderStroke(1.dp, content.copy(alpha = 0.5f)),
        ) {
            Icon(
                painter = painterResource(
                    if (sound == SoundState.PLAYING) UiR.drawable.ic_sound_off else UiR.drawable.ic_sound,
                ),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        beside?.invoke(Modifier.weight(1f))
    }
}

@Composable
private fun MainActions(state: FinderUiState, actions: FinderActions, content: Color, background: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
        FinderStage.NOT_FOUND -> when (state.scanFailure) {
            null -> R.string.finder_not_found_title to R.string.finder_not_found_body
            ScanFailure.BLUETOOTH_OFF -> R.string.finder_bluetooth_off_title to R.string.finder_bluetooth_off_body
            ScanFailure.LOCATION_OFF -> R.string.finder_location_off_title to R.string.finder_location_off_body
            ScanFailure.PERMISSION_MISSING -> R.string.finder_permission_title to R.string.finder_permission_body
            else -> R.string.finder_scan_failed_title to R.string.finder_scan_failed_body
        }
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
    Text(
        text = stringResource(R.string.finder_direction_label),
        modifier = Modifier.slot(visible = state.arrowDegrees != null),
        style = MaterialTheme.typography.labelLarge,
        color = LocalContentColor.current.copy(alpha = MUTED_ALPHA),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        // A vector lock instead of the 🔒 emoji, which looks different on every phone (#71).
        if (state.stage == FinderStage.LOCKED) {
            Icon(painterResource(UiR.drawable.ic_lock), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    }
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

    // Fixed slots (band line, trend chip): only alpha changes, so the number keeps its y (#60).
    val showBand = state.stage != FinderStage.RIGHT_HERE && state.band != null
    // No "Keep going" while the user is asked to spin in place.
    val spinning = state.stage == FinderStage.SPIN || state.stage == FinderStage.SPIN_AGAIN
    // The band and the trend chip share one line (design find-flow v1.4), which leaves the scene more height.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = state.band?.let { stringResource(it.labelRes()) }.orEmpty(),
            modifier = Modifier.slot(visible = showBand),
            style = MaterialTheme.typography.titleMedium,
        )
        if (state.stage != FinderStage.RIGHT_HERE && !spinning) TrendChip(state.trend)
    }
    // One line in every language: "a menos de 1 m" shrinks from 54 sp down to 34 sp instead of losing "m" (#74).
    BasicText(
        text = distanceText(meters),
        style = NearoTextStyles.findDistance.copy(color = LocalContentColor.current, textAlign = TextAlign.Center),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 34.sp, maxFontSize = 54.sp, stepSize = 2.sp),
    )
    Text(
        text = stringResource(R.string.finder_distance_label),
        style = MaterialTheme.typography.labelMedium,
        color = LocalContentColor.current.copy(alpha = MUTED_ALPHA),
    )
    Spacer(Modifier.height(12.dp))
    HeatGauge(state.heat, Modifier.fillMaxWidth(GAUGE_WIDTH))
}

/**
 * Hot-cold gauge as a thin bar (design find-flow v1.4): filled from the left as far as the dot, which
 * moves right as the device gets closer. Position carries the meaning, so it does not depend on colour.
 */
@Composable
private fun HeatGauge(heat: Float, modifier: Modifier = Modifier) {
    val content = LocalFindAccent.current.takeOrElse { LocalContentColor.current }
    val share = heat.coerceIn(0f, 1f)
    BoxWithConstraints(modifier = modifier.height(GAUGE_DOT), contentAlignment = Alignment.CenterStart) {
        val dotStart = (maxWidth - GAUGE_DOT) * share
        Box(
            Modifier
                .fillMaxWidth()
                .height(GAUGE_TRACK)
                .background(content.copy(alpha = 0.3f), RoundedCornerShape(GAUGE_TRACK)),
        )
        Box(
            Modifier
                .width(dotStart + GAUGE_DOT / 2)
                .height(GAUGE_TRACK)
                .background(content, RoundedCornerShape(GAUGE_TRACK)),
        )
        Box(Modifier.offset(x = dotStart).size(GAUGE_DOT).background(content, CircleShape))
    }
}

@Composable
private fun TrendChip(trend: ProximityTrend, modifier: Modifier = Modifier) {
    val content = LocalContentColor.current
    val text = when (trend) {
        ProximityTrend.CLOSER -> R.string.finder_trend_closer
        ProximityTrend.FARTHER -> R.string.finder_trend_farther
        ProximityTrend.STEADY -> R.string.finder_trend_steady
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = content.copy(alpha = 0.16f),
        contentColor = content,
    ) {
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

/** Keeps a row's space when it is hidden, so nothing below it moves; hidden rows are also silent to TalkBack. */
private fun Modifier.slot(visible: Boolean): Modifier = if (visible) this else alpha(0f).clearAndSetSemantics {}

/** Background and text colour for the current state (design v3 state table). */
@Composable
private fun findColorsFor(state: FinderUiState): FindStateColors {
    val colors = NearoTheme.colors
    return when (state.stage) {
        FinderStage.WAITING -> colors.findSearching
        FinderStage.SIGNAL_LOST, FinderStage.NOT_FOUND -> colors.findLost
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
private fun AdvancedMenu(info: AdvancedInfo, tint: Color, background: Color) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.finder_advanced), tint = tint)
        }
        // Surfaces on blue are a lighter blue, never a dark card (#97): white 14 % over the background.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = lerp(background, Color.White, MENU_WHITE),
        ) {
            Text(
                text = stringResource(R.string.finder_advanced),
                color = tint,
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
                color = tint,
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
    FinderStage.WAITING, FinderStage.NOT_FOUND, FinderStage.SIGNAL_LOST -> Rhythm.NONE
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

private const val SCENE_CENTER_BIAS = 0.64f
private const val HAPTIC_MS = 40L
private const val LONG_HAPTIC_MS = 400L
private const val COLOR_FADE_MS = 400
private const val STAGE_RISE_GAP_MS = 1_500L
private const val STAGE_GLINT = 0.8f
private const val MENU_WHITE = 0.14f
private const val FOUND_CONTENT_FROM = 0.45f

private const val MUTED_ALPHA = 0.78f
private const val HINT_ALPHA = 0.8f
private val BOTTOM_SLOT = 48.dp
private val GAUGE_TRACK = 4.dp
private val DIAL_MIN = 180.dp
private val GAUGE_DOT = 12.dp
private const val GAUGE_WIDTH = 0.76f

// Gauge ends (#71): the same on every find state background.
private const val NO_VALUE = "–"

/** The find state's highlight (arrow, gauge); unset outside [FinderContent], where content colour is used. */
private val LocalFindAccent = compositionLocalOf { Color.Unspecified }

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
