package com.example.workmonitoring.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.workmonitoring.R
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.viewmodel.AddWorkerViewModel
import com.example.workmonitoring.viewmodel.AddWorkerViewModelFactory

class AddWorkerActivity : AppCompatActivity() {

    private val viewModel: AddWorkerViewModel by viewModels {
        AddWorkerViewModelFactory(FirebaseRepository())
    }

    private lateinit var workersSpinner: Spinner
    private lateinit var sendRequestButton: Button
    private lateinit var progressBar: ProgressBar

    private var selectedWorkerId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_worker)

        workersSpinner = findViewById(R.id.workersSpinner)
        sendRequestButton = findViewById(R.id.sendRequestButton)
        progressBar = findViewById(R.id.progressBar)
        progressBar.visibility = View.VISIBLE

        viewModel.loadWorkers()

        viewModel.workers.observe(this) { workersList ->
            progressBar.visibility = View.GONE
            if (workersList.isNotEmpty()) {
                val namesList = workersList.map { "${it.firstName} ${it.lastName}" }
                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, namesList)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                workersSpinner.adapter = adapter

                workersSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        selectedWorkerId = workersList[position].uid
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {
                        selectedWorkerId = null
                    }
                }
            } else {
                Toast.makeText(this, "Нет доступных работников", Toast.LENGTH_SHORT).show()
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
