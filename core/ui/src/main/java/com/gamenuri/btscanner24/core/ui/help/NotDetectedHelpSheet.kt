package com.gamenuri.btscanner24.core.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.core.ui.R

private val reasons = listOf(
    R.string.help_not_detected_case,
    R.string.help_not_detected_battery,
    R.string.help_not_detected_range,
    R.string.help_not_detected_one_bud,
    R.string.help_not_detected_settings,
)

/**
 * Why a device does not show up and what to try next (#154). Opened from the find screen when the
 * signal is missing and from My devices when a device is not seen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotDetectedHelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        NotDetectedHelpContent(onDismiss)
    }
}

@Composable
private fun NotDetectedHelpContent(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.help_not_detected_title), style = MaterialTheme.typography.titleLarge)
        reasons.forEach { reason ->
            Row {
                Text("•", modifier = Modifier.width(16.dp), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(reason), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.help_not_detected_ok))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun NotDetectedHelpPreview() {
    NearoTheme { NotDetectedHelpContent(onDismiss = {}) }
}
