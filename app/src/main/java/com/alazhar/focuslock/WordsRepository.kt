package com.alazhar.focuslock

import android.content.Context
import android.content.SharedPreferences

object WordsRepository {
    private const val PREFS_NAME = "focuslock_prefs"
    private const val KEY_WORDS = "blocked_words"
    private const val KEY_ENABLED = "service_enabled"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getWords(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_WORDS, emptySet()) ?: emptySet()

    fun setWords(context: Context, words: Set<String>) {
        prefs(context).edit().putStringSet(KEY_WORDS, words).apply()
    }

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
