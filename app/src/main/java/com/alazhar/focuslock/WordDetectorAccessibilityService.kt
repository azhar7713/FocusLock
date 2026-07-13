package com.alazhar.focuslock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

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
        val eventPackage = event.packageName?.toString() ?: return

        if (eventPackage == applicationContext.packageName) return
        if (eventPackage in protectedPackages || eventPackage.contains("systemui")) return

        val blockedWords = WordsRepository.getWords(this)
        if (blockedWords.isEmpty()) return

        val root = rootInActiveWindow ?: return
        val screenText = StringBuilder()
        collectText(root, screenText)
        val content = screenText.toString().lowercase()

        for (word in blockedWords) {
            val w = word.trim().lowercase()
            if (w.isNotEmpty() && content.contains(w)) {
                val now = System.currentTimeMillis()
                if (now - lastActionTime < DEBOUNCE_MS) return
                lastActionTime = now

                performGlobalAction(GLOBAL_ACTION_HOME)
                showReminder()
                break
            }
        }
    }

    private fun showReminder() {
        mainHandler.post {
            Toast.makeText(applicationContext, REMINDER_TEXT, Toast.LENGTH_LONG).show()
        }
    }

    private fun collectText(node: AccessibilityNodeInfo?, out: StringBuilder, depth: Int = 0) {
        if (node == null || depth > 40) return
        node.text?.let { out.append(it).append(' ') }
        node.contentDescription?.let { out.append(it).append(' ') }
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), out, depth + 1)
        }
    }

    override fun onInterrupt() {}
}
