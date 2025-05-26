package com.example.workmonitoring.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.workmonitoring.R
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.utils.NavigationHelper
import com.example.workmonitoring.viewmodel.LoginViewModel
import com.example.workmonitoring.viewmodel.LoginViewModelFactory
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var emailEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var loginButton: MaterialButton
    private lateinit var registerButton: MaterialButton
    private lateinit var resetPasswordButton: MaterialButton
    private lateinit var progressBar: View

    private val viewModel: LoginViewModel by viewModels {
        LoginViewModelFactory(FirebaseRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        initializeViews()
        
        // Проверяем, авторизован ли пользователь
        if (FirebaseAuth.getInstance().currentUser != null) {
            checkUserRoleAndNavigate()
            return
        }

        setupClickListeners()
        observeViewModel()
    }

    private fun initializeViews() {
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        registerButton = findViewById(R.id.registerButton)
        resetPasswordButton = findViewById(R.id.resetPasswordButton)
        progressBar = findViewById(R.id.progressBar) ?: throw IllegalStateException("ProgressBar not found in layout")
    }

    private fun setupClickListeners() {
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()
            viewModel.login(email, password)
        }

        registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        resetPasswordButton.setOnClickListener {
            startActivity(Intent(this, ResetPasswordActivity::class.java))
        }
    }

    private fun observeViewModel() {
        viewModel.loginResult.observe(this) { result ->
            showLoading(false)
            
            result.onSuccess {
                checkUserRoleAndNavigate()
            }.onFailure { error ->
                Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkUserRoleAndNavigate() {
        showLoading(true)
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        
        if (userId != null) {
            viewModel.loadUserRole(userId) { role ->
                if (role == "manager") {
                    showLoading(false)
                    val intent = Intent(this, ManagerHomeActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    // Для работников проверяем активную смену
                    checkActiveShiftAndNavigate(userId)
                }
            }
        } else {
            showLoading(false)
            Toast.makeText(this, "Ошибка авторизации", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkActiveShiftAndNavigate(userId: String) {
        val repository = FirebaseRepository()
        repository.getCurrentUser { user ->
            showLoading(false)
            
            if (user != null) {
                // Отладочные логи
                android.util.Log.d("LoginActivity", "User data loaded:")
                android.util.Log.d("LoginActivity", "role: ${user.role}")
                android.util.Log.d("LoginActivity", "isActive: ${user.isActive}")
                android.util.Log.d("LoginActivity", "shiftStartTime: ${user.shiftStartTime}")
                
                val intent = NavigationHelper.getAppropriateIntent(this, user)
                android.util.Log.d("LoginActivity", "Navigating to: ${intent.component?.className}")
                startActivity(intent)
                finish()
            } else {
                Toast.makeText(this, "Ошибка загрузки данных пользователя", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        if (::progressBar.isInitialized) {
            progressBar.visibility = if (show) View.VISIBLE else View.GONE
        }
        if (::loginButton.isInitialized) {
            loginButton.isEnabled = !show
        }
        if (::registerButton.isInitialized) {
            registerButton.isEnabled = !show
        }
        if (::resetPasswordButton.isInitialized) {
            resetPasswordButton.isEnabled = !show
        }
    }
}
