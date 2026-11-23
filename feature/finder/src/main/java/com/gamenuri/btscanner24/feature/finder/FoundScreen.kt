package com.gamenuri.btscanner24.feature.finder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.core.model.DeviceLabel
import com.gamenuri.btscanner24.core.model.NameSource
import com.gamenuri.btscanner24.core.ui.text

/**
 * "Found it!" (design screens-rest-v3 §1): full-screen mint, the device and how long it took.
 * Saving to My devices and the in-app review prompt come with #32/#9 storage and the Play libraries.
 */
@Composable
fun FoundContent(label: DeviceLabel, elapsedSeconds: Int, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NearoTheme.colors.findHere
    CompositionLocalProvider(LocalContentColor provides colors.content) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.background)
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.found_emoji), style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.found_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.found_summary, label.text(), elapsedText(elapsedSeconds)),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.content, contentColor = colors.background),
            ) {
                Text(stringResource(R.string.found_done))
            }
        }
    }
}

/** "1 min 42 s" or "35 s". */
@Composable
private fun elapsedText(seconds: Int): String {
    val safe = seconds.coerceAtLeast(1)
    val minutes = safe / 60
    val rest = safe % 60
    return if (minutes == 0) {
        pluralStringResource(R.plurals.found_seconds, rest, rest)
    } else {
        stringResource(R.string.found_minutes_seconds, minutes, rest)
    }
}

@Preview(name = "Found")
@Composable
private fun FoundPreview() {
    NearoTheme {
        FoundContent(
            label = DeviceLabel.Named("My Earbuds", NameSource.ADVERTISED, suffix = "0001"),
            elapsedSeconds = 102,
            onDone = {},
        )
    }
}
