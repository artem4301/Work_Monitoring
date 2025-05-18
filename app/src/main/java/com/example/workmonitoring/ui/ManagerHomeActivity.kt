package com.example.workmonitoring.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.workmonitoring.R
import com.example.workmonitoring.data.FirebaseRepository
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth

class ManagerHomeActivity : AppCompatActivity() {

    private lateinit var textManagerName: TextView
    private lateinit var textManagerRole: TextView
    private val repository = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manager_home)

        initializeViews()
        loadManagerData()
        setupClickListeners()
    }

    private fun initializeViews() {
        textManagerName = findViewById(R.id.textManagerName)
        textManagerRole = findViewById(R.id.textManagerRole)
    }

    private fun loadManagerData() {
        repository.getCurrentUser { user ->
            if (user != null) {
                textManagerName.text = "${user.firstName} ${user.lastName}"
                textManagerRole.text = "Менеджер"
            }
        }
    }

    private fun setupClickListeners() {
        findViewById<MaterialButton>(R.id.btnAddWorkers).setOnClickListener {
            startActivity(Intent(this, AddWorkerActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnAssignZone).setOnClickListener {
            startActivity(Intent(this, ZoneSelectorActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnReport).setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
