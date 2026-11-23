package com.gamenuri.btscanner24.feature.onboarding

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.gamenuri.btscanner24.core.ble.BleScanner
import com.gamenuri.btscanner24.core.designsystem.component.IllustratedMessage
import com.gamenuri.btscanner24.core.designsystem.component.IllustrationTone
import com.gamenuri.btscanner24.core.designsystem.icon.NearoIcons
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.core.ui.permission.openAppSettings
import com.gamenuri.btscanner24.core.ui.permission.rememberBlePermissionState

/** The three onboarding pages (design screens-rest-v3 §4). */
enum class OnboardingPage { INTRO, PERMISSION, DENIED }

data class OnboardingActions(
    val onGetStarted: () -> Unit = {},
    val onContinue: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onTryAgain: () -> Unit = {},
)

/**
 * First launch (#30): one value page, then why we need "Nearby devices" right before the system
 * dialog. Denied leads to a Settings page. [onDone] runs once the permission is granted.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    var page by rememberSaveable { mutableStateOf(OnboardingPage.INTRO) }
    // The grant can be reported twice (dialog result and resume); leave only once.
    var finished by remember { mutableStateOf(false) }
    val finish = {
        if (!finished) {
            finished = true
            onDone()
        }
    }
    val permission = rememberBlePermissionState(BleScanner.requiredPermissions(), onGranted = finish)
    val context = LocalContext.current

    // Back from the app's system settings page with the permission turned on.
    LifecycleResumeEffect(permission.isGranted, page) {
        if (page != OnboardingPage.INTRO && permission.isGranted) finish()
        onPauseOrDispose { }
    }

    OnboardingContent(
        // The system dialog was answered with "Don't allow".
        page = if (page == OnboardingPage.PERMISSION && permission.showRationale) OnboardingPage.DENIED else page,
        modifier = modifier,
        actions = OnboardingActions(
            onGetStarted = {
                // Updating users usually granted it in the old app already.
                if (permission.isGranted) finish() else page = OnboardingPage.PERMISSION
            },
            onContinue = permission::request,
            onOpenSettings = { openAppSettings(context) },
            // Opens Settings by itself once the system stops showing the dialog.
            onTryAgain = permission::request,
        ),
    )
}

/** Stateless pages; also used by the debug demo mode for screenshots. */
@Composable
fun OnboardingContent(
    page: OnboardingPage,
    actions: OnboardingActions,
    modifier: Modifier = Modifier,
    // Android 11 and older ask for location instead of "Nearby devices".
    legacyLocation: Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                when (page) {
                    OnboardingPage.INTRO -> IllustratedMessage(
                        icon = NearoIcons.PinSignal,
                        title = stringResource(R.string.onboarding_intro_title),
                        body = stringResource(R.string.onboarding_intro_body),
                    )
                    OnboardingPage.PERMISSION -> IllustratedMessage(
                        icon = NearoIcons.NearbySignal,
                        title = stringResource(
                            if (legacyLocation) {
                                R.string.onboarding_permission_title_legacy
                            } else {
                                R.string.onboarding_permission_title
                            },
                        ),
                        body = stringResource(
                            if (legacyLocation) {
                                R.string.onboarding_permission_body_legacy
                            } else {
                                R.string.onboarding_permission_body
                            },
                        ),
                    ) {
                        Spacer(Modifier.height(8.dp))
                        CheckLine(R.string.onboarding_check_location)
                        CheckLine(R.string.onboarding_check_local)
                        CheckLine(R.string.onboarding_check_stops)
                    }
                    OnboardingPage.DENIED -> IllustratedMessage(
                        icon = NearoIcons.NearbySignal,
                        title = stringResource(R.string.onboarding_denied_title),
                        body = stringResource(
                            if (legacyLocation) {
                                R.string.onboarding_denied_body_legacy
                            } else {
                                R.string.onboarding_denied_body
                            },
                        ),
                        tone = IllustrationTone.NEUTRAL,
                    )
                }
            }
            Footer(page, actions)
        }
    }
}

@Composable
private fun CheckLine(text: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_check_mark),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Footer(page: OnboardingPage, actions: OnboardingActions) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (page != OnboardingPage.DENIED) {
            PageDots(selected = page.ordinal)
            Spacer(Modifier.height(8.dp))
        }
        val (label, onClick) = when (page) {
            OnboardingPage.INTRO -> R.string.onboarding_get_started to actions.onGetStarted
            OnboardingPage.PERMISSION -> R.string.onboarding_continue to actions.onContinue
            OnboardingPage.DENIED -> R.string.onboarding_open_settings to actions.onOpenSettings
        }
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)) {
            Text(stringResource(label), fontWeight = FontWeight.SemiBold)
        }
        if (page == OnboardingPage.DENIED) {
            TextButton(onClick = actions.onTryAgain, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_try_again), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PageDots(selected: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(2) { index ->
            val scheme = MaterialTheme.colorScheme
            val color = if (index == selected) scheme.primary else scheme.outlineVariant
            Box(Modifier.size(7.dp).background(color, CircleShape))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingPermissionPreview() {
    NearoTheme { OnboardingContent(OnboardingPage.PERMISSION, OnboardingActions(), legacyLocation = false) }
}
