package com.alazhar.focuslock

import android.content.Context
import android.content.SharedPreferences

object WordsRepository {
    private const val PREFS_NAME = "focuslock_prefs"
    private const val KEY_WORDS = "blocked_words"
    private const val KEY_BLOCKED_PREFIX = "blocked_until_"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getWords(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_WORDS, emptySet()) ?: emptySet()

    fun setWords(context: Context, words: Set<String>) {
        prefs(context).edit().putStringSet(KEY_WORDS, words).apply()
    }

    fun blockApp(context: Context, packageName: String, durationMillis: Long) {
        val until = System.currentTimeMillis() + durationMillis
        prefs(context).edit().putLong(KEY_BLOCKED_PREFIX + packageName, until).apply()
    }

    fun isAppBlocked(context: Context, packageName: String): Boolean {
        val until = prefs(context).getLong(KEY_BLOCKED_PREFIX + packageName, 0L)
        return System.currentTimeMillis() < until
    }

    fun remainingMinutes(context: Context, packageName: String): Long {
        val until = prefs(context).getLong(KEY_BLOCKED_PREFIX + packageName, 0L)
        val remaining = until - System.currentTimeMillis()
        return if (remaining > 0) remaining / 60000 else 0
    }
}
