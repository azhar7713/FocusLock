package com.alazhar.focuslock

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WordDetectorAccessibilityService : AccessibilityService() {

    companion object {
        private const val BLOCK_DURATION_MS = 30 * 60 * 1000L
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        if (WordsRepository.isAppBlocked(this, packageName)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        val blockedWords = WordsRepository.getWords(this)
        if (blockedWords.isEmpty()) return

        val root = rootInActiveWindow ?: return
        val screenText = StringBuilder()
        collectText(root, screenText)
        val content = screenText.toString().lowercase()

        for (word in blockedWords) {
            val w = word.trim().lowercase()
            if (w.isNotEmpty() && content.contains(w)) {
                WordsRepository.blockApp(this, packageName, BLOCK_DURATION_MS)
                performGlobalAction(GLOBAL_ACTION_HOME)
                break
            }
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
