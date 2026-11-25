package com.gamenuri.btscanner24.feature.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamenuri.btscanner24.core.billing.PremiumState
import com.gamenuri.btscanner24.core.billing.PremiumStore
import com.gamenuri.btscanner24.core.billing.RestoreResult
import com.gamenuri.btscanner24.core.designsystem.theme.BrandIconGradientBottom
import com.gamenuri.btscanner24.core.designsystem.theme.BrandIconGradientTop
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.core.model.DistanceUnit
import com.gamenuri.btscanner24.core.ui.LocalDistanceUnit
import com.gamenuri.btscanner24.core.ui.premium.PaywallActions
import com.gamenuri.btscanner24.core.ui.premium.PaywallSheet
import kotlinx.coroutines.launch

/** App facts the Settings screen shows or sends; given by `:app`, which owns BuildConfig. */
data class AppInfo(
    val versionName: String,
    val versionCode: Long,
    /** Empty until the real address is set in local.properties; the Contact row then hides. */
    val supportEmail: String,
    val privacyPolicyUrl: String,
)

/**
 * Ad consent (UMP, #31). Only shown where the law asks for it, so the row stays hidden while this
 * is null or [isRequired] is false.
 */
data class PrivacyOptions(val isRequired: Boolean, val show: () -> Unit)

/**
 * The opt-in rewarded ad for 24 hours of Premium (#81 ④). [show] plays it and calls back with the
 * day pass end (epoch ms) once it was watched to the end. The button hides while [isReady] is false.
 */
data class RewardedOffer(val isReady: Boolean, val show: (onDayPass: (until: Long) -> Unit) -> Unit)

/**
 * Recording where the user's devices were last detected (#178). Turning it on asks for the location
 * permission first where it is missing; turning it off asks whether the stored places go too.
 */
data class PlaceRecording(val isOn: Boolean, val onTurnOn: () -> Unit, val onTurnOff: (deletePlaces: Boolean) -> Unit)

data class SettingsActions(
    val onBack: () -> Unit = {},
    val onGetPremium: () -> Unit = {},
    val onRestore: () -> Unit = {},
    val onPrivacyOptions: () -> Unit = {},
    val onPrivacyPolicy: () -> Unit = {},
    val onContact: () -> Unit = {},
    val onLicenses: () -> Unit = {},
    val onDistanceUnit: (DistanceUnit) -> Unit = {},
    val onPlaceRecording: (Boolean) -> Unit = {},
)

