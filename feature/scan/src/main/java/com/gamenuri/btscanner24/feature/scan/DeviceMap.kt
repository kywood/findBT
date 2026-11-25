package com.gamenuri.btscanner24.feature.scan

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.core.model.DistanceUnit
import com.gamenuri.btscanner24.core.model.MapEntry
import com.gamenuri.btscanner24.core.model.MapLayout
import com.gamenuri.btscanner24.core.sensors.PoseSensor
import com.gamenuri.btscanner24.core.ui.LocalDistanceUnit
import com.gamenuri.btscanner24.core.ui.arrow3d.DeviceModel
import com.gamenuri.btscanner24.core.ui.arrow3d.PoseFeed
import com.gamenuri.btscanner24.core.ui.distanceText
import com.gamenuri.btscanner24.core.ui.iconRes
import com.gamenuri.btscanner24.core.ui.label
import com.gamenuri.btscanner24.core.ui.map3d.MapPin
import com.gamenuri.btscanner24.core.ui.map3d.MapScene3d
import com.gamenuri.btscanner24.core.ui.map3d.MapSceneSpec
import com.gamenuri.btscanner24.core.ui.text
import kotlinx.coroutines.launch

/**
 * The Nearby tab as a 3D map (design bt-map-3d v2.2, #86): the same devices and filters as the list,
 * standing round "me" by distance. The map never claims a direction it does not know, and says so in
 * the chip at the top. Tapping a device (or its row in the sheet) picks it out; tapping it again, or
 * "Find" on its row, opens the find screen. Nothing shown here leaves the phone (#19).
 */
@Composable
internal fun DeviceMap(
    state: ScanUiState,
    actions: ScanActions,
    modifier: Modifier = Modifier,
    /** The view follows how the phone is held and turned; off for demo screenshots. */
    followPose: Boolean = true,
) {
    val context = LocalContext.current
    val items = state.items.filter { !it.isGone }
    var pickedKey by rememberSaveable { mutableStateOf<String?>(null) }
    if (pickedKey != null && items.none { it.device.address == pickedKey }) pickedKey = null
    var sceneUnavailable by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val spots = MapLayout.arrange(items.map { MapEntry(it.device.address, it.distanceMeters) })
    val pins = items.mapNotNull { item ->
        val spot = spots[item.device.address] ?: return@mapNotNull null
        val name = item.label.text(withSuffix = item.showSuffix).shortened()
        val distance = item.distanceMeters
        MapPin(
            key = item.device.address,
            model = DeviceModel.of(item.device.category),
            angleDegrees = spot.angleDegrees,
            ring = spot.ring,
            label = if (distance == null) {
                name
            } else {
                stringResource(R.string.map_pin_label, name, distanceText(distance))
            },
            measured = spot.measured,
        )
    }
    val feet = LocalDistanceUnit.current == DistanceUnit.FEET
    val stepLabels = listOf(
        stringResource(if (feet) R.string.map_step_near_ft else R.string.map_step_near),
        stringResource(if (feet) R.string.map_step_mid_ft else R.string.map_step_mid),
        stringResource(if (feet) R.string.map_step_far_ft else R.string.map_step_far),
    )
    val poseFeed = remember(context) {
        val sensor = PoseSensor(context.applicationContext)
        PoseFeed { onPose ->
            sensor.start { pose ->
                onPose(pose.headingDegrees.toFloat(), pose.elevationDegrees.toFloat(), pose.rollDegrees.toFloat())
            }
        }
    }
    val reduceMotion = remember(context) { context.animationsOff() }
    var height by remember { mutableIntStateOf(0) }
    var chipsBottom by remember { mutableIntStateOf(0) }
    var sheetHeight by remember { mutableIntStateOf(0) }
    val colors = NearoTheme.colors
    val background = colors.findFar.background

    fun open(key: String) {
        items.firstOrNull { it.device.address == key }?.let { actions.onDeviceClick(it.device) }
    }

    CompositionLocalProvider(LocalContentColor provides colors.findFar.content) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(background)
                .onSizeChanged { height = it.height },
        ) {
            if (!sceneUnavailable) {
                MapScene3d(
                    spec = MapSceneSpec(
                        pins = pins,
                        selectedKey = pickedKey,
                        backgroundColor = background,
                        accentColor = colors.found.rings,
                        reduceMotion = reduceMotion,
                        clipTop = if (height > 0) chipsBottom.toFloat() / height else 0f,
                        clipBottom = if (height > 0 && items.isNotEmpty()) 1f - sheetHeight.toFloat() / height else 1f,
                        stepLabels = stepLabels,
                    ),
                    modifier = Modifier.fillMaxSize(),
                    poseFeed = if (followPose) poseFeed else null,
                    onTap = { key ->
                        when (key) {
                            null -> pickedKey = null
                            pickedKey -> open(key)
                            else -> {
                                pickedKey = key
                                val row = items.indexOfFirst { it.device.address == key }
                                if (row >= 0) scope.launch { listState.animateScrollToItem(row) }
                            }
                        }
                    },
                    onUnavailable = { sceneUnavailable = true },
                )
            }
            Column(modifier = Modifier.fillMaxWidth().onSizeChanged { chipsBottom = it.height }) {
                val chip = if (items.isEmpty()) R.string.map_scanning else R.string.map_arranged_by_distance
                MapChip(
                    text = stringResource(chip),
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                )
            }
            when {
                sceneUnavailable -> MapMessage(stringResource(R.string.map_unavailable), null)
                items.isEmpty() ->
                    MapMessage(stringResource(R.string.map_empty_title), stringResource(R.string.map_empty_body))
            }
            if (items.isNotEmpty()) {
                MapSheet(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { sheetHeight = it.height },
                ) {
                    LazyColumn(state = listState, modifier = Modifier.heightIn(max = SHEET_LIST_HEIGHT)) {
                        itemsIndexed(items, key = { _, item -> item.device.address }) { _, item ->
                            val key = item.device.address
                            MapRow(
                                item = item,
                                picked = key == pickedKey,
                                onClick = { pickedKey = if (key == pickedKey) null else key },
                                onFind = { open(key) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A small pill under the top bar: white on translucent white (v2.2). */
@Composable
private fun MapChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = CHIP_ALPHA))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Title and an optional line under it, centred below the chips: the empty state. */
@Composable
private fun MapMessage(title: String, body: String?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 88.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = LocalContentColor.current.copy(alpha = SECONDARY_ALPHA),
            )
        }
    }
}

/** The list over the lower part of the map: translucent white with a rounded top and a grip. */
@Composable
private fun MapSheet(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Color.White.copy(alpha = SHEET_ALPHA))
            .padding(top = 10.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = GRIP_ALPHA)),
        )
        Spacer(Modifier.height(6.dp))
        content()
    }
}

