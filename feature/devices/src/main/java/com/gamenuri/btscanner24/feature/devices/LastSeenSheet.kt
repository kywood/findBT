package com.gamenuri.btscanner24.feature.devices

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.RelativeDateTimeFormatter.AbsoluteUnit
import android.icu.text.RelativeDateTimeFormatter.Direction
import android.icu.text.RelativeDateTimeFormatter.RelativeUnit
import android.icu.util.ULocale
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gamenuri.btscanner24.core.model.LastSeen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * When and where this phone last heard one of the user's devices (#162, design protection-screens
 * v2). The place opens in the phone's maps app; nothing is drawn or looked up here, and the sheet
 * says plainly that it is not where the device is now. Without a place it shows the time alone and,
 * if location access is what is missing, offers to turn place recording on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LastSeenSheet(
    title: String,
    record: LastSeen,
    now: Long,
    place: PlaceRecording,
    onFind: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(context.hasLocationAccess()) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAccess = granted
    }
    // Recording the place takes both: the user's own "turn it on" and the permission. On Android 11 and
    // older the scan already has the permission, so there the switch alone is the consent (#178).
    val canLocate = place.isOn && hasAccess
    val noMapApp = stringResource(R.string.last_seen_no_map_app)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.last_seen_menu),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(lastSeenTime(context, record.seenAt, now), style = MaterialTheme.typography.titleMedium)
                    val spot = record.place
                    val line = when {
                        spot != null -> R.string.last_seen_place
                        canLocate -> R.string.last_seen_no_fix
                        else -> R.string.last_seen_no_permission
                    }
                    Text(
                        stringResource(line),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (spot != null) {
                        Button(
                            onClick = {
                                if (!context.openInMaps(spot)) {
                                    Toast.makeText(context, noMapApp, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.last_seen_open_map))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.last_seen_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (!canLocate) {
                        OutlinedButton(
                            onClick = {
                                place.onTurnOn()
                                if (!hasAccess) askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.last_seen_turn_on))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onFind, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.last_seen_find))
            }
        }
    }
}

/** Whether the user turned place recording on, and how they do. */
internal class PlaceRecording(val isOn: Boolean = false, val onTurnOn: () -> Unit = {})

/**
 * "2 hours ago", "Nov 24, 6:12 PM", in the app's language. DateUtils' relative wording follows the
 * phone's language instead, so an app set to English on a Korean phone read "2시간 전" (#162).
 */
internal fun lastSeenTime(context: Context, seenAt: Long, now: Long): String {
    val locale = context.resources.configuration.locales[0]
    val ago = (now - seenAt).coerceAtLeast(0L)
    if (ago >= DateUtils.DAY_IN_MILLIS) {
        val time = Instant.ofEpochMilli(seenAt).atZone(ZoneId.systemDefault())
        val format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        return format.withLocale(locale).format(time)
    }
    val words = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(locale))
    val hours = ago / DateUtils.HOUR_IN_MILLIS
    val minutes = ago / DateUtils.MINUTE_IN_MILLIS
    return when {
        hours > 0 -> words.format(hours.toDouble(), Direction.LAST, RelativeUnit.HOURS)
        minutes > 0 -> words.format(minutes.toDouble(), Direction.LAST, RelativeUnit.MINUTES)
        else -> words.format(Direction.PLAIN, AbsoluteUnit.NOW)
    }
}

private fun Context.hasLocationAccess(): Boolean =
    listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION).any {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

/**
 * Hands the place to whatever maps app the user has, as plain coordinates: no device name goes with
 * it (#19). False when no app takes it.
 */
private fun Context.openInMaps(place: LastSeen.Place): Boolean {
    val at = String.format(Locale.US, "%.5f,%.5f", place.latitude, place.longitude)
    return try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$at?q=$at")))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
