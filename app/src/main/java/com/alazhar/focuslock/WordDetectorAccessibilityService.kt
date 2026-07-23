package com.alazhar.focuslock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import android.widget.Toast
import android.provider.Settings

class WordDetectorAccessibilityService : AccessibilityService() {

    companion object {
        private val REMINDER_TEXTS = listOf(
            "يَا أَيُّهَا النَّاسُ اعْبُدُوا رَبَّكُمُ الَّذِي خَلَقَكُمْ وَالَّذِينَ مِن قَبْلِكُمْ لَعَلَّكُمْ تَتَّقُونَ",
            "اللهم اغفر ذنبي وطهر قلبي وحصّن فرجي"
        )
        private const val DEBOUNCE_MS = 3000L
        private const val OVERLAY_DURATION_MS = 4000L
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

        if (!WordsRepository.isEnabled(this)) return

        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return

        val eventPackage = event.packageName?.toString() ?: return
        if (eventPackage == applicationContext.packageName) return
        if (eventPackage in protectedPackages || eventPackage.contains("systemui")) return

        val blockedWords = WordsRepository.getWords(this)
        if (blockedWords.isEmpty()) return

        val typedText = event.text?.joinToString(" ") ?: return
        if (typedText.isBlank()) return

        // نطبّع وننقسم النص المكتوب مرة واحدة فقط (أداء أفضل بدل تكراره داخل كل مقارنة)
        val normalizedContent = normalizeArabic(typedText.lowercase())
        val contentTokens = tokenize(normalizedContent)

        for (word in blockedWords) {
            val w = word.trim()
            if (w.isEmpty()) continue
            if (containsWholeWordFixed(normalizedContent, contentTokens, w)) {
                val now = System.currentTimeMillis()
                if (now - lastActionTime < DEBOUNCE_MS) return
                lastActionTime = now

                performGlobalAction(GLOBAL_ACTION_HOME)
                showReminder()
                break
            }
        }
    }

    /**
     * تطبيع النص العربي:
     * - إزالة التشكيل (الحركات)
     * - إزالة حرف التطويل (الكشيدة)
     * - توحيد أشكال الألف والهمزات إلى "ا"
     */
    private fun normalizeArabic(input: String): String {
        var s = input

        // إزالة الحركات والتشكيل
        s = s.replace(Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]"), "")

        // إزالة حرف التطويل (الكشيدة)
        s = s.replace("\u0640", "")

        // توحيد أشكال الألف والهمزة
        s = s.replace(Regex("[إأآا]"), "ا")

        return s.trim()
    }

    /**
     * تقسيم النص إلى كلمات فعلية بدل الاعتماد على \b
     * (\b غير موثوق مع النصوص العربية على بعض أجهزة Android)
     */
    private fun tokenize(text: String): List<String> {
        return text.split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }
    }

    /**
     * مطابقة الكلمة الممنوعة مع النص المكتوب:
     * - مطابقة تامة لكلمة واحدة داخل قائمة الكلمات المقسّمة (tokens)
     * - أو مطابقة جزئية (contains) في حال كانت الكلمة الممنوعة عبارة من أكثر من كلمة
     */
    private fun containsWholeWordFixed(
        normalizedContent: String,
        contentTokens: List<String>,
        word: String
    ): Boolean {
        val normalizedWord = normalizeArabic(word.lowercase())
        if (normalizedWord.isEmpty()) return false

        // عبارة مكوّنة من أكثر من كلمة -> نبحث عنها كسلسلة متصلة داخل النص الكامل
        if (normalizedWord.contains(" ")) {
            return normalizedContent.contains(normalizedWord)
        }

        // كلمة واحدة -> مطابقة تامة مع إحدى الكلمات المقسّمة
        return contentTokens.any { it == normalizedWord }
    }

    private fun showReminder() {
        val reminderText = REMINDER_TEXTS.random()
        mainHandler.post {
            if (!Settings.canDrawOverlays(applicationContext)) {
                Toast.makeText(applicationContext, reminderText, Toast.LENGTH_LONG).show()
                return@post
            }
            try {
                val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                val textView = TextView(applicationContext).apply {
                    text = reminderText
                    setTextColor(Color.WHITE)
                    textSize = 20f
                    gravity = Gravity.CENTER
                    setPadding(56, 40, 56, 40)
                    setBackgroundColor(Color.parseColor("#E61B5E20"))
                }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                )
                params.gravity = Gravity.CENTER

                windowManager.addView(textView, params)
                mainHandler.postDelayed({
                    try {
                        windowManager.removeView(textView)
                    } catch (e: Exception) {
                    }
                }, OVERLAY_DURATION_MS)
            } catch (e: Exception) {
                Toast.makeText(applicationContext, reminderText, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onInterrupt() {}
}
