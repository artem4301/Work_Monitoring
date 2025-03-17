package com.example.workmonitoring.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.workmonitoring.R
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.viewmodel.RegisterViewModelFactory

class RegisterActivity : AppCompatActivity() {
    private val registerViewModel: RegisterViewModel by viewModels {
        RegisterViewModelFactory(FirebaseRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val btnSignUp = findViewById<Button>(R.id.btnSignUp)
        val btnSignIn = findViewById<Button>(R.id.btnSignIn)
        val btnResetPassword = findViewById<Button>(R.id.btnResetPassword)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        btnSignUp.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            registerViewModel.register(email, password)
        }

        btnSignIn.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        btnResetPassword.setOnClickListener {
            startActivity(Intent(this, ResetPasswordActivity::class.java))
        }

        registerViewModel.registerResult.observe(this) { result ->
            result.onSuccess {
                Toast.makeText(this, "Регистрация успешна", Toast.LENGTH_SHORT).show()
                finish()
            }.onFailure { error ->
                Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

