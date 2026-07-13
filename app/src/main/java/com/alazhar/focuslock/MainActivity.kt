package com.alazhar.focuslock

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var wordsEditText: EditText
    private lateinit var enabledSwitch: SwitchMaterial
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wordsEditText = findViewById(R.id.editTextWords)
        val saveButton = findViewById<Button>(R.id.buttonSave)
        val enableServiceButton = findViewById<Button>(R.id.buttonEnableService)
        val enableOverlayButton = findViewById<Button>(R.id.buttonEnableOverlay)
        enabledSwitch = findViewById(R.id.switchEnabled)
        statusText = findViewById(R.id.textEnabledStatus)

        val savedWords = WordsRepository.getWords(this)
        wordsEditText.setText(savedWords.joinToString("\n"))

        val isEnabled = WordsRepository.isEnabled(this)
        enabledSwitch.isChecked = isEnabled
        updateStatusText(isEnabled)

        saveButton.setOnClickListener {
            val words = wordsEditText.text.toString()
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            WordsRepository.setWords(this, words)
            Toast.makeText(this, "تم حفظ الكلمات (${words.size})", Toast.LENGTH_SHORT).show()
        }

        enableServiceButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(
                this,
                "فعّل خدمة FocusLock من القائمة، ثم ارجع للتطبيق",
                Toast.LENGTH_LONG
            ).show()
        }

        enableOverlayButton.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "الصلاحية مفعّلة بالفعل", Toast.LENGTH_SHORT).show()
            }
        }

        enabledSwitch.setOnCheckedChangeListener { _, checked ->
            WordsRepository.setEnabled(this, checked)
            updateStatusText(checked)
            val message = if (checked) "تم تفعيل الخدمة" else "تم تعطيل الخدمة مؤقتًا"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatusText(enabled: Boolean) {
        statusText.text = if (enabled) "الخدمة مفعّلة" else "الخدمة معطّلة مؤقتًا"
    }
}
