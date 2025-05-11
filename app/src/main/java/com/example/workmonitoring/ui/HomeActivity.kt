package com.example.workmonitoring.ui

import android.content.Intent
import android.os.Bundle
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
        val userZoneText = findViewById<TextView>(R.id.textViewWorkZone)

        homeViewModel.getUserWorkZone(
            onResult = { address ->
                userZoneText.text = "Зона: $address"
            },
            onFailure = { error ->
                Toast.makeText(this, "Ошибка загрузки зоны: $error", Toast.LENGTH_SHORT).show()
            }
        )

        homeViewModel.getUserFullName(
            onResult = { name ->
                userEmailText.text = name
            },
            onFailure = {
                userEmailText.text = "Неизвестный пользователь"
            }
        )

        btnFaceRegistration.isEnabled = true
        btnFaceRegistration.text = "Зарегистрировать лицо"

        btnFaceRegistration.setOnClickListener {
            startActivity(Intent(this, FaceRegistrationActivity::class.java))
        }

        homeViewModel.checkUserEmbeddings(
            onRegistered = {
                btnFaceRegistration.text = "Регистрация завершена"
                btnFaceRegistration.isEnabled = false
            },
            onNotRegistered = {
                btnFaceRegistration.isEnabled = true
            },
            onFailure = { error ->
                Toast.makeText(this, "Ошибка проверки эмбеддингов: $error", Toast.LENGTH_SHORT).show()
                btnFaceRegistration.isEnabled = true
            }
        )

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

        btnLogout.setOnClickListener {
            homeViewModel.logout()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}