/** ⋮ → Settings (#56, design settings-v3). No ads on this screen. */
@Composable
fun SettingsScreen(
    appInfo: AppInfo,
    onBack: () -> Unit,
    onLicenses: () -> Unit,
    modifier: Modifier = Modifier,
    onDistanceUnit: (DistanceUnit) -> Unit = {},
    privacyOptions: PrivacyOptions? = null,
    rewarded: RewardedOffer? = null,
    placeRecording: PlaceRecording? = null,
    /** Opens the paywall sheet right away (debug demo shots, later "Go Premium" links). */
    openPaywall: Boolean = false,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val store = (context.applicationContext as PremiumStore.Provider).premium
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(store))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(openPaywall) { if (openPaywall) viewModel.openPaywall() }

    val restored = stringResource(R.string.settings_restore_ok)
    val nothing = stringResource(R.string.settings_restore_none)
    val failed = stringResource(R.string.settings_restore_failed)
    val scope = rememberCoroutineScope()
    val dayPassOn = stringResource(R.string.settings_day_pass_on)
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { result ->
            snackbar.showSnackbar(
                when (result) {
                    RestoreResult.RESTORED -> restored
                    RestoreResult.NOTHING_TO_RESTORE -> nothing
                    RestoreResult.FAILED -> failed
                },
            )
        }
    }

    var askDeletePlaces by rememberSaveable { mutableStateOf(false) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Refused: the switch stays off, so it never claims a recording that cannot happen.
        if (granted) placeRecording?.onTurnOn?.invoke()
    }

    val subject = stringResource(
        R.string.settings_contact_subject,
        appInfo.versionName,
        appInfo.versionCode,
        Build.VERSION.RELEASE,
        Build.MODEL,
    )
    SettingsContent(
        appInfo = appInfo,
        premium = state.premium,
        showPrivacyOptions = privacyOptions?.isRequired == true,
        recordsPlace = placeRecording?.isOn,
        snackbar = snackbar,
        modifier = modifier,
        actions = SettingsActions(
            onBack = onBack,
            onGetPremium = viewModel::openPaywall,
            onRestore = viewModel::restore,
            onPrivacyOptions = { privacyOptions?.show?.invoke() },
            onPrivacyPolicy = { context.openUri(Intent(Intent.ACTION_VIEW, Uri.parse(appInfo.privacyPolicyUrl))) },
            // Version and phone model only: never scan data (#19).
            onContact = {
                val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${appInfo.supportEmail}"))
                    .putExtra(Intent.EXTRA_SUBJECT, subject)
                context.openUri(mail)
            },
            onLicenses = onLicenses,
            onDistanceUnit = onDistanceUnit,
            onPlaceRecording = { on ->
                when {
                    !on -> askDeletePlaces = true
                    context.hasLocationAccess() -> placeRecording?.onTurnOn?.invoke()
                    else -> askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            },
        ),
    )

    if (askDeletePlaces) {
        AlertDialog(
            onDismissRequest = { askDeletePlaces = false },
            title = { Text(stringResource(R.string.settings_place_off_title)) },
            text = { Text(stringResource(R.string.settings_place_off_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        askDeletePlaces = false
                        placeRecording?.onTurnOff?.invoke(true)
                    },
                ) { Text(stringResource(R.string.settings_place_off_delete)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        askDeletePlaces = false
                        placeRecording?.onTurnOff?.invoke(false)
                    },
                ) { Text(stringResource(R.string.settings_place_off_keep)) }
            },
        )
    }

    if (state.showPaywall) {
        PaywallSheet(
            price = state.premium.formattedPrice,
            isPending = state.premium.isPending,
            actions = PaywallActions(
                onBuy = { activity?.let(viewModel::buy) },
                onRestore = viewModel::restore,
                onClose = viewModel::closePaywall,
                // Only when an ad is loaded and today's day pass is still unused (#81).
                onWatchAd = rewarded?.takeIf { it.isReady && state.premium.dayPassAvailable }?.let { offer ->
                    {
                        offer.show { until ->
                            viewModel.closePaywall()
                            scope.launch { snackbar.showSnackbar(dayPassOn.format(context.formatUntil(until))) }
                        }
                    }
                },
            ),
        )
    }
}

/** "Nov 25, 3:20 PM" in the phone's language and clock style. */
private fun Context.formatUntil(until: Long): String = DateUtils.formatDateTime(
    this,
    until,
    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
)

/** Approximate is enough: places are kept to about 100 m anyway. */
private fun Context.hasLocationAccess(): Boolean =
    listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION).any {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

private fun Context.openUri(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No browser or mail app: nothing sensible to open.
    }
}

/** Stateless; also used by previews and the debug demo mode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    appInfo: AppInfo,
    premium: PremiumState,
    showPrivacyOptions: Boolean,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    /** Null hides the row, e.g. where the caller has no last-seen records to switch. */
    recordsPlace: Boolean? = null,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            PremiumCard(premium, actions.onGetPremium)
            // Still offered during a 24 h day pass: it is not a purchase (#92).
            if (!premium.isPurchased) {
                SettingsRow(Icons.Outlined.Restore, R.string.settings_restore, onClick = actions.onRestore)
            }
            SectionHeader(R.string.settings_section_display)
            DistanceUnitRow(LocalDistanceUnit.current, actions.onDistanceUnit)
            SectionHeader(R.string.settings_section_privacy)
            if (recordsPlace != null) PlaceRecordingRow(recordsPlace, actions.onPlaceRecording)
            if (showPrivacyOptions) {
                SettingsRow(
                    icon = Icons.Outlined.PrivacyTip,
                    title = R.string.settings_privacy_options,
                    supporting = stringResource(R.string.settings_privacy_options_body),
                    trailing = Trailing.CHEVRON,
                    onClick = actions.onPrivacyOptions,
                )
            }
            SettingsRow(
                icon = Icons.Outlined.Lock,
                title = R.string.settings_privacy_policy,
                trailing = Trailing.EXTERNAL,
                onClick = actions.onPrivacyPolicy,
            )
            SectionHeader(R.string.settings_section_about)
            if (appInfo.supportEmail.isNotBlank()) {
                SettingsRow(
                    icon = Icons.Outlined.Mail,
                    title = R.string.settings_contact,
                    trailing = Trailing.EXTERNAL,
                    onClick = actions.onContact,
                )
            }
            SettingsRow(
                icon = Icons.Outlined.Code,
                title = R.string.settings_licenses,
                trailing = Trailing.CHEVRON,
                onClick = actions.onLicenses,
            )
            SettingsRow(
                icon = Icons.Outlined.Info,
                title = R.string.settings_version,
                supporting = stringResource(R.string.settings_version_value, appInfo.versionName, appInfo.versionCode),
            )
        }
    }
}

