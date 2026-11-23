package com.gamenuri.btscanner24.feature.finder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.core.model.DeviceLabel
import com.gamenuri.btscanner24.core.model.NameSource
import com.gamenuri.btscanner24.core.ui.text

/**
 * "Found it!" (design screens-rest-v3 §1, #70): full-screen mint, a white check inside the same rings
 * as "Right here", the device and how long it took; Done stays at the bottom.
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CheckHero(colors.content)
                Spacer(Modifier.height(28.dp))
                Text(
                    text = stringResource(R.string.found_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.found_summary, label.text(), elapsedText(elapsedSeconds)),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.content, contentColor = colors.background),
            ) {
                Text(stringResource(R.string.found_done), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** White 84 dp circle with a check, inside two rings (110 and 150 dp) at 25 % of the content colour. */
@Composable
private fun CheckHero(content: Color) {
    Box(modifier = Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val ring = content.copy(alpha = 0.25f)
            val stroke = Stroke(1.5.dp.toPx())
            drawCircle(content.copy(alpha = 0.06f), radius = size.minDimension / 2)
            drawCircle(ring, radius = size.minDimension / 2 - stroke.width / 2, style = stroke)
            drawCircle(content.copy(alpha = 0.06f), radius = 55.dp.toPx())
            drawCircle(ring, radius = 55.dp.toPx(), style = stroke)
        }
        Surface(shape = CircleShape, color = Color.White, shadowElevation = 3.dp, modifier = Modifier.size(84.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = content, modifier = Modifier.size(44.dp))
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
