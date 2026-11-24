plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.gamenuri.btscanner24.core.billing"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(projects.core.model)
    implementation(libs.play.billing)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
