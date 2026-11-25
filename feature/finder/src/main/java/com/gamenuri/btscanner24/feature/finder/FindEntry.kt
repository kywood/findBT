package com.gamenuri.btscanner24.feature.finder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.gamenuri.btscanner24.core.designsystem.component.WaterMotion
import com.gamenuri.btscanner24.core.designsystem.component.waterCover
import com.gamenuri.btscanner24.core.designsystem.theme.NearoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * The water the find screen comes in with, owned by the screen that opens it (#107). Navigating to
 * the find screen and drawing its first frame takes 130 to 600 ms on a Galaxy A50, so the water
 * cannot wait for that: [start] sets it off in the tap itself, [FindEntryCover] shows it over the
 * list, and the find screen takes the same water over once it has drawn.
 */
@Stable
class FindEntryWater internal constructor(
    private val scope: CoroutineScope,
    private val reduceMotion: () -> Boolean,
) {
    internal val motion = WaterMotion()

    /** True from the tap until the find screen draws under this water itself. */
    internal var covering by mutableStateOf(false)

    /** tan(roll) of the phone; the find screen feeds it in once it reads the sensor. */
    internal var slope: () -> Float = { 0f }

    /** Call in the tap that opens the find screen, before navigating. [tapX]: 0..1 across the screen. */
    fun start(tapX: Float) {
        // A second tap while it rises over the list would start the water over from the bottom.
        if (covering && motion.isMoving) return
        covering = true
        // Undispatched: the water is rising by the time the tap handler returns.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            motion.rise(kickAt = tapX, reduceMotion = reduceMotion(), slope = { slope() })
        }
    }
}

@Composable
fun rememberFindEntryWater(): FindEntryWater {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    return remember(scope, context) { FindEntryWater(scope) { context.animationsOff() } }
}

/**
 * Draws [entry]'s water over everything under it until the find screen has drawn. Always in
 * composition, so the water knows its size before the first tap; it draws nothing at rest and takes
 * no touches.
 */
@Composable
fun FindEntryCover(entry: FindEntryWater, modifier: Modifier = Modifier) {
    val cover = NearoTheme.colors.findSearching.brush()
    Box(modifier.fillMaxSize().waterCover(entry.motion, cover) { entry.covering })
}
