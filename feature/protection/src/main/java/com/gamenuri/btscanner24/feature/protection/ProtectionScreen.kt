package com.gamenuri.btscanner24.feature.protection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamenuri.btscanner24.core.ble.BleDependencies
import com.gamenuri.btscanner24.core.ble.BleScanner
import com.gamenuri.btscanner24.core.data.DataDependenciesProvider
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.core.model.DeviceCategory
import com.gamenuri.btscanner24.core.model.PairedDevice
import com.gamenuri.btscanner24.core.model.ProtectionSettings
import com.gamenuri.btscanner24.core.model.SavedDevice
import com.gamenuri.btscanner24.core.model.TrackerAlert
import com.gamenuri.btscanner24.core.sensors.MoveTrigger
import com.gamenuri.btscanner24.core.ui.DeviceIcon
import com.gamenuri.btscanner24.core.ui.label
import kotlinx.coroutines.launch

/** One of the user's devices that can be watched. */
internal data class ProtectionChoice(
    val address: String,
    val name: String?,
    val category: DeviceCategory,
    /** Saved devices are watched until switched off; paired ones only once switched on. */
    val isSaved: Boolean,
)

/** Saved devices first, then the paired ones people lose; the same devices My devices lists up front. */
internal fun protectionChoices(
    saved: List<SavedDevice>,
    paired: List<PairedDevice>,
    hidden: Set<String>,
): List<ProtectionChoice> {
    val savedAddresses = saved.mapTo(HashSet()) { it.address }
    return saved.map { ProtectionChoice(it.address, it.name.takeIf(String::isNotBlank), it.category, isSaved = true) } +
        paired
            .filter { it.isPriority && it.address !in hidden && it.address !in savedAddresses }
            .map { ProtectionChoice(it.address, it.name?.takeIf(String::isNotBlank), it.category, isSaved = false) }
}

/**
 * Protection mode (#163, #79, design protection-screens-v2): what it does and the button to turn it
 * on; once on, the main switch, one left-behind switch per device and unknown-tracker detection.
 *
 * @param turnOnNow opened from the "protection is off" notification: carries on as if the button was pressed.
 * @param onOpenTracker opens the details of the unknown tracker at that address.
 */
@Composable
fun ProtectionScreen(onBack: () -> Unit, turnOnNow: Boolean = false, onOpenTracker: (address: String) -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext
    val data = app as DataDependenciesProvider
    val bonded = (app as BleDependencies.Provider).bleDependencies.bondedDevices
    val scope = rememberCoroutineScope()
    val settings by data.protection.settings.collectAsStateWithLifecycle(initialValue = null)
    val saved by data.savedDevices.saved.collectAsStateWithLifecycle(initialValue = emptyList())
    val hidden by data.hiddenDevices.hidden.collectAsStateWithLifecycle(initialValue = emptySet())
    val paired = remember { if (bonded.hasPermission()) bonded.list() else emptyList() }
    val choices = remember(saved, paired, hidden) { protectionChoices(saved, paired, hidden) }
    val canDetectTrackers = remember { MoveTrigger(app).isAvailable }

    var refused by rememberSaveable { mutableStateOf(false) }
    fun start() {
        refused = false
        Protection.turnOn(app)
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (context.hasAll(neededPermissions())) {
            start()
        } else {
            refused = true
        }
    }

    fun turnOn() {
        val needed = neededPermissions()
        if (context.hasAll(needed)) {
            start()
        } else {
            permissions.launch(needed.toTypedArray())
        }
    }
    var askedOnOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(turnOnNow, settings) {
        if (turnOnNow && !askedOnOpen && settings?.enabled == false) {
            askedOnOpen = true
            turnOn()
        }
    }
    ProtectionContent(
        settings = settings,
        choices = choices,
        refused = refused,
        canDetectTrackers = canDetectTrackers,
        actions = ProtectionActions(
            onBack = onBack,
            onTurnOn = ::turnOn,
            onTurnOff = { Protection.turnOff(app) },
            onWatch = { address, watched ->
                scope.launch { data.protection.setWatched(address, watched) }
            },
            onTrackers = { on -> scope.launch { data.protection.setTrackers(on) } },
            onOpenTracker = onOpenTracker,
        ),
    )
}

/** Scanning, plus showing notifications on Android 13 and newer. */
private fun neededPermissions(): List<String> = BleScanner.requiredPermissions() +
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyList()
    }

private fun Context.hasAll(permissions: List<String>): Boolean =
    permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