/** Free: brand gradient card with the localized price. Premium: quiet card with a mint check. */
@Composable
private fun PremiumCard(premium: PremiumState, onGetPremium: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val cardModifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
    if (premium.isPremium) {
        Row(
            modifier = cardModifier
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CheckCircle, null, tint = NearoTheme.colors.signalStrong, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                val until = premium.dayPassUntil?.takeIf { !premium.isPurchased }
                Text(
                    stringResource(
                        if (until != null) R.string.settings_day_pass_title else R.string.settings_premium_active,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (until != null) {
                        stringResource(R.string.settings_day_pass_until, LocalContext.current.formatUntil(until))
                    } else {
                        stringResource(R.string.settings_premium_active_body)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    Column(
        modifier = cardModifier
            .background(Brush.linearGradient(listOf(BrandIconGradientTop, BrandIconGradientBottom)), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.settings_premium_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        Text(
            stringResource(R.string.settings_premium_body),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
        Spacer(Modifier.size(8.dp))
        Button(
            onClick = onGetPremium,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = BrandIconGradientBottom),
        ) {
            val price = premium.formattedPrice
            Text(
                text = if (price != null) {
                    stringResource(R.string.settings_premium_buy, price)
                } else {
                    stringResource(R.string.settings_premium_buy_no_price)
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
    )
}

private enum class Trailing { NONE, CHEVRON, EXTERNAL }

/** M3 list row: 52 dp tall, 64 dp with a second line; 24 dp icon; 20 dp side padding. */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: Int,
    supporting: String? = null,
    trailing: Trailing = Trailing.NONE,
    onClick: (() -> Unit)? = null,
) {
    val variant = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = if (supporting != null) 64.dp else 52.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = variant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = variant)
            }
        }
        when (trailing) {
            Trailing.NONE -> Unit
            Trailing.CHEVRON -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = variant)
            Trailing.EXTERNAL -> Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                stringResource(R.string.settings_opens_outside),
                tint = variant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Off until the user turns it on here or from a device's last-seen sheet (#178). */
@Composable
private fun PlaceRecordingRow(isOn: Boolean, onChange: (Boolean) -> Unit) {
    val variant = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = isOn, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 64.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Place, null, tint = variant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_place_recording), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_place_recording_body),
                style = MaterialTheme.typography.bodyMedium,
                color = variant,
            )
        }
        Spacer(Modifier.width(12.dp))
        // The whole row is the switch for TalkBack and touch.
        Switch(checked = isOn, onCheckedChange = null)
    }
}

/** Metres or feet (#83). Shows the unit in use, which is the region's until one is picked here. */
@Composable
private fun DistanceUnitRow(selected: DistanceUnit, onSelect: (DistanceUnit) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val variant = MaterialTheme.colorScheme.onSurfaceVariant
        Icon(Icons.Outlined.Straighten, null, tint = variant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            stringResource(R.string.settings_distance_unit),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        SingleChoiceSegmentedButtonRow {
            DistanceUnit.entries.forEachIndexed { index, unit ->
                SegmentedButton(
                    selected = unit == selected,
                    onClick = { onSelect(unit) },
                    shape = SegmentedButtonDefaults.itemShape(index, DistanceUnit.entries.size),
                    icon = {},
                ) {
                    Text(
                        stringResource(
                            when (unit) {
                                DistanceUnit.METERS -> R.string.settings_distance_unit_meters
                                DistanceUnit.FEET -> R.string.settings_distance_unit_feet
                            },
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private val previewInfo = AppInfo(
    versionName = "2.0.0",
    versionCode = 200,
    supportEmail = "support@example.com",
    privacyPolicyUrl = "https://example.com/privacy",
)

@Preview(name = "Free", showBackground = true)
@Preview(name = "Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsFreePreview() {
    NearoTheme {
        SettingsContent(
            appInfo = previewInfo,
            premium = PremiumState(formattedPrice = "$4.99"),
            showPrivacyOptions = true,
            actions = SettingsActions(),
        )
    }
}

@Preview(name = "Premium", showBackground = true)
@Composable
private fun SettingsPremiumPreview() {
    NearoTheme {
        SettingsContent(
            appInfo = previewInfo,
            premium = PremiumState(isPremium = true),
            showPrivacyOptions = false,
            actions = SettingsActions(),
        )
    }
}
