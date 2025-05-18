package com.example.workmonitoring.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.workmonitoring.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var emailEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var loginButton: MaterialButton
    private lateinit var registerButton: MaterialButton
    private lateinit var resetPasswordButton: MaterialButton
    private lateinit var progressBar: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Проверяем, авторизован ли пользователь
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
                        // Пользователь залогинен, но нет данных в Firestore
                        auth.signOut()
                        Toast.makeText(this, "Пользователь не найден. Войдите снова.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    auth.signOut()
                    Toast.makeText(this, "Ошибка при получении данных пользователя. Войдите снова.", Toast.LENGTH_SHORT).show()
                }
            return
        }

        // Инициализация views
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)
        resetPasswordButton = findViewById(R.id.resetPasswordButton)
        progressBar = findViewById(R.id.progressBar)

        // Обработчики нажатий
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Пожалуйста, заполните все поля", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showLoading(true)
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val userId = auth.currentUser?.uid
                        if (userId != null) {
                            db.collection("users").document(userId).get()
                                .addOnSuccessListener { document ->
                                    showLoading(false)
                                    if (document.exists()) {
                                        val role = document.getString("role")
                                        if (role == "manager") {
                                            startActivity(Intent(this, ManagerHomeActivity::class.java))
                                        } else {
                                            startActivity(Intent(this, HomeActivity::class.java))
                                        }
                                        finish()
                                    } else {
                                        Toast.makeText(this, "Пользователь не найден", Toast.LENGTH_SHORT).show()
                                        auth.signOut()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    showLoading(false)
                                    Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                    auth.signOut()
                                }
                        }
                    } else {
                        showLoading(false)
                        Toast.makeText(this, "Ошибка входа: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        resetPasswordButton.setOnClickListener {
            startActivity(Intent(this, ResetPasswordActivity::class.java))
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        loginButton.isEnabled = !show
        registerButton.isEnabled = !show
        resetPasswordButton.isEnabled = !show
    }
}
