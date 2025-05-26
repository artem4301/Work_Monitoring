package com.example.workmonitoring.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.workmonitoring.R
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.service.LocationTrackingService
import com.example.workmonitoring.service.PeriodicVerificationService
import com.example.workmonitoring.utils.NavigationHelper
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth

class HomeActivity : AppCompatActivity() {

    private lateinit var textUserName: TextView
    private lateinit var textUserEmail: TextView
    private lateinit var textWorkspaceName: TextView
    private lateinit var btnFaceRegistration: MaterialButton
    private lateinit var btnFaceControl: MaterialButton
    private lateinit var btnLogout: MaterialButton
    private val repository = FirebaseRepository()
    private var isInZone = false
    private val handler = Handler(Looper.getMainLooper())
    private val zoneCheckRunnable = object : Runnable {
        override fun run() {
            checkZoneStatus()
            handler.postDelayed(this, 5000) // Проверяем каждые 5 секунд
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        initializeViews()
        checkLocationPermission()
        loadUserData()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        handler.post(zoneCheckRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(zoneCheckRunnable)
    }

    private fun initializeViews() {
        textUserName = findViewById(R.id.userNameText)
        textUserEmail = findViewById(R.id.userEmailText)
        textWorkspaceName = findViewById(R.id.workspaceNameText)
        btnFaceRegistration = findViewById(R.id.btnFaceRegistration)
        btnFaceControl = findViewById(R.id.btnFaceControl)
        btnLogout = findViewById(R.id.btnLogout)
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            startLocationService()
        }
    }

    private fun startLocationService() {
        val serviceIntent = Intent(this, LocationTrackingService::class.java)
        startService(serviceIntent)
    }

    private fun loadUserData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        repository.getCurrentUser { user ->
            if (user != null) {
                textUserName.text = "${user.firstName} ${user.lastName}"
                textUserEmail.text = user.email

                if (user.role == "worker") {
                    // Отладочные логи
                    android.util.Log.d("HomeActivity", "User data loaded:")
                    android.util.Log.d("HomeActivity", "isActive: ${user.isActive}")
                    android.util.Log.d("HomeActivity", "shiftStartTime: ${user.shiftStartTime}")
                    android.util.Log.d("HomeActivity", "Should navigate: ${user.isActive == true && user.shiftStartTime != null}")
                    
                    // Проверяем активную смену - если есть, переходим к WorkTimeActivity
                    if (user.isActive == true && user.shiftStartTime != null) {
                        android.util.Log.d("HomeActivity", "Navigating to WorkTimeActivity")
                        val intent = NavigationHelper.getAppropriateIntent(this@HomeActivity, user)
                        startActivity(intent)
                        finish()
                        return@getCurrentUser
                    }
                    
                    loadWorkerData(userId)
                    
                    // Проверяем, требуется ли верификация или смена приостановлена
                    if (user.verificationRequired || user.shiftPaused) {
                        showVerificationRequiredDialog(user.pauseReason ?: "Требуется верификация")
                    }
                    
                    // Запускаем сервис периодической верификации если смена активна
                    if (user.isActive && !user.shiftPaused) {
                        startPeriodicVerificationService()
                    }
                }
            }
        }
    }

    private fun loadWorkerData(userId: String) {
        repository.getUserWorkZone(userId,
            onSuccess = { address ->
                repository.getCurrentUser { user ->
                    if (user != null) {
                        val radius = user.workZoneRadius ?: 0.0
                        textWorkspaceName.text = if (radius > 0) {
                            "$address\nРадиус зоны: ${radius.toInt()} м"
                        } else {
                            address
                        }
                    }
                }
            },
            onFailure = {
                textWorkspaceName.text = "Зона не назначена"
            }
        )

        // Проверяем наличие эмбеддингов
        repository.getUserEmbeddings(
            userId = userId,
            onSuccess = { embeddings ->
                // Если эмбеддинги есть, скрываем кнопку регистрации
                btnFaceRegistration.visibility = View.GONE
            },
            onFailure = {
                // Если эмбеддингов нет, показываем кнопку регистрации
                btnFaceRegistration.visibility = View.VISIBLE
            }
        )

        // Начинаем периодическую проверку статуса зоны
        checkZoneStatus()
    }

    private fun checkZoneStatus() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        repository.getWorkerLocationStatus(userId) { inZone ->
            isInZone = inZone
            updateFaceControlButton()
        }
    }

    private fun updateFaceControlButton() {
        btnFaceControl.isEnabled = isInZone
        btnFaceControl.alpha = if (isInZone) 1.0f else 0.5f
    }

    private fun setupClickListeners() {
        btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        btnFaceRegistration.setOnClickListener {
            startActivity(Intent(this, FaceRegistrationActivity::class.java))
        }

        btnFaceControl.setOnClickListener {
            if (isInZone) {
                startActivity(Intent(this, FaceControlActivity::class.java))
            } else {
                Toast.makeText(
                    this,
                    "Вы должны находиться в рабочей зоне для прохождения фотоконтроля",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        
        // Добавляем скрытую кнопку для тестирования (долгое нажатие на логотип)
        findViewById<View>(R.id.userNameText)?.setOnLongClickListener {
            startActivity(Intent(this, TestPeriodicVerificationActivity::class.java))
            true
        }
    }

    private fun startPeriodicVerificationService() {
        val serviceIntent = Intent(this, PeriodicVerificationService::class.java)
        startService(serviceIntent)
    }

    private fun showVerificationRequiredDialog(reason: String) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Требуется верификация")
            .setMessage(reason)
            .setPositiveButton("Пройти верификацию") { _, _ ->
                val intent = Intent(this, FaceControlActivity::class.java)
                intent.putExtra("periodic_verification", true)
                startActivity(intent)
            }
            .setCancelable(false)
            .create()
        
        dialog.show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // После получения разрешения на точное местоположение, запрашиваем фоновое
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                            BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE
                        )
                    } else {
                        startLocationService()
                    }
                }
            }
            BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocationService()
                }
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 1002
    }
}