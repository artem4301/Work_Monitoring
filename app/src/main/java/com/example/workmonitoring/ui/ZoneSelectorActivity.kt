package com.example.workmonitoring.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.workmonitoring.R
import com.example.workmonitoring.adapter.WorkerZoneAdapter
import com.example.workmonitoring.viewmodel.ZoneSelectorViewModel

class ZoneSelectorActivity : AppCompatActivity() {

    private val viewModel: ZoneSelectorViewModel by viewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WorkerZoneAdapter

    // ✅ Добавляем лаунчер для получения результата из AssignZoneActivity
    private val assignZoneLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Это сработает когда пользователь вернется из карты
        viewModel.loadApprovedWorkers() // Снова загружаем список работников
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zone_selector)

        recyclerView = findViewById(R.id.recyclerViewWorkers)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = WorkerZoneAdapter { worker ->
            openAssignZoneActivity(worker.uid)
        }

        recyclerView.adapter = adapter

        observeViewModel()

        viewModel.loadApprovedWorkers()
    }

    private fun observeViewModel() {
        viewModel.workers.observe(this) { workers ->
            adapter.submitList(workers)
        }

        viewModel.error.observe(this) { errorMessage ->
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAssignZoneActivity(workerId: String) {
        val intent = Intent(this, AssignZoneActivity::class.java)
        intent.putExtra("workerId", workerId)
        assignZoneLauncher.launch(intent) // ✅ вместо обычного startActivity
    }
}
