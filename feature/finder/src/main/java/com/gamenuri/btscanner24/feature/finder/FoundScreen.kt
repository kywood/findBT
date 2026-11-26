package com.gamenuri.btscanner24.feature.finder

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.gamenuri.btscanner24.core.designsystem.theme.FoundColors
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import com.gamenuri.btscanner24.core.model.DeviceCategory
import com.gamenuri.btscanner24.core.model.DeviceLabel
import com.gamenuri.btscanner24.core.model.NameSource
import com.gamenuri.btscanner24.core.ui.arrow3d.DeviceModel
import com.gamenuri.btscanner24.core.ui.arrow3d.FindScene3d
import com.gamenuri.btscanner24.core.ui.arrow3d.FindSceneSpec
import com.gamenuri.btscanner24.core.ui.text
import kotlinx.coroutines.delay

/**
 * "Found it!" (design find-flow-3d v1.1, #76): the white check medal rises from a mint floor ring in
 * the same 3D scene as the find screen, then the device and how long it took; Done stays at the
 * bottom. The drawn check (#70) is only shown where the 3D scene cannot run.
 * Saving to My devices and the in-app review prompt come with #32/#9 storage and the Play libraries.
 */
@Composable
fun FoundContent(
    label: DeviceLabel,
    elapsedSeconds: Int,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** After the water rise (#90): pop the check once (1 → 1.12 → 1) with one haptic tick. */
    popCheckAfterMs: Int? = null,
    /** The kind of device that was found: its 3D model leaves as the medal rises (#133). */
    category: DeviceCategory = DeviceCategory.UNKNOWN,
) {
    val colors = NearoTheme.colors.found
    val checkScale = remember { Animatable(1f) }
    val haptics = LocalHapticFeedback.current
    if (popCheckAfterMs != null) {
        LaunchedEffect(Unit) {
            delay(popCheckAfterMs.toLong())
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            checkScale.animateTo(CHECK_POP_SCALE, tween(CHECK_POP_MS / 2, easing = FastOutSlowInEasing))
            checkScale.animateTo(1f, tween(CHECK_POP_MS / 2, easing = FastOutSlowInEasing))
        }
    }
    val pop = Modifier.graphicsLayer {
        scaleX = checkScale.value
        scaleY = checkScale.value
    }
    var sceneFailed by remember { mutableStateOf(false) }
    val scene3d = LocalArFindScene.current && !sceneFailed && !LocalInspectionMode.current
    val heroSlot = remember { SceneSlot() }
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        val scale = Settings.Global.ANIMATOR_DURATION_SCALE
        Settings.Global.getFloat(context.contentResolver, scale, 1f) == 0f
    }
    CompositionLocalProvider(LocalContentColor provides colors.content) {
        Box(
            modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(colors.backgroundTop, colors.backgroundBottom))),
        ) {
            // The same full-width 3D layer as the find screen; the layout below keeps its place free.
            if (scene3d) {
                FindSceneLayer(heroSlot, pop) { centerY, _, _ ->
                    FindScene3d(
                        spec = FindSceneSpec(
                            found = true,
                            model = DeviceModel.of(category),
                            arrowColor = Color.White,
                            floorColor = colors.content,
                            accentColor = colors.rings,
                            reduceMotion = reduceMotion,
                            fullBleed = true,
                            centerY = centerY,
                        ),
                        modifier = Modifier.fillMaxSize(),
                        onUnavailable = { sceneFailed = true },
                    )
                }
            }
            FoundLayout(label, elapsedSeconds, onDone, colors) {
                if (scene3d) {
                    Box(
                        Modifier
                            .weight(1f, fill = false)
                            .sizeIn(maxWidth = HERO_SLOT, maxHeight = HERO_SLOT)
                            .aspectRatio(1f)
                            .sceneSlot(heroSlot),
                    )
                } else {
                    CheckHero(colors, pop)
                }
            }
        }
    }
}

/** The hero's place, then what was found and how long it took; Done stays at the bottom. */
@Composable
private fun FoundLayout(
    label: DeviceLabel,
    elapsedSeconds: Int,
    onDone: () -> Unit,
    colors: FoundColors,
    hero: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            hero()
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
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.buttonContainer,
                contentColor = colors.buttonContent,
            ),
        ) {
            Text(stringResource(R.string.found_done), fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 84 dp circle with a check, inside two rings (110 and 150 dp) at 25 % of the ring colour. */
@Composable
private fun CheckHero(colors: FoundColors, modifier: Modifier = Modifier) {
    val content = colors.rings
    Box(modifier = modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val ring = content.copy(alpha = 0.25f)
            val stroke = Stroke(1.5.dp.toPx())
            drawCircle(content.copy(alpha = 0.06f), radius = size.minDimension / 2)
            drawCircle(ring, radius = size.minDimension / 2 - stroke.width / 2, style = stroke)
            drawCircle(content.copy(alpha = 0.06f), radius = 55.dp.toPx())
            drawCircle(ring, radius = 55.dp.toPx(), style = stroke)
        }
        Surface(
            shape = CircleShape,
            color = colors.heroCircle,
            shadowElevation = 3.dp,
            modifier = Modifier.size(84.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.check,
                    modifier = Modifier.size(44.dp),
                )
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

private val HERO_SLOT = 280.dp
private const val CHECK_POP_SCALE = 1.12f
private const val CHECK_POP_MS = 260
