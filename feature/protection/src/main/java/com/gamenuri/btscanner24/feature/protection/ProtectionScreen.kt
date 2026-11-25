package com.gamenuri.btscanner24.feature.protection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import com.gamenuri.btscanner24.core.ui.DeviceIcon
import com.gamenuri.btscanner24.core.ui.label
import kotlinx.coroutines.launch

/** One of the user's devices that can be watched. */
internal data class ProtectionChoice(val address: String, val name: String?, val category: DeviceCategory)

/** Saved devices first, then the paired ones people lose; the same devices My devices lists up front. */
internal fun protectionChoices(
    saved: List<SavedDevice>,
    paired: List<PairedDevice>,
    hidden: Set<String>,
): List<ProtectionChoice> {
    val savedAddresses = saved.mapTo(HashSet()) { it.address }
    return saved.map { ProtectionChoice(it.address, it.name.takeIf(String::isNotBlank), it.category) } +
        paired
            .filter { it.isPriority && it.address !in hidden && it.address !in savedAddresses }
            .map { ProtectionChoice(it.address, it.name?.takeIf(String::isNotBlank), it.category) }
}

/**
 * Protection mode (#163, design protection-screens-v2): what it does and the button to turn it on;
 * once on, the main switch and one left-behind switch per device.
 *
 * @param turnOnNow opened from the "protection is off" notification: carries on as if the button was pressed.
 */
@Composable
fun ProtectionScreen(onBack: () -> Unit, turnOnNow: Boolean = false) {
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

    var refused by rememberSaveable { mutableStateOf(false) }
    fun start() {
        refused = false
        scope.launch {
            // The first time, every device offered is watched; after that the switches decide.
            data.protection.watchIfUnset(choices.mapTo(HashSet()) { it.address })
            Protection.turnOn(app)
        }
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
        actions = ProtectionActions(
            onBack = onBack,
            onTurnOn = ::turnOn,
            onTurnOff = { Protection.turnOff(app) },
            onWatch = { address, watched ->
                scope.launch { data.protection.setWatched(address, watched) }
            },
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
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProtectionContent(
    /** Null until read from storage: nothing is drawn, so the wrong state never flashes. */
    settings: ProtectionSettings?,
    choices: List<ProtectionChoice>,
    refused: Boolean,
    actions: ProtectionActions,
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
            settings.enabled -> WatchedDevices(settings, choices, actions, Modifier.padding(padding))
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
    actions: ProtectionActions,
    modifier: Modifier = Modifier,
) {
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
        item(key = "h-left-behind") {
            Text(
                text = stringResource(R.string.protection_section_left_behind),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
        }
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
                    checked = choice.address in settings.watched,
                    onCheckedChange = { actions.onWatch(choice.address, it) },
                )
            }
        }
        item(key = "footer") { Note(stringResource(R.string.protection_footer)) }
    }
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
            settings = ProtectionSettings(enabled = true, watched = setOf("A")),
            choices = listOf(
                ProtectionChoice("A", "Keys Tag", DeviceCategory.TRACKER),
                ProtectionChoice("B", "Running Watch", DeviceCategory.WATCH),
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
