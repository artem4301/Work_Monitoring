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
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {
    private val loginViewModel: LoginViewModel by viewModels {
        LoginViewModelFactory(FirebaseRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()

        if (auth.currentUser != null) {
            val currentUserId = auth.currentUser!!.uid
            db.collection("users")
                .document(currentUserId)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val role = document.getString("role")
                        if (role == "manager") {
                            startActivity(Intent(this, ManagerHomeActivity::class.java))
                        } else {
                            startActivity(Intent(this, HomeActivity::class.java))
                        }
                        finish()
                    } else {
                        // Пользователь залогинен, но нет данных в Firestore — сбрасываем авторизацию
                        auth.signOut()
                        Toast.makeText(this, "Пользователь не найден. Войдите снова.", Toast.LENGTH_SHORT).show()
                        // Оставляем на LoginActivity
                    }
                }
                .addOnFailureListener {
                    auth.signOut()
                    Toast.makeText(this, "Ошибка при получении данных пользователя. Войдите снова.", Toast.LENGTH_SHORT).show()
                    // Оставляем на LoginActivity
                }
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
                progressBar.visibility = View.GONE

                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@onSuccess
                val db = FirebaseFirestore.getInstance()

                db.collection("users").document(userId).get()
                    .addOnSuccessListener { doc ->
                        val role = doc.getString("role") ?: "worker"

                        if (role == "manager") {
                            startActivity(Intent(this, ManagerHomeActivity::class.java))
                            finish()
                        } else {
                            // Проверка есть ли входящий запрос от управляющего
                            db.collection("requests")
                                .whereEqualTo("workerId", userId)
                                .whereEqualTo("status", "pending")
                                .get()
                                .addOnSuccessListener { requests ->
                                    if (!requests.isEmpty) {
                                        startActivity(Intent(this, RequestApprovalActivity::class.java))
                                    } else {
                                        startActivity(Intent(this, HomeActivity::class.java))
                                    }
                                    finish()
                                }
                        }
                    }
            }.onFailure { error ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
            }
        }


    }
}
