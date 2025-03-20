package com.example.workmonitoring.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.workmonitoring.R
import com.example.workmonitoring.viewmodel.HomeViewModel
import com.google.firebase.auth.FirebaseAuth
import com.example.workmonitoring.data.FirebaseRepository

class HomeActivity : AppCompatActivity() {

    private val homeViewModel: HomeViewModel by viewModels {
        HomeViewModel.Factory(assets, FirebaseRepository(), FirebaseAuth.getInstance())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val userEmailText = findViewById<TextView>(R.id.textViewUserEmail)
        val btnFaceRegistration = findViewById<Button>(R.id.btnFaceRegistration)
        val btnFaceControl = findViewById<Button>(R.id.btnFaceControl)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        // Устанавливаем email текущего пользователя
        userEmailText.text = homeViewModel.getUserEmail() ?: "Неизвестный пользователь"

        // Делаем кнопку регистрации лица активной по умолчанию
        btnFaceRegistration.isEnabled = true
        btnFaceRegistration.text = "Зарегистрировать лицо"

        btnFaceRegistration.setOnClickListener {
            startActivity(Intent(this, FaceRegistrationActivity::class.java))
        }

        // Проверяем количество эмбеддингов пользователя
        homeViewModel.checkUserEmbeddings(
            onRegistered = {
                Log.d("HomeActivity", "✅ Пользователь зарегистрирован, кнопка отключена")
                btnFaceRegistration.text = "Регистрация завершена"
                btnFaceRegistration.isEnabled = false
            },
            onNotRegistered = {
                Log.d("HomeActivity", "⚠️ Недостаточно эмбеддингов, кнопка включена")
                btnFaceRegistration.isEnabled = true
            },
            onFailure = { error ->
                Log.e("HomeActivity", "❌ Ошибка проверки эмбеддингов: $error")
                Toast.makeText(this, "Ошибка проверки эмбеддингов: $error", Toast.LENGTH_SHORT).show()
                btnFaceRegistration.isEnabled = true
            }
        )

        // Кнопка Face ID проверки
        btnFaceControl.setOnClickListener {
            homeViewModel.checkUserEmbeddings(
                onRegistered = {
                    startActivity(Intent(this, FaceControlActivity::class.java))
                },
                onNotRegistered = {
                    Toast.makeText(this, "Для прохождения Face ID нужно загрузить 10 фотографий!", Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Кнопка выхода
        btnLogout.setOnClickListener {
            homeViewModel.logout()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