internal class ProtectionActions(
    val onBack: () -> Unit = {},
    val onTurnOn: () -> Unit = {},
    val onTurnOff: () -> Unit = {},
    val onWatch: (address: String, watched: Boolean) -> Unit = { _, _ -> },
    val onTrackers: (Boolean) -> Unit = {},
    val onOpenTracker: (address: String) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProtectionContent(
    /** Null until read from storage: nothing is drawn, so the wrong state never flashes. */
    settings: ProtectionSettings?,
    choices: List<ProtectionChoice>,
    refused: Boolean,
    actions: ProtectionActions,
    /** False on a phone without the motion sensor tracker detection is built on. */
    canDetectTrackers: Boolean = true,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.protection_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.protection_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            settings == null -> Unit
            settings.enabled ->
                WatchedDevices(settings, choices, canDetectTrackers, actions, Modifier.padding(padding))
            else -> Intro(refused, actions, Modifier.padding(padding))
        }
    }
}

@Composable
private fun Intro(refused: Boolean, actions: ProtectionActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(
            painterResource(R.drawable.ic_protection),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.protection_intro_headline),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Point(R.string.protection_point_alert)
                Point(R.string.protection_point_tracker)
                Point(R.string.protection_point_notification)
                Point(R.string.protection_point_battery)
                Point(R.string.protection_point_privacy)
            }
        }
        Spacer(Modifier.height(24.dp))
        if (refused) {
            Text(
                text = stringResource(R.string.protection_permissions_needed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Button(onClick = actions.onTurnOn, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.protection_turn_on))
        }
        TextButton(onClick = actions.onBack) { Text(stringResource(R.string.protection_not_now)) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Point(@StringRes text: Int) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WatchedDevices(
    settings: ProtectionSettings,
    choices: List<ProtectionChoice>,
    canDetectTrackers: Boolean,
    actions: ProtectionActions,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "main") {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_protection), contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.protection_status_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.protection_point_notification),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = true, onCheckedChange = { if (!it) actions.onTurnOff() })
                }
            }
        }
        item(key = "h-left-behind") { SectionTitle(R.string.protection_section_left_behind) }
        if (choices.isEmpty()) {
            item(key = "none") { Note(stringResource(R.string.protection_no_devices)) }
        }
        items(choices, key = { it.address }) { choice ->
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeviceIcon(choice.category)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = choice.name ?: choice.category.label(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = settings.watches(choice.address, choice.isSaved),
                    onCheckedChange = { actions.onWatch(choice.address, it) },
                )
            }
        }
        item(key = "h-trackers") { SectionTitle(R.string.protection_section_trackers) }
        item(key = "trackers") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = settings.trackers && canDetectTrackers,
                        enabled = canDetectTrackers,
                        role = Role.Switch,
                        onValueChange = actions.onTrackers,
                    )
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.protection_tracker_setting),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.protection_point_tracker),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                // The whole row is the switch for TalkBack and touch.
                Switch(
                    checked = settings.trackers && canDetectTrackers,
                    onCheckedChange = null,
                    enabled = canDetectTrackers,
                )
            }
        }
        items(settings.trackerAlerts, key = { "t-" + it.address }) { alert ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { actions.onOpenTracker(alert.address) }
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeviceIcon(DeviceCategory.TRACKER)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.protection_tracker_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.protection_tracker_last, context.formatTime(alert.lastHeardAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "trackers-how") {
            Note(
                stringResource(
                    if (canDetectTrackers) R.string.protection_tracker_how else R.string.protection_tracker_unavailable,
                ),
            )
        }
        item(key = "footer") { Note(stringResource(R.string.protection_footer)) }
    }
}

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Preview(name = "On", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProtectionOnPreview() {
    NearoTheme {
        ProtectionContent(
            settings = ProtectionSettings(
                enabled = true,
                trackerAlerts = listOf(TrackerAlert("7A:00:00:00:00:01", 0L, 2_400_000L)),
            ),
            choices = listOf(
                ProtectionChoice("A", "Keys Tag", DeviceCategory.TRACKER, isSaved = true),
                ProtectionChoice("B", "Running Watch", DeviceCategory.WATCH, isSaved = false),
            ),
            refused = false,
            actions = ProtectionActions(),
        )
    }
}

@Preview(name = "Off", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProtectionOffPreview() {
    NearoTheme {
        ProtectionContent(
            settings = ProtectionSettings(),
            choices = emptyList(),
            refused = true,
            actions = ProtectionActions(),
        )
    }
}
