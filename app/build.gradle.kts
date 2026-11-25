import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Open-source licenses screen (#56): writes res/raw/aboutlibraries.json from the real dependencies.
    alias(libs.plugins.aboutlibraries.android)
}

// Values kept out of git (#56, #31, #35). Missing keys fall back to empty, and the screens hide the row.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// Release signing (#35): the keystore path and passwords come from local.properties or environment
// variables only, never from git. Without them the release build is simply left unsigned.
fun secret(property: String, environment: String): String? =
    localProperties.getProperty(property)?.takeIf { it.isNotBlank() }
        ?: System.getenv(environment)?.takeIf { it.isNotBlank() }

val signingStoreFile = secret("nearo.signing.storeFile", "NEARO_SIGNING_STORE_FILE")
val signingStorePassword = secret("nearo.signing.storePassword", "NEARO_SIGNING_STORE_PASSWORD")
val signingKeyAlias = secret("nearo.signing.keyAlias", "NEARO_SIGNING_KEY_ALIAS")
val signingKeyPassword = secret("nearo.signing.keyPassword", "NEARO_SIGNING_KEY_PASSWORD")
val hasReleaseSigning =
    listOf(signingStoreFile, signingStorePassword, signingKeyAlias, signingKeyPassword).all { it != null }

// A release must never ship Google's test ads or go out unsigned (#35): building or bundling it stops
// with the list of what is missing. Debug and benchmark builds keep the test IDs.
val admobTestPublisher = "ca-app-pub-3940256099942544"
val missingForRelease = listOf("appId", "banner", "interstitial", "rewarded")
    .map { "nearo.admob.$it" }
    .filter { key ->
        val value = localProperties.getProperty(key)
        value.isNullOrBlank() || value.startsWith(admobTestPublisher) || value.contains("XXXX")
    } + if (hasReleaseSigning) {
    emptyList()
} else {
    listOf("nearo.signing.* (storeFile, storePassword, keyAlias, keyPassword)")
}

gradle.taskGraph.whenReady {
    val buildsRelease = allTasks.any { task ->
        task.project == project && task.name.endsWith("Release") &&
            (task.name.startsWith("assemble") || task.name.startsWith("bundle") || task.name.startsWith("package"))
    }
    if (buildsRelease && missingForRelease.isNotEmpty()) {
        throw GradleException(
            "Release build stopped. Missing in local.properties: " + missingForRelease.joinToString(", ") +
                ". See docs/RELEASE.md.",
        )
    }
}

android {
    namespace = "com.gamenuri.btscanner24"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.gamenuri.btscanner24"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // AdMob (#31). Google's public test IDs unless local.properties has the real ones (release, #35).
        manifestPlaceholders["admobAppId"] =
            localProperties.getProperty("nearo.admob.appId", "ca-app-pub-3940256099942544~3347511713")
        for (unit in listOf("banner", "interstitial", "rewarded")) {
            val value = localProperties.getProperty("nearo.admob.$unit", "")
            buildConfigField("String", "ADMOB_${unit.uppercase()}", "\"$value\"")
        }

        buildConfigField("String", "SUPPORT_EMAIL", "\"${localProperties.getProperty("nearo.supportEmail", "")}\"")
        // GitHub Pages copy of docs/privacy (#57).
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"https://kywood.github.io/findBT/privacy/\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingStoreFile!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Installs next to the Play Store build on test phones; release applicationId is unchanged.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8 drops the unused part of material-icons-extended and friends; a much smaller dex
            // is the biggest cold-start win (#34).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // The existing upload key (the package stays com.gamenuri.btscanner24); null = unsigned.
            signingConfig = signingConfigs.findByName("release")
        }
        // Release code, debug signing, profileable: for measuring start-up on test phones (#34).
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isProfileable = true
            applicationIdSuffix = ".benchmark"
            versionNameSuffix = "-benchmark"
        }
    }
    sourceSets {
        // The benchmark build runs the debug demo (release code, R8), so motion is recorded and measured
        // as users get it, with demo devices only (#101).
        getByName("benchmark") {
            // AGP 9 built-in Kotlin compiles from the kotlin source set, not java.
            kotlin.directories.add("src/debug/java")
        }
    }
    androidResources {
        // Per-app language picker on Android 13+ (#29); locales come from the values-* folders.
        generateLocaleConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(projects.core.ads)
    implementation(projects.core.ble)
    implementation(projects.core.billing)
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.feature.devices)
    implementation(projects.feature.finder)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.scan)
    implementation(projects.feature.settings)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material.icons)
}
