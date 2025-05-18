package com.example.workmonitoring.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.workmonitoring.R
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.model.User
import com.example.workmonitoring.viewmodel.AddWorkerViewModel
import com.example.workmonitoring.viewmodel.AddWorkerViewModelFactory
import com.google.android.material.textfield.TextInputLayout

class AddWorkerActivity : AppCompatActivity() {

    private val viewModel: AddWorkerViewModel by viewModels {
        AddWorkerViewModelFactory(FirebaseRepository())
    }

    private lateinit var workersSpinner: AutoCompleteTextView
    private lateinit var spinnerLayout: TextInputLayout
    private lateinit var sendRequestButton: Button
    private lateinit var progressBar: ProgressBar

    private var selectedWorkerId: String? = null
    private var workersList: List<User> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_worker)

        workersSpinner = findViewById(R.id.workersSpinner)
        spinnerLayout = findViewById(R.id.spinnerLayout)
        sendRequestButton = findViewById(R.id.sendRequestButton)
        progressBar = findViewById(R.id.progressBar)
        progressBar.visibility = View.VISIBLE

        viewModel.loadWorkers()

        viewModel.workers.observe(this) { workers ->
            progressBar.visibility = View.GONE
            workersList = workers
            if (workers.isNotEmpty()) {
                val namesList = workers.map { "${it.firstName} ${it.lastName}" }
                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, namesList)
                workersSpinner.setAdapter(adapter)

                workersSpinner.setOnItemClickListener { _, _, position, _ ->
                    selectedWorkerId = workers[position].uid
                }
            } else {
                spinnerLayout.error = "Нет доступных работников"
            }
        }

        sendRequestButton.setOnClickListener {
            val workerId = selectedWorkerId
            if (workerId != null) {
                progressBar.visibility = View.VISIBLE
                viewModel.sendRequest(workerId) { success ->
                    progressBar.visibility = View.GONE
                    if (success) {
                        Toast.makeText(this, "Запрос отправлен", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this, "Ошибка отправки запроса", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Выберите работника", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
