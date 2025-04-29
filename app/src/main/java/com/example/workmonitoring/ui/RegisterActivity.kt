package com.example.workmonitoring.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.workmonitoring.R
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.viewmodel.RegisterViewModel
import com.example.workmonitoring.viewmodel.RegisterViewModelFactory
import com.google.firebase.auth.FirebaseAuth

class RegisterActivity : AppCompatActivity() {
    private val registerViewModel: RegisterViewModel by viewModels {
        RegisterViewModelFactory(FirebaseRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val firstNameInput = findViewById<EditText>(R.id.firstNameInput)
        val lastNameInput = findViewById<EditText>(R.id.lastNameInput)
        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val btnSignUp = findViewById<Button>(R.id.btnSignUp)
        val roleGroup = findViewById<RadioGroup>(R.id.roleGroup)

        btnSignUp.setOnClickListener {
            val firstName = firstNameInput.text.toString().trim()
            val lastName = lastNameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val role = when (roleGroup.checkedRadioButtonId) {
                R.id.radioManager -> "manager"
                else -> "worker"
            }

            if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Заполните все поля!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            registerViewModel.register(firstName, lastName, email, password, role)
        }


        registerViewModel.registerResult.observe(this) { result ->
            result.onSuccess { role ->
                Toast.makeText(this, "Регистрация успешна!", Toast.LENGTH_SHORT).show()

                if (role == "manager") {
                    startActivity(Intent(this, ManagerHomeActivity::class.java)) // управляющий
                } else {
                    startActivity(Intent(this, HomeActivity::class.java)) // работник
                }

                finish()
            }.onFailure { error ->
                Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
            }
        }

    }
}
