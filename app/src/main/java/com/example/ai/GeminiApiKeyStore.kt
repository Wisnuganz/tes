package com.example.ai

import android.content.Context

object GeminiApiKeyStore {
    private const val PREFS_NAME = "cerdas_ai_api_prefs"
    private const val KEY_API_KEY = "user_gemini_api_key"
    private const val KEY_VERIFIED_MODEL = "user_gemini_verified_model"

    @Volatile
    private var cachedKey: String? = null

    @Volatile
    private var cachedModel: String? = null

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (cachedKey == null) {
            cachedKey = prefs.getString(KEY_API_KEY, "").orEmpty().trim()
        }
        if (cachedModel == null) {
            cachedModel = prefs.getString(KEY_VERIFIED_MODEL, "").orEmpty().trim()
        }
    }

    fun getApiKey(context: Context? = null): String {
        if (cachedKey == null && context != null) {
            init(context)
        }
        return cachedKey.orEmpty().trim()
    }

    fun getVerifiedModel(context: Context? = null): String {
        if (cachedModel == null && context != null) {
            init(context)
        }
        return cachedModel.orEmpty().trim()
    }

    fun saveApiKey(context: Context, rawKey: String, verifiedModel: String = "") {
        val cleanKey = rawKey.trim()
        val cleanModel = verifiedModel.trim()
        cachedKey = cleanKey
        if (cleanModel.isNotEmpty()) {
            cachedModel = cleanModel
        }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_API_KEY, cleanKey)
            .apply {
                if (cleanModel.isNotEmpty()) {
                    putString(KEY_VERIFIED_MODEL, cleanModel)
                }
            }
            .commit()
    }

    fun clearApiKey(context: Context) {
        cachedKey = ""
        cachedModel = ""
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_API_KEY)
            .remove(KEY_VERIFIED_MODEL)
            .commit()
    }

    fun maskKey(key: String): String {
        val k = key.trim()
        if (k.length <= 10) return if (k.isEmpty()) "" else "••••••"
        return "${k.take(6)}••••${k.takeLast(4)}"
    }
}
