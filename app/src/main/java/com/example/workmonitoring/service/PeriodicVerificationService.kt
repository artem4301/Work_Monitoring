package com.example.workmonitoring.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.workmonitoring.R
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.ui.FaceControlActivity
import com.google.firebase.auth.FirebaseAuth

class PeriodicVerificationService : Service() {
    
    private val repository = FirebaseRepository()
    private val handler = Handler(Looper.getMainLooper())
    private var verificationRunnable: Runnable? = null
    
    companion object {
        private const val VERIFICATION_INTERVAL = 4 * 60 * 60 * 1000L // 4 часа в миллисекундах
        private const val CHANNEL_ID = "periodic_verification_channel"
        private const val NOTIFICATION_ID = 2
        private const val TAG = "PeriodicVerification"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Сервис периодической верификации создан")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Log.w(TAG, "Пользователь не авторизован, останавливаем сервис")
            stopSelf()
            return START_NOT_STICKY
        }
        
        startPeriodicVerification(userId)
        return START_STICKY
    }
    
    private fun startPeriodicVerification(userId: String) {
        verificationRunnable = object : Runnable {
            override fun run() {
                checkVerificationRequired(userId)
                // Проверяем каждые 5 минут, но требуем верификацию каждые 4 часа
                handler.postDelayed(this, 5 * 60 * 1000L) // 5 минут
            }
        }
        
        // Первая проверка через 5 минут после запуска
        handler.postDelayed(verificationRunnable!!, 5 * 60 * 1000L)
        Log.d(TAG, "Запущена периодическая верификация для пользователя: $userId")
    }
    
    private fun checkVerificationRequired(userId: String) {
        repository.getCurrentUser { user ->
            if (user != null && user.isActive) {
                val currentTime = System.currentTimeMillis()
                val lastVerification = user.lastVerificationTime ?: user.shiftStartTime ?: 0L
                
                Log.d(TAG, "Проверка верификации: текущее время=$currentTime, последняя верификация=$lastVerification")
                Log.d(TAG, "Разница: ${(currentTime - lastVerification) / 1000 / 60} минут")
                
                // Проверяем, прошло ли 4 часа с последней верификации
                if (currentTime - lastVerification >= VERIFICATION_INTERVAL && !user.verificationRequired) {
                    Log.d(TAG, "Требуется верификация для пользователя: $userId")
                    
                    // Устанавливаем флаг необходимости верификации
                    repository.setVerificationRequired(userId, true) { success ->
                        if (success) {
                            // Приостанавливаем смену до прохождения верификации
                            repository.pauseShift(userId, "Требуется периодическая верификация") { pauseSuccess ->
                                if (pauseSuccess) {
                                    Log.d(TAG, "Смена приостановлена для верификации")
                                    showVerificationNotification()
                                }
                            }
                        }
                    }
                } else if (user.verificationRequired) {
                    Log.d(TAG, "Верификация уже требуется, показываем уведомление")
                    showVerificationNotification()
                }
            } else {
                Log.d(TAG, "Пользователь неактивен или не найден")
            }
        }
    }
    
    private fun showVerificationNotification() {
        val intent = Intent(this, FaceControlActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("periodic_verification", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Требуется верификация")
            .setContentText("Пройдите фотоконтроль для продолжения смены")
            .setSmallIcon(R.drawable.ic_face_verification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_face_verification,
                "Пройти верификацию",
                pendingIntent
            )
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Периодическая верификация",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о необходимости прохождения периодической верификации"
                setShowBadge(true)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        verificationRunnable?.let { handler.removeCallbacks(it) }
        Log.d(TAG, "Сервис периодической верификации остановлен")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
} 