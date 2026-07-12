package com.alazhar.focuslock

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var wordsEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wordsEditText = findViewById(R.id.editTextWords)
        val saveButton = findViewById<Button>(R.id.buttonSave)
        val enableServiceButton = findViewById<Button>(R.id.buttonEnableService)

        val savedWords = WordsRepository.getWords(this)
        wordsEditText.setText(savedWords.joinToString("\n"))

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
    }
}
