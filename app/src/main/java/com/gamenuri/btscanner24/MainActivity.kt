package com.gamenuri.btscanner24

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.res.stringResource
import com.gamenuri.btscanner24.core.designsystem.theme.FindBTTheme
import com.gamenuri.btscanner24.feature.scan.ScanScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FindBTTheme {
                ScanScreen(title = stringResource(R.string.app_name))
            }
        }
    }
}
