package com.gamenuri.btscanner24.feature.protection

import android.content.Context
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gamenuri.btscanner24.core.data.DataDependenciesProvider
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.core.model.DeviceCategory
import com.gamenuri.btscanner24.core.model.TrackerAlert
import com.gamenuri.btscanner24.core.ui.DeviceIcon
import kotlinx.coroutines.launch

/**
 * An unknown tracker that may be moving with the user (#79, design protection-screens-v2): what was
 * seen, a reminder that it is usually someone else's nearby, and what to do. It states only what the
 * phone heard; whose tracker it is cannot be known from a Bluetooth signal.
 *
 * @param onFind opens the find screen for the tracker's address.
 */
@Composable
fun TrackerScreen(address: String, onBack: () -> Unit, onFind: (address: String) -> Unit) {
    val data = LocalContext.current.applicationContext as DataDependenciesProvider
    val scope = rememberCoroutineScope()
    val settings by data.protection.settings.collectAsStateWithLifecycle(initialValue = null)
    var showHelp by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showHelp) { showHelp = false }
    TrackerContent(
        // Null until read from storage: nothing is drawn, so "nothing kept" never flashes.
        state = settings?.let { TrackerState(it.trackerAlerts.firstOrNull { alert -> alert.address == address }) },
        showHelp = showHelp,
        actions = TrackerActions(
            onBack = { if (showHelp) showHelp = false else onBack() },
            onFind = { onFind(address) },
            onKnown = {
                // Written before leaving: the screen's scope ends with it.
                scope.launch {
                    data.protection.markTrackerKnown(address, System.currentTimeMillis())
                    onBack()
                }
            },
            onHelp = { showHelp = true },
        ),
    )
}

/** What is kept about the tracker; [alert] is null once it was answered or grew too old. */
internal class TrackerState(val alert: TrackerAlert?)

internal class TrackerActions(
    val onBack: () -> Unit = {},
    val onFind: () -> Unit = {},
    val onKnown: () -> Unit = {},
    val onHelp: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackerContent(state: TrackerState?, showHelp: Boolean, actions: TrackerActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { if (showHelp) Text(stringResource(R.string.protection_help_title)) },
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
        val modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
        when {
            showHelp -> Help(modifier)
            state == null -> Unit
            state.alert == null -> Text(
                text = stringResource(R.string.protection_tracker_gone),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.padding(16.dp),
            )
            else -> Details(state.alert, actions, modifier)
        }
    }
}

@Composable
private fun Details(alert: TrackerAlert, actions: TrackerActions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val minutes = ((alert.lastHeardAt - alert.firstHeardAt) / MINUTE_MS).toInt().coerceAtLeast(1)
    Column(modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        DeviceIcon(DeviceCategory.TRACKER, size = 56.dp)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.protection_tracker_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(R.string.protection_tracker_last, context.formatTime(alert.lastHeardAt)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Fact(R.string.protection_tracker_first, context.formatTime(alert.firstHeardAt))
                Fact(
                    R.string.protection_tracker_duration,
                    pluralStringResource(R.plurals.protection_tracker_duration_value, minutes, minutes),
                )
                Fact(R.string.protection_tracker_type, stringResource(R.string.protection_tracker_type_value))
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.protection_tracker_calm_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.protection_tracker_calm_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = actions.onFind, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.protection_tracker_find))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = actions.onKnown, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.protection_tracker_known))
        }
        TextButton(onClick = actions.onHelp) { Text(stringResource(R.string.protection_tracker_what)) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Fact(@StringRes label: Int, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Help(modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Step(1, R.string.protection_help_1_title, R.string.protection_help_1_body)
        Step(2, R.string.protection_help_2_title, R.string.protection_help_2_body)
        Step(3, R.string.protection_help_3_title, R.string.protection_help_3_body)
        Step(4, R.string.protection_help_4_title, R.string.protection_help_4_body)
        Text(
            text = stringResource(R.string.protection_help_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun Step(number: Int, @StringRes title: Int, @StringRes body: Int) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "2:10 PM" today, "Nov 25, 2:10 PM" otherwise, in the phone's language and clock style. */
internal fun Context.formatTime(time: Long): String {
    val date = if (DateUtils.isToday(time)) 0 else DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
    return DateUtils.formatDateTime(this, time, DateUtils.FORMAT_SHOW_TIME or date)
}

private const val MINUTE_MS = 60_000L

@Preview(name = "Details", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TrackerDetailsPreview() {
    NearoTheme {
        TrackerContent(
            state = TrackerState(TrackerAlert("7A:00:00:00:00:01", 0L, 40 * MINUTE_MS)),
            showHelp = false,
            actions = TrackerActions(),
        )
    }
}

@Preview(name = "Help", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TrackerHelpPreview() {
    NearoTheme { TrackerContent(state = null, showHelp = true, actions = TrackerActions()) }
}