/** One device in the sheet: icon tile, name and kind, the distance, and "Find" once it is picked. */
@Composable
private fun MapRow(item: DeviceListItem, picked: Boolean, onClick: () -> Unit, onFind: () -> Unit) {
    val accent = NearoTheme.colors.found.rings
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .background(if (picked) Color.White.copy(alpha = PICKED_ROW_ALPHA) else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { selected = picked },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The mint bar that ties the row to the device picked out on the map.
        Box(Modifier.width(4.dp).height(ROW_HEIGHT).background(if (picked) accent else Color.Transparent))
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = TILE_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(item.device.category.iconRes()),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label.text(withSuffix = item.showSuffix),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.device.category.label(),
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = SECONDARY_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (picked) {
            Button(
                onClick = onFind,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = NearoTheme.colors.findFar.background,
                ),
            ) {
                Text(stringResource(R.string.map_find))
            }
        } else {
            val distance = item.distanceMeters
            if (distance != null) {
                Text(
                    text = distanceText(distance),
                    style = MaterialTheme.typography.labelLarge,
                    color = LocalContentColor.current.copy(alpha = DISTANCE_ALPHA),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
    }
}

/** A name short enough for a tag over a device; the row in the sheet shows it whole. */
private fun String.shortened(): String = if (length > MAX_TAG_NAME) take(MAX_TAG_NAME - 1).trimEnd() + "…" else this

/** The system's "remove animations" setting. */
internal fun Context.animationsOff(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

private const val MAX_TAG_NAME = 18
private const val CHIP_ALPHA = 0.16f
private const val SHEET_ALPHA = 0.13f
private const val GRIP_ALPHA = 0.4f
private const val TILE_ALPHA = 0.2f
private const val PICKED_ROW_ALPHA = 0.16f
private const val SECONDARY_ALPHA = 0.75f
private const val DISTANCE_ALPHA = 0.9f
private val ROW_HEIGHT = 60.dp

/** Three rows show; the rest scroll inside the sheet. */
private val SHEET_LIST_HEIGHT = ROW_HEIGHT * 3
