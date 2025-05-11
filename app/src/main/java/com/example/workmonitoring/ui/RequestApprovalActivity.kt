package com.example.workmonitoring.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.workmonitoring.R
import com.example.workmonitoring.viewmodel.RequestApprovalViewModel
import com.google.firebase.firestore.FirebaseFirestore

class RequestApprovalActivity : AppCompatActivity() {

    private val viewModel: RequestApprovalViewModel by viewModels()

    private lateinit var textRequestInfo: TextView
    private lateinit var btnApprove: Button
    private lateinit var btnReject: Button
    private lateinit var progressBar: ProgressBar

    private var requestId: String? = null
    private var managerId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_approval)

        textRequestInfo = findViewById(R.id.textRequestInfo)
        btnApprove = findViewById(R.id.btnApprove)
        btnReject = findViewById(R.id.btnReject)
        progressBar = findViewById(R.id.progressBar)

        progressBar.visibility = View.VISIBLE

        viewModel.loadRequest()

        viewModel.requestStatus.observe(this) { state ->
            when (state) {
                is RequestApprovalViewModel.RequestState.Success -> {
                    requestId = state.requestId
                    managerId = state.managerId
                    loadManagerName(managerId)
                    progressBar.visibility = View.GONE
                    btnApprove.visibility = View.VISIBLE
                    btnReject.visibility = View.VISIBLE
                }
                is RequestApprovalViewModel.RequestState.NoRequest -> {
                    Toast.makeText(this, "Запросов нет.", Toast.LENGTH_SHORT).show()
                    goToHome()
                }
                is RequestApprovalViewModel.RequestState.Error -> {
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                    goToHome()
                }
                is RequestApprovalViewModel.RequestState.Updated -> {
                    Toast.makeText(this, "Ответ отправлен!", Toast.LENGTH_SHORT).show()
                    goToHome()
                }
            }
        }

        btnApprove.setOnClickListener {
            requestId?.let { id ->
                viewModel.updateRequestStatus(id, "approved")
            }
        }

        btnReject.setOnClickListener {
            requestId?.let { id ->
                viewModel.updateRequestStatus(id, "rejected")
            }
        }
    }

    private fun loadManagerName(managerId: String?) {
        if (managerId == null) {
            textRequestInfo.text = "Вас пригласил управляющий. Принять запрос?"
            return
        }
        val db = FirebaseFirestore.getInstance()
        db.collection("users")
            .document(managerId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val firstName = document.getString("firstName") ?: ""
                    val lastName = document.getString("lastName") ?: ""
                    textRequestInfo.text = "Вас пригласил управляющий: $lastName $firstName\nПринять запрос?"
                } else {
                    textRequestInfo.text = "Вас пригласил управляющий. Принять запрос?"
                }
            }
            .addOnFailureListener {
                textRequestInfo.text = "Вас пригласил управляющий. Принять запрос?"
            }
    }

    private fun goToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
