package com.example.workmonitoring.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.workmonitoring.R
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.service.LocationTrackingService
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

class WorkTimeActivity : AppCompatActivity() {

    private lateinit var timerTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var statusIcon: ImageView
    private lateinit var shiftStartTimeText: TextView
    private lateinit var pauseTimeText: TextView
    private lateinit var btnEndShift: MaterialButton
    
    private val repository = FirebaseRepository()
    private val handler = Handler(Looper.getMainLooper())
    private var shiftStartTime: Long = 0L
    private var totalPauseDuration: Long = 0L
    private var isShiftPaused = false
    private var backPressedOnce = false
    private var wasInZone = true // Предполагаем, что изначально в зоне
    private var pauseStartTime: Long = 0L
    private var verificationDialogShown = false
    
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateTimer()
            updateLocationStatus()
            handler.postDelayed(this, 1000) // Обновляем каждую секунду
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_work_time)
        
        initializeViews()
        setupClickListeners()
        setupBackPressedHandler()
        checkPermissionsAndStartTracking()
        loadShiftData()
    }

    override fun onResume() {
        super.onResume()
        // Сбрасываем флаг диалога при возобновлении активности
        verificationDialogShown = false
        // Перезагружаем данные смены для проверки актуального статуса
        loadShiftData()
    }

    private fun initializeViews() {
        timerTextView = findViewById(R.id.timerTextView)
        statusTextView = findViewById(R.id.statusTextView)
        statusIcon = findViewById(R.id.statusIcon)
        shiftStartTimeText = findViewById(R.id.shiftStartTimeText)
        pauseTimeText = findViewById(R.id.pauseTimeText)
        btnEndShift = findViewById(R.id.btnEndShift)
    }

    private fun setupClickListeners() {
        btnEndShift.setOnClickListener {
            showEndShiftDialog()
        }
    }

    private fun setupBackPressedHandler() {
        // Современный способ обработки кнопки "Назад" для Android 13+
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    private fun handleBackPress() {
        if (!backPressedOnce) {
            backPressedOnce = true
            Toast.makeText(this, "Приложение свернется. Для завершения смены используйте кнопку 'Завершить смену'", Toast.LENGTH_LONG).show()
            
            // Сбрасываем флаг через 3 секунды
            handler.postDelayed({
                backPressedOnce = false
            }, 3000)
        } else {
            // Сворачиваем приложение
            moveTaskToBack(true)
        }
    }

    private fun checkPermissionsAndStartTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            startLocationTracking()
        }
    }

    private fun startLocationTracking() {
        val intent = Intent(this, LocationTrackingService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun loadShiftData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        repository.getCurrentUser { user ->
            if (user != null) {
                shiftStartTime = user.shiftStartTime ?: System.currentTimeMillis()
                totalPauseDuration = user.totalPauseDuration ?: 0L
                isShiftPaused = user.shiftPaused ?: false
                pauseStartTime = user.pauseStartTime ?: 0L
                
                // Проверяем текущий статус зоны и применяем логику паузы
                repository.getWorkerLocationStatus(userId) { inZone ->
                    runOnUiThread {
                        // Если пользователь вне зоны и смена не приостановлена, приостанавливаем
                        if (!inZone && !isShiftPaused) {
                            repository.pauseShift(userId, "Выход из рабочей зоны") { success ->
                                if (success) {
                                    runOnUiThread {
                                        Toast.makeText(this@WorkTimeActivity, "Смена приостановлена: выход из рабочей зоны", Toast.LENGTH_LONG).show()
                                        // Обновляем локальные данные
                                        isShiftPaused = true
                                        pauseStartTime = System.currentTimeMillis()
                                    }
                                }
                            }
                        }
                        // Если пользователь в зоне и смена приостановлена по причине выхода из зоны, возобновляем
                        else if (inZone && isShiftPaused && user.pauseReason == "Выход из рабочей зоны") {
                            repository.resumeShift(userId) { success ->
                                if (success) {
                                    runOnUiThread {
                                        Toast.makeText(this@WorkTimeActivity, "Смена возобновлена: возврат в рабочую зону", Toast.LENGTH_SHORT).show()
                                        // Обновляем локальные данные
                                        isShiftPaused = false
                                        totalPauseDuration += (System.currentTimeMillis() - pauseStartTime)
                                        pauseStartTime = 0L
                                    }
                                }
                            }
                        }
                        
                        // Устанавливаем текущий статус как предыдущий для дальнейшего отслеживания
                        wasInZone = inZone
                    }
                }
                
                // Обновляем информацию о смене
                updateShiftInfo()
                
                // Запускаем обновление таймера
                handler.post(updateRunnable)
            }
        }
    }

    private fun updateTimer() {
        if (shiftStartTime == 0L) return
        
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - shiftStartTime
        
        // Если смена сейчас приостановлена, добавляем текущее время паузы
        val currentPauseDuration = if (isShiftPaused && pauseStartTime > 0L) {
            totalPauseDuration + (currentTime - pauseStartTime)
        } else {
            totalPauseDuration
        }
        
        val workingTime = maxOf(0L, elapsedTime - currentPauseDuration)
        
        val hours = workingTime / (1000 * 60 * 60)
        val minutes = (workingTime % (1000 * 60 * 60)) / (1000 * 60)
        val seconds = (workingTime % (1000 * 60)) / 1000
        
        val statusSuffix = if (isShiftPaused) " (ПАУЗА)" else ""
        timerTextView.text = String.format("%02d:%02d:%02d%s", hours, minutes, seconds, statusSuffix)
        
        // Обновляем время пауз
        updatePauseTime(currentPauseDuration)
    }

    private fun updateShiftInfo() {
        if (shiftStartTime > 0L) {
            val startTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(shiftStartTime))
            shiftStartTimeText.text = startTime
        }
    }

    private fun updatePauseTime(pauseDuration: Long) {
        val pauseHours = pauseDuration / (1000 * 60 * 60)
        val pauseMinutes = (pauseDuration % (1000 * 60 * 60)) / (1000 * 60)
        pauseTimeText.text = String.format("%02d:%02d", pauseHours, pauseMinutes)
    }

    private fun updateLocationStatus() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        repository.getWorkerLocationStatus(userId) { inZone ->
            runOnUiThread {
                repository.getCurrentUser { user ->
                    if (user != null) {
                        // Проверяем изменение статуса зоны
                        handleZoneStatusChange(userId, inZone, user.shiftPaused ?: false)
                        
                        val (status, iconRes, colorRes) = when {
                            user.verificationRequired == true -> {
                                // Показываем диалог верификации если еще не показан
                                showVerificationRequiredDialog()
                                Triple("Требуется верификация", R.drawable.ic_face_verification, R.color.warning)
                            }
                            user.shiftPaused == true -> {
                                val reason = user.pauseReason ?: "Неизвестная причина"
                                Triple("Смена приостановлена: $reason", R.drawable.ic_stop, R.color.warning)
                            }
                            inZone -> Triple("В рабочей зоне", R.drawable.ic_location_on, R.color.success)
                            else -> Triple("Вне рабочей зоны", R.drawable.ic_location, R.color.error)
                        }
                        
                        statusTextView.text = status
                        statusIcon.setImageResource(iconRes)
                        statusIcon.setColorFilter(ContextCompat.getColor(this@WorkTimeActivity, colorRes))
                        statusTextView.setTextColor(ContextCompat.getColor(this@WorkTimeActivity, colorRes))
                        
                        // Обновляем состояние паузы
                        isShiftPaused = user.shiftPaused ?: false
                        totalPauseDuration = user.totalPauseDuration ?: 0L
                        pauseStartTime = user.pauseStartTime ?: 0L
                    }
                }
            }
        }
    }

    private fun handleZoneStatusChange(userId: String, currentlyInZone: Boolean, currentlyPaused: Boolean) {
        // Если статус зоны изменился
        if (wasInZone != currentlyInZone) {
            if (!currentlyInZone && !currentlyPaused) {
                // Вышел из зоны и смена не приостановлена - приостанавливаем
                repository.pauseShift(userId, "Выход из рабочей зоны") { success ->
                    if (success) {
                        runOnUiThread {
                            Toast.makeText(this, "Смена приостановлена: выход из рабочей зоны", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else if (currentlyInZone && currentlyPaused) {
                // Вернулся в зону и смена приостановлена по причине выхода из зоны - возобновляем
                repository.getCurrentUser { user ->
                    if (user?.pauseReason == "Выход из рабочей зоны") {
                        repository.resumeShift(userId) { success ->
                            if (success) {
                                runOnUiThread {
                                    Toast.makeText(this, "Смена возобновлена: возврат в рабочую зону", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
            
            // Обновляем предыдущий статус
            wasInZone = currentlyInZone
        }
    }

    private fun showVerificationRequiredDialog() {
        // Проверяем, не показан ли уже диалог
        if (verificationDialogShown) {
            return
        }
        
        verificationDialogShown = true
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Требуется верификация")
            .setMessage("Для продолжения работы необходимо пройти фотоконтроль. Время смены приостановлено до прохождения верификации.")
            .setPositiveButton("Пройти верификацию") { _, _ ->
                verificationDialogShown = false
                val intent = Intent(this, FaceControlActivity::class.java)
                intent.putExtra("periodic_verification", true)
                startActivity(intent)
            }
            .setCancelable(false)
            .create()
        
        dialog.setOnDismissListener {
            verificationDialogShown = false
        }
        
        dialog.show()
    }

    private fun showEndShiftDialog() {
        AlertDialog.Builder(this)
            .setTitle("Завершить смену")
            .setMessage("Вы уверены, что хотите завершить рабочую смену?")
            .setPositiveButton("Да") { _, _ ->
                endShift()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun endShift() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        repository.endShift(userId) { success ->
            if (success) {
                // Останавливаем сервис отслеживания
                val intent = Intent(this, LocationTrackingService::class.java)
                stopService(intent)
                
                Toast.makeText(this, "Смена завершена", Toast.LENGTH_SHORT).show()
                
                // Возвращаемся на главный экран
                val homeIntent = Intent(this, HomeActivity::class.java)
                homeIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(homeIntent)
                finish()
            } else {
                Toast.makeText(this, "Ошибка завершения смены", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationTracking()
            } else {
                Toast.makeText(this, "Разрешение на местоположение необходимо для работы", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Для совместимости со старыми версиями Android
        handleBackPress()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
} 