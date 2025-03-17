package com.example.workmonitoring.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.workmonitoring.R
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.viewmodel.ResetPasswordViewModelFactory
import com.google.firebase.auth.FirebaseAuth

class ResetPasswordActivity : AppCompatActivity() {

    private val resetPasswordViewModel: ResetPasswordViewModel by viewModels {
        ResetPasswordViewModelFactory(FirebaseRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        val emailInput = findViewById<EditText>(R.id.emailInput)
        val btnResetPassword = findViewById<Button>(R.id.btnResetPassword)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        btnResetPassword.setOnClickListener {
            val email = emailInput.text.toString().trim()
            if (email.isNotEmpty()) {
                resetPasswordViewModel.resetPassword(email)
            } else {
                Toast.makeText(this, "Введите email", Toast.LENGTH_SHORT).show()
            }
        }

        btnBack.setOnClickListener {
            finish()
        }

        resetPasswordViewModel.resetPasswordStatus.observe(this) { result ->
            result.onSuccess {
                Toast.makeText(this, "Ссылка для сброса отправлена", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

