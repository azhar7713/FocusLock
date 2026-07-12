package com.alazhar.focuslock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class WordDetectorAccessibilityService : AccessibilityService() {

    companion object {
        private const val BLOCK_DURATION_MS = 30 * 60 * 1000L
    }

    private val protectedPackages: Set<String> by lazy { buildProtectedPackages() }

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
            // تجاهل أي خطأ هنا؛ القائمة الثابتة أعلاه تبقى خط الدفاع الأول
        }
        return protected
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventPackage = event.packageName?.toString() ?: return

        // حماية مطلقة أولى: تجاهل تطبيق FocusLock نفسه دائمًا، قبل أي فحص آخر
        if (eventPackage == applicationContext.packageName) return

        // حماية ثانية: واجهة النظام، الشاشة الرئيسية، والإعدادات
        if (eventPackage in protectedPackages || eventPackage.contains("systemui")) return

        if (WordsRepository.isAppBlocked(this, eventPackage)) {
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
                WordsRepository.blockApp(this, eventPackage, BLOCK_DURATION_MS)
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
