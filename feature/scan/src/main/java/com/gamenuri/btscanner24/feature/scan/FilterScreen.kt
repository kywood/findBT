package com.gamenuri.btscanner24.feature.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.core.model.CategoryGroup
import com.gamenuri.btscanner24.core.model.DeviceFilter
import com.gamenuri.btscanner24.core.model.DistanceBucket
import com.gamenuri.btscanner24.core.model.SignalLevel
import com.gamenuri.btscanner24.core.ui.R as UiR
import com.gamenuri.btscanner24.core.ui.SignalBars
import com.gamenuri.btscanner24.core.ui.label

/**
 * Full-height filter screen (#85, design nearby-filter-v1 §4). Edits a copy of [current]; "Show N
 * devices" applies it, ✕ and system back throw the edits away. [countFor] gives the live result count.
 */
@Composable
internal fun FilterDialog(
    current: DeviceFilter,
    census: ScanCensus,
    countFor: (DeviceFilter) -> Int,
    onApply: (DeviceFilter) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var draft by remember { mutableStateOf(current) }
        FilterContent(
            draft = draft,
            census = census,
            resultCount = countFor(draft),
            onChange = { draft = it },
            onApply = { onApply(draft) },
            onClose = onClose,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterContent(
    draft: DeviceFilter,
    census: ScanCensus,
    resultCount: Int,
    onChange: (DeviceFilter) -> Unit,
    onApply: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.filter_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.filter_close))
                    }
                },
                actions = {
                    TextButton(onClick = { onChange(DeviceFilter()) }, enabled = draft != DeviceFilter()) {
                        Text(stringResource(R.string.filter_reset))
                    }
                },
            )
        },
        bottomBar = { ApplyBar(resultCount, onApply) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Section(R.string.filter_section_show)
            SwitchRow(
                title = stringResource(R.string.filter_show_other),
                body = stringResource(R.string.filter_show_other_body, census.other),
                checked = draft.showOther,
                onToggle = { onChange(draft.copy(showOther = !draft.showOther)) },
            )
            SwitchRow(
                title = stringResource(R.string.filter_show_unnamed),
                checked = draft.showUnnamed,
                onToggle = { onChange(draft.copy(showUnnamed = !draft.showUnnamed)) },
            )
            SwitchRow(
                title = stringResource(R.string.filter_hide_gone),
                checked = draft.hideGone,
                onToggle = { onChange(draft.copy(hideGone = !draft.hideGone)) },
            )

            Section(R.string.filter_types)
            ChipGroup(CategoryGroup.entries, draft.types, { it.label() }) { onChange(draft.copy(types = it)) }

            Section(R.string.filter_distance)
            ChipGroup(DistanceBucket.entries, draft.distances, { stringResource(it.labelRes()) }) {
                onChange(draft.copy(distances = it))
            }

            Section(R.string.filter_signal)
            SignalChips(draft.signals) { onChange(draft.copy(signals = it)) }

            // With a single maker the section can't narrow anything (#100).
            if (census.makers.size >= MIN_MAKERS) {
                Section(R.string.filter_maker)
                MakerChips(census.makers, draft.manufacturers) { onChange(draft.copy(manufacturers = it)) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ApplyBar(resultCount: Int, onApply: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider()
            Button(
                onClick = onApply,
                enabled = resultCount > 0,
                modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp),
            ) {
                Text(
                    if (resultCount > 0) {
                        pluralStringResource(R.plurals.filter_show_results, resultCount, resultCount)
                    } else {
                        stringResource(R.string.filter_no_results)
                    },
                )
            }
        }
    }
}

@Composable
private fun Section(title: Int) {
    Text(
        text = stringResource(title),
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onToggle: () -> Unit, body: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (body != null) 64.dp else 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Multi-select chips; nothing selected means "no limit". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipGroup(
    options: List<T>,
    selected: Set<T>,
    label: @Composable (T) -> String,
    onChange: (Set<T>) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            SelectChip(
                selected = option in selected,
                onClick = { onChange(if (option in selected) selected - option else selected + option) },
                label = label(option),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignalChips(selected: Set<SignalLevel>, onChange: (Set<SignalLevel>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SignalLevel.entries.forEach { level ->
            val text = stringResource(level.labelRes())
            val isSelected = level in selected
            FilterChip(
                selected = isSelected,
                onClick = { onChange(if (isSelected) selected - level else selected + level) },
                label = { Text(text) },
                // A tick when chosen, like the other chips; the bars otherwise. The chip text already says
                // the level, so the bars stay silent for TalkBack.
                leadingIcon = {
                    if (isSelected) CheckIcon() else SignalBars(level = level, label = "")
                },
                colors = selectChipColors(),
            )
        }
    }
}

/** M3 selected filter chip: secondary container and a leading tick, so a choice never reads as disabled (#100). */
@Composable
private fun SelectChip(selected: Boolean, onClick: () -> Unit, label: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { CheckIcon() }
        } else {
            null
        },
        colors = selectChipColors(),
    )
}

@Composable
private fun CheckIcon() {
    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
}

@Composable
private fun selectChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
)

/** The most common makers in the scan with their counts; the rest behind "+ N more" (#85). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MakerChips(makers: List<MakerCount>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // A chosen maker stays visible even when it is not among the first few.
    val shown = if (expanded) makers else makers.filterIndexed { i, m -> i < MAKERS_SHOWN || m.name in selected }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { maker ->
            SelectChip(
                selected = maker.name in selected,
                onClick = {
                    onChange(if (maker.name in selected) selected - maker.name else selected + maker.name)
                },
                label = stringResource(R.string.filter_maker_count, maker.name, maker.count),
            )
        }
        val hidden = makers.size - shown.size
        if (hidden > 0) {
            TextButton(onClick = { expanded = true }) { Text(stringResource(R.string.filter_maker_more, hidden)) }
        }
    }
}

private fun DistanceBucket.labelRes(): Int = when (this) {
    DistanceBucket.WITHIN_5_M -> R.string.filter_distance_within_5
    DistanceBucket.FIVE_TO_TEN_M -> R.string.filter_distance_5_10
    DistanceBucket.OVER_10_M -> R.string.filter_distance_over_10
}

private fun SignalLevel.labelRes(): Int = when (this) {
    SignalLevel.STRONG -> UiR.string.signal_strong
    SignalLevel.MEDIUM -> UiR.string.signal_medium
    SignalLevel.WEAK -> UiR.string.signal_weak
}

private const val MAKERS_SHOWN = 5
private const val MIN_MAKERS = 2

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun FilterPreview() {
    NearoTheme {
        FilterContent(
            draft = DeviceFilter(types = setOf(CategoryGroup.EARBUDS), manufacturers = setOf("Samsung")),
            census = ScanCensus(
                personal = 8,
                other = 23,
                makers = listOf(
                    MakerCount("Apple", 12),
                    MakerCount("Samsung", 8),
                    MakerCount("Xiaomi", 5),
                    MakerCount("Microsoft", 4),
                    MakerCount("Google", 3),
                    MakerCount("Sony", 2),
                ),
            ),
            resultCount = 2,
            onChange = {},
            onApply = {},
            onClose = {},
        )
    }
}
