package com.gamenuri.btscanner24.core.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Usage events and crash reports (#33). One instance lives in the Application ([Provider]). */
interface Analytics {
    fun log(event: AnalyticsEvent)

    /** Turns event and crash collection on or off; off until the app says otherwise. */
    fun setCollectionEnabled(enabled: Boolean)

    interface Provider {
        val analytics: Analytics
    }

    companion object {
        /**
         * Firebase when this build has it (a `google-services.json` was present at build time),
         * otherwise [None]: the app builds and runs the same without the file.
         */
        fun create(context: Context): Analytics =
            if (FirebaseApp.getApps(context).isEmpty()) None else FirebaseTracker(context.applicationContext)
    }

    /** Debug and demo builds, and builds without Firebase: nothing leaves the phone. */
    object None : Analytics {
        override fun log(event: AnalyticsEvent) = Unit

        override fun setCollectionEnabled(enabled: Boolean) = Unit
    }
}

private class FirebaseTracker(context: Context) : Analytics {
    private val analytics = FirebaseAnalytics.getInstance(context)
    private val crashlytics = FirebaseCrashlytics.getInstance()

    @Volatile private var enabled = false

    override fun log(event: AnalyticsEvent) {
        if (!enabled) return
        val bundle = Bundle()
        for ((key, value) in event.params) bundle.putString(key, value)
        analytics.logEvent(event.name, bundle)
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        this.enabled = enabled
        analytics.setAnalyticsCollectionEnabled(enabled)
        crashlytics.isCrashlyticsCollectionEnabled = enabled
    }
}
