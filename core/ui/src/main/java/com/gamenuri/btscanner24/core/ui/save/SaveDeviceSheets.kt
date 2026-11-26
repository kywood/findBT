package com.gamenuri.btscanner24.core.ui.save

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.core.model.DeviceCategory
import com.gamenuri.btscanner24.core.ui.R
import com.gamenuri.btscanner24.core.ui.iconRes
import com.gamenuri.btscanner24.core.ui.label

/** The types offered when saving (design save-to-my-devices-v1): two rows of seven, most-lost first. */
val SAVE_CATEGORIES = listOf(
    DeviceCategory.TRACKER,
    DeviceCategory.EARBUDS,
    DeviceCategory.HEADPHONES,
    DeviceCategory.WATCH,
    DeviceCategory.FITNESS_BAND,
    DeviceCategory.PHONE,
    DeviceCategory.TABLET,
    DeviceCategory.COMPUTER,
    DeviceCategory.SPEAKER,
    DeviceCategory.KEYBOARD,
    DeviceCategory.MOUSE,
    DeviceCategory.TV,
    DeviceCategory.CAR,
    DeviceCategory.UNKNOWN,
)

const val SAVE_NAME_MAX = 30

/**
 * "Save to My devices" (#108): a name and a type icon, both kept on the phone only. Also renames a
 * saved device, and is the sheet the device map's "name it" will reuse (#86).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveDeviceSheet(
    initialName: String,
    initialCategory: DeviceCategory,
    onSave: (name: String, category: DeviceCategory) -> Unit,
    onDismiss: () -> Unit,
    /** Over the find screen: a light blue sheet with white controls instead of the dark card (#152). */
    onFindScreen: Boolean = false,
) {
    if (!onFindScreen) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            SaveDeviceContent(initialName, initialCategory, onSave, onDismiss)
        }
        return
    }
    val blue = NearoTheme.colors.findFar.background
    val surface = lerp(blue, Color.White, FIND_SHEET_LIGHTEN)
    val scheme = MaterialTheme.colorScheme.copy(
        primary = Color.White,
        onPrimary = blue,
        primaryContainer = Color.White,
        onPrimaryContainer = blue,
        secondaryContainer = Color.White,
        onSecondaryContainer = blue,
        surface = surface,
        onSurface = Color.White,
        onSurfaceVariant = Color.White.copy(alpha = 0.8f),
        outline = Color.White.copy(alpha = 0.7f),
    )
    MaterialTheme(colorScheme = scheme) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = surface,
            contentColor = Color.White,
        ) {
            SaveDeviceContent(initialName, initialCategory, onSave, onDismiss)
        }
    }
}

private const val FIND_SHEET_LIGHTEN = 0.14f

@Composable
fun SaveDeviceContent(
    initialName: String,
    initialCategory: DeviceCategory,
    onSave: (name: String, category: DeviceCategory) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf(initialName.take(SAVE_NAME_MAX)) }
    // A type that is not on the grid (hearing aid, gamepad, …) starts on its nearest neighbour.
    var category by rememberSaveable { mutableStateOf(initialCategory.onSaveGrid()) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Text(stringResource(R.string.save_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.replace('\n', ' ').take(SAVE_NAME_MAX) },
            label = { Text(stringResource(R.string.save_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.save_type_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        SAVE_CATEGORIES.chunked(TYPES_PER_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { item -> TypeChoice(item, selected = item == category, onSelect = { category = item }) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.save_local_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.save_cancel))
            }
            Button(
                onClick = { onSave(name.trim(), category) },
                enabled = name.isNotBlank(),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.save_confirm), fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** One type icon; the chosen one sits on a filled circle. 44 dp touch target. */
@Composable
private fun TypeChoice(category: DeviceCategory, selected: Boolean, onSelect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val name = category.label()
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (selected) colors.primary else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(category.iconRes()),
            contentDescription = null,
            tint = if (selected) colors.onPrimary else colors.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

private fun DeviceCategory.onSaveGrid(): DeviceCategory = when (this) {
    in SAVE_CATEGORIES -> this
    DeviceCategory.HEARING_AID -> DeviceCategory.EARBUDS
    else -> DeviceCategory.UNKNOWN
}

/** What the locked sheet offers. [onWatchAd] is null while no rewarded ad is loaded: the button hides. */
data class SaveLockedActions(
    val onGetPremium: () -> Unit = {},
    val onWatchAd: (() -> Unit)? = null,
    val onDismiss: () -> Unit = {},
)

/**
 * Shown instead of the save sheet when a free user saves a second device (#108): Premium first,
 * then the rewarded 24 hours (#81), which is off once today's pass was used.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveLockedSheet(adUsedToday: Boolean, actions: SaveLockedActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        SaveLockedContent(adUsedToday, actions)
    }
}

@Composable
fun SaveLockedContent(adUsedToday: Boolean, actions: SaveLockedActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.save_locked_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.save_locked_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = actions.onGetPremium, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.paywall_buy), fontWeight = FontWeight.SemiBold)
        }
        val watchAd = actions.onWatchAd
        if (watchAd != null || adUsedToday) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { watchAd?.invoke() },
                enabled = watchAd != null && !adUsedToday,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.paywall_reward))
            }
            if (adUsedToday) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.save_locked_ad_used),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = actions.onDismiss) { Text(stringResource(R.string.save_locked_later)) }
        Spacer(Modifier.height(16.dp))
    }
}

private const val TYPES_PER_ROW = 7
