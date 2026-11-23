package com.gamenuri.btscanner24.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gamenuri.btscanner24.core.designsystem.icon.NearoIcons
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme

/** Circle tint: brand for invitations (onboarding), neutral for problems (permission denied). */
enum class IllustrationTone { PRIMARY, NEUTRAL }

/**
 * Centred illustration, title and body (design screens-rest-v3: onboarding, permission, empty and
 * error states). [extra] goes under the body, e.g. the onboarding check list or an action button.
 */
@Composable
fun IllustratedMessage(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tone: IllustrationTone = IllustrationTone.PRIMARY,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val (circle, tint) = when (tone) {
        IllustrationTone.PRIMARY -> scheme.primaryContainer to scheme.primary
        IllustrationTone.NEUTRAL -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(circle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        extra()
    }
}

@Preview(showBackground = true)
@Composable
private fun IllustratedMessagePreview() {
    NearoTheme {
        IllustratedMessage(
            icon = NearoIcons.BluetoothOff,
            title = "Bluetooth is off",
            body = "Turn on Bluetooth to find devices around you.",
        )
    }
}
