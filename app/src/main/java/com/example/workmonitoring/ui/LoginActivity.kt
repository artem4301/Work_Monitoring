package com.example.workmonitoring.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.workmonitoring.R
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.viewmodel.LoginViewModelFactory
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {
    private val loginViewModel: LoginViewModel by viewModels {
        LoginViewModelFactory(FirebaseRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Проверяем, залогинен ли пользователь
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            // Если пользователь уже залогинен, переходим в HomeActivity
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        // Если не залогинен, показываем экран входа
        setContentView(R.layout.activity_login)

        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnSignUp = findViewById<Button>(R.id.btnSignUp)
        val btnResetPassword = findViewById<Button>(R.id.btnResetPassword)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        btnLogin.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Введите email и пароль!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // ✅ Показываем индикатор загрузки
            progressBar.visibility = View.VISIBLE

            loginViewModel.login(email, password)
        }

        btnSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnResetPassword.setOnClickListener {
            startActivity(Intent(this, ResetPasswordActivity::class.java))
        }

        loginViewModel.loginResult.observe(this) { result ->
            result.onSuccess {
                progressBar.visibility = View.GONE  // ✅ Скрываем индикатор
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            }.onFailure { error ->
                progressBar.visibility = View.GONE  // ✅ Скрываем индикатор
                Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
