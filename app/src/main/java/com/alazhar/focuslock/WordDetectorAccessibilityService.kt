package com.alazhar.focuslock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import java.util.regex.Pattern

class WordDetectorAccessibilityService : AccessibilityService() {

    companion object {
        private const val REMINDER_TEXT =
            "يَا أَيُّهَا النَّاسُ اعْبُدُوا رَبَّكُمُ الَّذِي خَلَقَكُمْ وَالَّذِينَ مِن قَبْلِكُمْ لَعَلَّكُمْ تَتَّقُونَ"
        private const val DEBOUNCE_MS = 3000L
    }

    private val protectedPackages: Set<String> by lazy { buildProtectedPackages() }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastActionTime = 0L

    private fun buildProtectedPackages(): Set<String> {
        val myOwnPackage = applicationContext.packageName
        val protected = mutableSetOf(
            "com.android.systemui",
            "com.android.settings",
            "android",
            myOwnPackage
        )
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName?.let { protected.add(it) }

            packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_ALL)
                .forEach { protected.add(it.activityInfo.packageName) }
        } catch (e: Exception) {
        }
        return protected
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return

        val eventPackage = event.packageName?.toString() ?: return

        if (eventPackage == applicationContext.packageName) return
        if (eventPackage in protectedPackages || eventPackage.contains("systemui")) return

        val blockedWords = WordsRepository.getWords(this)
        if (blockedWords.isEmpty()) return

        val typedText = event.text?.joinToString(" ") ?: return
        if (typedText.isBlank()) return

        for (word in blockedWords) {
            val w = word.trim()
            if (w.isEmpty()) continue
            if (containsWholeWord(typedText, w)) {
                val now = System.currentTimeMillis()
                if (now - lastActionTime < DEBOUNCE_MS) return
                lastActionTime = now

                performGlobalAction(GLOBAL_ACTION_HOME)
                showReminder()
                break
            }
        }
    }

    private fun containsWholeWord(content: String, word: String): Boolean {
        return try {
            val pattern = Pattern.compile(
                "\\b" + Pattern.quote(word) + "\\b",
                Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE or Pattern.UNICODE_CHARACTER_CLASS
            )
            pattern.matcher(content).find()
        } catch (e: Exception) {
            content.lowercase().contains(word.lowercase())
        }
    }

    private fun showReminder() {
        mainHandler.post {
            Toast.makeText(applicationContext, REMINDER_TEXT, Toast.LENGTH_LONG).show()
        }
    }

    override fun onInterrupt() {}
}
