package com.example.workmonitoring.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.workmonitoring.R
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.service.PeriodicVerificationService
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

class TestPeriodicVerificationActivity : AppCompatActivity() {

    private lateinit var repository: FirebaseRepository
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_periodic_verification)
        
        repository = FirebaseRepository()
        statusText = findViewById(R.id.statusText)
        
        setupButtons()
        updateStatus()
    }

    private fun setupButtons() {
        findViewById<MaterialButton>(R.id.btnStartService).setOnClickListener {
            startPeriodicVerificationService()
        }
        
        findViewById<MaterialButton>(R.id.btnStopService).setOnClickListener {
            stopPeriodicVerificationService()
        }
        
        findViewById<MaterialButton>(R.id.btnSimulateVerificationRequired).setOnClickListener {
            simulateVerificationRequired()
        }
        
        findViewById<MaterialButton>(R.id.btnResetVerificationTime).setOnClickListener {
            resetVerificationTime()
        }
        
        findViewById<MaterialButton>(R.id.btnSetVerificationTime4HoursAgo).setOnClickListener {
            setVerificationTime4HoursAgo()
        }
        
        findViewById<MaterialButton>(R.id.btnUpdateStatus).setOnClickListener {
            updateStatus()
        }
        
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun startPeriodicVerificationService() {
        val intent = Intent(this, PeriodicVerificationService::class.java)
        startService(intent)
        Toast.makeText(this, "Сервис периодической верификации запущен", Toast.LENGTH_SHORT).show()
    }

    private fun stopPeriodicVerificationService() {
        val intent = Intent(this, PeriodicVerificationService::class.java)
        stopService(intent)
        Toast.makeText(this, "Сервис периодической верификации остановлен", Toast.LENGTH_SHORT).show()
    }

    private fun simulateVerificationRequired() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        repository.setVerificationRequired(userId, true) { success ->
            if (success) {
                repository.pauseShift(userId, "Тестовая периодическая верификация") { pauseSuccess ->
                    if (pauseSuccess) {
                        Toast.makeText(this, "Верификация установлена как требуемая", Toast.LENGTH_SHORT).show()
                        updateStatus()
                    }
                }
            }
        }
    }

    private fun resetVerificationTime() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val currentTime = System.currentTimeMillis()
        
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .update(mapOf(
                "lastVerificationTime" to currentTime,
                "verificationRequired" to false,
                "shiftPaused" to false,
                "pauseReason" to ""
            ))
            .addOnSuccessListener {
                Toast.makeText(this, "Время верификации сброшено на текущее", Toast.LENGTH_SHORT).show()
                updateStatus()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Ошибка сброса времени", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setVerificationTime4HoursAgo() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val fourHoursAgo = System.currentTimeMillis() - (4 * 60 * 60 * 1000L + 5 * 60 * 1000L) // 4 часа 5 минут назад
        
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .update(mapOf(
                "lastVerificationTime" to fourHoursAgo,
                "verificationRequired" to false,
                "shiftPaused" to false
            ))
            .addOnSuccessListener {
                Toast.makeText(this, "Время верификации установлено на 4+ часов назад", Toast.LENGTH_SHORT).show()
                updateStatus()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Ошибка установки времени", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateStatus() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        repository.getCurrentUser { user ->
            if (user != null) {
                val currentTime = System.currentTimeMillis()
                val shiftStartTime = user.shiftStartTime
                val lastVerification = user.lastVerificationTime ?: shiftStartTime ?: 0L
                val timeSinceLastVerification = currentTime - lastVerification
                val hoursAgo = timeSinceLastVerification / (1000 * 60 * 60)
                val minutesAgo = (timeSinceLastVerification % (1000 * 60 * 60)) / (1000 * 60)
                
                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
                
                val statusInfo = buildString {
                    appendLine("=== СТАТУС ПОЛЬЗОВАТЕЛЯ ===")
                    appendLine("Активная смена: ${user.isActive}")
                    appendLine("Смена приостановлена: ${user.shiftPaused}")
                    appendLine("Причина паузы: ${user.pauseReason ?: "нет"}")
                    appendLine("Требуется верификация: ${user.verificationRequired}")
                    appendLine()
                    appendLine("=== ВРЕМЕНА ===")
                    appendLine("Текущее время: ${dateFormat.format(Date(currentTime))}")
                    if (shiftStartTime != null) {
                        appendLine("Начало смены: ${dateFormat.format(Date(shiftStartTime))}")
                    }
                    if (lastVerification > 0) {
                        appendLine("Последняя верификация: ${dateFormat.format(Date(lastVerification))}")
                        appendLine("Прошло времени: ${hoursAgo}ч ${minutesAgo}м")
                    }
                    appendLine()
                    appendLine("=== ЛОГИКА ВЕРИФИКАЦИИ ===")
                    appendLine("Интервал верификации: 4 часа")
                    appendLine("Требуется верификация через: ${maxOf(0, 4 - hoursAgo)}ч ${maxOf(0, 60 - minutesAgo)}м")
                    appendLine("Условие срабатывания: ${timeSinceLastVerification >= 4 * 60 * 60 * 1000L}")
                }
                
                runOnUiThread {
                    statusText.text = statusInfo
                }
            }
        }
    }
} 