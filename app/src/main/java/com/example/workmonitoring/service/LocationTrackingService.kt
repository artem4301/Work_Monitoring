package com.example.workmonitoring.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.workmonitoring.R
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.ui.HomeActivity
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth

class LocationTrackingService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val repository = FirebaseRepository()
    private var isInZone = false
    private var workZoneLatitude: Double = 0.0
    private var workZoneLongitude: Double = 0.0
    private var workZoneRadius: Double = 0.0
    private var workZoneAddress: String = ""

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationCallback()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (FirebaseAuth.getInstance().currentUser == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        loadWorkZone()
        startLocationUpdates()
        return START_STICKY
    }

    private fun loadWorkZone() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        repository.getCurrentUser { user ->
            if (user != null) {
                workZoneLatitude = user.workZoneLatitude ?: 0.0
                workZoneLongitude = user.workZoneLongitude ?: 0.0
                workZoneRadius = user.workZoneRadius ?: 0.0
                workZoneAddress = user.workZoneAddress ?: ""
                
                Log.d("LocationService", "Загружена зона: lat=$workZoneLatitude, lon=$workZoneLongitude, radius=$workZoneRadius м")
            }
        }
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (FirebaseAuth.getInstance().currentUser == null) {
                    stopSelf()
                    return
                }

                locationResult.lastLocation?.let { location ->
                    checkIfInZone(location)
                }
            }
        }
    }

    private fun checkIfInZone(location: Location) {
        if (workZoneLatitude == 0.0 || workZoneLongitude == 0.0) {
            Log.d("LocationService", "Координаты зоны не заданы")
            return
        }

        val distance = calculateDistance(
            location.latitude,
            location.longitude,
            workZoneLatitude,
            workZoneLongitude
        )
        
        Log.d("LocationService", """
            Текущее местоположение: lat=${location.latitude}, lon=${location.longitude}
            Расстояние до центра зоны: $distance м
            Радиус зоны: $workZoneRadius м
        """.trimIndent())

        val newIsInZone = distance <= workZoneRadius
        if (newIsInZone != isInZone) {
            isInZone = newIsInZone
            Log.d("LocationService", "Статус зоны изменился: ${if (isInZone) "в зоне" else "вне зоны"}")
            updateNotification()
            updateLocationStatus()
            handleZoneStatusChange(newIsInZone)
        }
    }

    private fun handleZoneStatusChange(currentlyInZone: Boolean) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        repository.getCurrentUser { user ->
            if (user != null) {
                if (!currentlyInZone && user.shiftPaused != true) {
                    // Вышел из зоны и смена не приостановлена - приостанавливаем
                    repository.pauseShift(userId, "Выход из рабочей зоны") { success ->
                        if (success) {
                            Log.d("LocationService", "Смена приостановлена: выход из рабочей зоны")
                            updateNotification()
                        }
                    }
                } else if (currentlyInZone && user.shiftPaused == true && user.pauseReason == "Выход из рабочей зоны") {
                    // Вернулся в зону и смена приостановлена по причине выхода из зоны - возобновляем
                    repository.resumeShift(userId) { success ->
                        if (success) {
                            Log.d("LocationService", "Смена возобновлена: возврат в рабочую зону")
                            updateNotification()
                        }
                    }
                }
            }
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 10000 // 10 seconds
            fastestInterval = 5000 // 5 seconds
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private fun updateLocationStatus() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        repository.updateWorkerLocationStatus(userId, isInZone)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Отслеживание местоположения",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Отслеживание местоположения работника"
                setShowBadge(true)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): android.app.Notification {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        var notificationText = if (isInZone) "Вы находитесь в рабочей зоне: $workZoneAddress" else "Вы вне рабочей зоны"
        
        // Проверяем статус паузы для более точного уведомления
        if (userId != null) {
            repository.getCurrentUser { user ->
                if (user?.shiftPaused == true) {
                    val reason = user.pauseReason ?: "Неизвестная причина"
                    notificationText = "Смена приостановлена: $reason"
                }
            }
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Отслеживание местоположения")
            .setContentText(notificationText)
            .setSmallIcon(R.drawable.ic_location)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, HomeActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    companion object {
        private const val CHANNEL_ID = "location_tracking_channel"
        private const val NOTIFICATION_ID = 1
    }
} 