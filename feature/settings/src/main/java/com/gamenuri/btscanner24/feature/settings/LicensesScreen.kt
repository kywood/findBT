package com.gamenuri.btscanner24.feature.settings

import androidx.annotation.RawRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

/**
 * Open-source licenses (#56). [librariesRes] is the `aboutlibraries.json` that the AboutLibraries
 * plugin generates in `:app` at build time, so the list always matches the shipped dependencies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(@RawRes librariesRes: Int, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val libraries by produceLibraries(librariesRes)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_licenses)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        LibrariesContainer(libraries, Modifier.padding(padding).fillMaxSize())
    }
}
