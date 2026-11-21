package com.example.firebase

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

private const val FALLBACK_APP_ID = "1:1089453676878:android:f145cc9d3078718ce9261f"
private const val FALLBACK_API_KEY = "AIzaSyAjTwvkPdNslUWQj3-KZxxEYlVVShuReZ4"
private const val FALLBACK_PROJECT_ID = "gen-lang-client-0354368628"
private const val FALLBACK_GCM_SENDER_ID = "1089453676878"
private const val FALLBACK_STORAGE_BUCKET = "gen-lang-client-0354368628.firebasestorage.app"
private const val FALLBACK_RECAPTCHA_SITE_KEY = "6LfwkSkuAAAAAI57Xe0-RwyNQzH9PvRArOFMW3JB"

private val initLock = Any()

@Volatile
private var isProviderInstalled = false

@Volatile
private var isTokenWarmupAttempted = false

fun ensureFirebaseAppInitialized(context: Context): Boolean {
    val appContext = context.applicationContext ?: context
    if (FirebaseApp.getApps(appContext).isNotEmpty()) {
        return true
    }

    val initializedFromRes = FirebaseApp.initializeApp(appContext)
    if (initializedFromRes != null || FirebaseApp.getApps(appContext).isNotEmpty()) {
        return true
    }

    val fallbackOptions = FirebaseOptions.Builder()
        .setApplicationId(FALLBACK_APP_ID)
        .setApiKey(FALLBACK_API_KEY)
        .setProjectId(FALLBACK_PROJECT_ID)
        .setGcmSenderId(FALLBACK_GCM_SENDER_ID)
        .setStorageBucket(FALLBACK_STORAGE_BUCKET)
        .build()

    val initializedFallback = FirebaseApp.initializeApp(appContext, fallbackOptions)
    Log.w(TAG, "FirebaseApp initialized using fallback FirebaseOptions (google-services resources not found).")
    return initializedFallback != null || FirebaseApp.getApps(appContext).isNotEmpty()
}

fun configureAppCheck(context: Context, intent: Intent?) {
    synchronized(initLock) {
        try {
            ensureFirebaseAppInitialized(context)

            if (BuildConfig.DEBUG) {
                val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)

                if (!debugToken.isNullOrEmpty()) {
                    val persistenceKey = FirebaseApp.getInstance().persistenceKey
                    val prefs = context.getSharedPreferences(
                        "$DEBUG_STORE_PREFIX$persistenceKey",
                        Context.MODE_PRIVATE
                    )
                    // Must use commit() synchronously before installing DebugAppCheckProviderFactory.
                    prefs.edit()
                        .putString(DEBUG_SECRET_KEY, debugToken)
                        .commit()
                    Log.d(TAG, "App Check debug secret configured successfully.")
                } else if (!isProviderInstalled) {
                    Log.w(TAG, "No debug token found in Intent extra.")
                }

                if (!isProviderInstalled) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                    isProviderInstalled = true
                    Log.d(TAG, "DebugAppCheckProviderFactory installed.")
                }
            } else {
                if (isProviderInstalled) {
                    return
                }

                val resId = context.resources.getIdentifier(
                    RECAPTCHA_SITE_KEY_RES,
                    "string",
                    context.packageName
                )
                val resourceSiteKey = if (resId != 0) context.getString(resId) else ""
                val siteKey = resourceSiteKey.ifBlank { FALLBACK_RECAPTCHA_SITE_KEY }

                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    isProviderInstalled = true
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    Log.e(TAG, "recaptcha_site_key is blank and no fallback site key is available.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase App Check", e)
        }
    }
}

fun warmUpAppCheckTokenIfNeeded() {
    if (!isProviderInstalled || isTokenWarmupAttempted) return
    isTokenWarmupAttempted = true
    try {
        FirebaseAppCheck.getInstance().getAppCheckToken(false)
            .addOnSuccessListener { token ->
                Log.d(TAG, "App Check token cached in foreground (len=${token.token.length})")
            }
            .addOnFailureListener { err ->
                Log.w(TAG, "Foreground App Check warm-up failed: ${err.localizedMessage}")
            }
    } catch (e: Exception) {
        Log.w(TAG, "Failed to warm up App Check token", e)
    }
}
