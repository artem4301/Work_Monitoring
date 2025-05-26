package com.example.workmonitoring.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.workmonitoring.R
import com.example.workmonitoring.data.FirebaseRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*

class ReportActivity : AppCompatActivity() {

    private lateinit var repository: FirebaseRepository
    private lateinit var dateFromButton: MaterialButton
    private lateinit var dateToButton: MaterialButton
    private lateinit var generateReportButton: MaterialButton
    private lateinit var progressBar: View
    private lateinit var reportContainer: View
    private lateinit var noDataText: TextView
    
    // Статистика
    private lateinit var totalWorkersText: TextView
    private lateinit var activeWorkersText: TextView
    private lateinit var totalWorkTimeText: TextView
    private lateinit var totalVerificationsText: TextView
    private lateinit var successfulVerificationsText: TextView
    private lateinit var averageWorkTimeText: TextView
    
    private lateinit var workersRecyclerView: RecyclerView
    
    private var startDate: Long = 0L
    private var endDate: Long = 0L
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)
        
        repository = FirebaseRepository()
        initializeViews()
        setupClickListeners()
        
        // Устанавливаем даты по умолчанию (широкий диапазон для поиска всех записей)
        val calendar = Calendar.getInstance()
        calendar.set(2030, Calendar.DECEMBER, 31) // Конец 2030 года
        endDate = calendar.timeInMillis
        calendar.set(2020, Calendar.JANUARY, 1) // Начало 2020 года
        startDate = calendar.timeInMillis
        
        updateDateButtons()
    }

    private fun initializeViews() {
        dateFromButton = findViewById(R.id.dateFromButton)
        dateToButton = findViewById(R.id.dateToButton)
        generateReportButton = findViewById(R.id.generateReportButton)
        progressBar = findViewById(R.id.progressBar)
        reportContainer = findViewById(R.id.reportContainer)
        noDataText = findViewById(R.id.noDataText)
        
        // Статистика
        totalWorkersText = findViewById(R.id.totalWorkersText)
        activeWorkersText = findViewById(R.id.activeWorkersText)
        totalWorkTimeText = findViewById(R.id.totalWorkTimeText)
        totalVerificationsText = findViewById(R.id.totalVerificationsText)
        successfulVerificationsText = findViewById(R.id.successfulVerificationsText)
        averageWorkTimeText = findViewById(R.id.averageWorkTimeText)
        
        workersRecyclerView = findViewById(R.id.workersRecyclerView)
        workersRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
        dateFromButton.setOnClickListener {
            showDatePicker(true)
        }
        
        dateToButton.setOnClickListener {
            showDatePicker(false)
        }
        
        // Добавляем диагностическую кнопку (временно)
        findViewById<View>(R.id.btnBack).setOnLongClickListener {
            runDiagnostics()
            true
        }
        
        // Добавляем кнопку исправления данных (двойное нажатие на кнопку генерации отчета)
        var lastClickTime = 0L
        generateReportButton.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime < 1000) { // Двойное нажатие
                fixTimestamps()
            } else {
                generateReport()
            }
            lastClickTime = currentTime
        }
        
        findViewById<View>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun showDatePicker(isStartDate: Boolean) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = if (isStartDate) startDate else endDate
        
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(year, month, dayOfMonth)
                
                if (isStartDate) {
                    startDate = selectedCalendar.timeInMillis
                } else {
                    selectedCalendar.set(Calendar.HOUR_OF_DAY, 23)
                    selectedCalendar.set(Calendar.MINUTE, 59)
                    selectedCalendar.set(Calendar.SECOND, 59)
                    endDate = selectedCalendar.timeInMillis
                }
                
                updateDateButtons()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateButtons() {
        dateFromButton.text = "С: ${dateFormat.format(Date(startDate))}"
        dateToButton.text = "По: ${dateFormat.format(Date(endDate))}"
    }

    private fun generateReport() {
        if (startDate >= endDate) {
            Toast.makeText(this, "Дата начала должна быть раньше даты окончания", Toast.LENGTH_SHORT).show()
            return
        }
        
        android.util.Log.d("ReportActivity", "Генерация отчета за период: ${dateFormat.format(Date(startDate))} - ${dateFormat.format(Date(endDate))}")
        android.util.Log.d("ReportActivity", "Период в миллисекундах: $startDate - $endDate")
        
        progressBar.visibility = View.VISIBLE
        reportContainer.visibility = View.GONE
        noDataText.visibility = View.GONE
        
        // Получаем список работников, связанных с менеджером
        repository.getAssignedWorkers { workers ->
            android.util.Log.d("ReportActivity", "Найдено работников: ${workers.size}")
            workers.forEach { worker ->
                android.util.Log.d("ReportActivity", "Работник: ${worker.firstName} ${worker.lastName} (${worker.uid})")
            }
            
            if (workers.isEmpty()) {
                showNoData("У вас нет назначенных работников")
                return@getAssignedWorkers
            }
            
            val workerReports = mutableListOf<WorkerReportData>()
            var processedWorkers = 0
            
            workers.forEach { worker ->
                android.util.Log.d("ReportActivity", "Запрашиваем отчет для работника: ${worker.firstName} ${worker.lastName}")
                repository.getWorkerReport(
                    workerId = worker.uid,
                    startDate = startDate,
                    endDate = endDate
                ) { report ->
                    processedWorkers++
                    android.util.Log.d("ReportActivity", "Получен отчет для работника ${worker.firstName}: $report")
                    
                    if (!report.containsKey("error")) {
                        val workTimeHistory = report["workTimeHistory"] as? List<Map<String, Any>> ?: emptyList()
                        val verificationHistory = report["verificationHistory"] as? List<Map<String, Any>> ?: emptyList()
                        val totalWorkTime = report["totalWorkTime"] as? Long ?: 0L
                        val totalVerifications = report["totalVerifications"] as? Int ?: 0
                        val successfulVerifications = report["successfulVerifications"] as? Int ?: 0
                        
                        android.util.Log.d("ReportActivity", "Данные работника ${worker.firstName}: workTime=$totalWorkTime, workDays=${workTimeHistory.size}")
                        
                        workerReports.add(
                            WorkerReportData(
                                worker = worker,
                                totalWorkTime = totalWorkTime,
                                totalVerifications = totalVerifications,
                                successfulVerifications = successfulVerifications,
                                workDays = workTimeHistory.size,
                                averageWorkTime = if (workTimeHistory.isNotEmpty()) totalWorkTime / workTimeHistory.size else 0L
                            )
                        )
                    } else {
                        android.util.Log.e("ReportActivity", "Ошибка в отчете для работника ${worker.firstName}: ${report["error"]}")
                    }
                    
                    // Когда все работники обработаны
                    if (processedWorkers == workers.size) {
                        android.util.Log.d("ReportActivity", "Все работники обработаны. Отчетов: ${workerReports.size}")
                        runOnUiThread {
                            displayReport(workerReports)
                        }
                    }
                }
            }
        }
    }

    private fun displayReport(workerReports: List<WorkerReportData>) {
        progressBar.visibility = View.GONE
        
        if (workerReports.isEmpty()) {
            showNoData("Нет данных за выбранный период")
            return
        }
        
        reportContainer.visibility = View.VISIBLE
        
        // Общая статистика
        val totalWorkers = workerReports.size
        val activeWorkers = workerReports.count { it.totalWorkTime > 0 }
        val totalWorkTime = workerReports.sumOf { it.totalWorkTime }
        val totalVerifications = workerReports.sumOf { it.totalVerifications }
        val successfulVerifications = workerReports.sumOf { it.successfulVerifications }
        val averageWorkTime = if (activeWorkers > 0) totalWorkTime / activeWorkers else 0L
        
        totalWorkersText.text = totalWorkers.toString()
        activeWorkersText.text = activeWorkers.toString()
        totalWorkTimeText.text = formatDuration(totalWorkTime)
        totalVerificationsText.text = totalVerifications.toString()
        successfulVerificationsText.text = "$successfulVerifications (${if (totalVerifications > 0) (successfulVerifications * 100 / totalVerifications) else 0}%)"
        averageWorkTimeText.text = formatDuration(averageWorkTime)
        
        // Список работников
        val adapter = WorkerReportAdapter(workerReports)
        workersRecyclerView.adapter = adapter
    }

    private fun showNoData(message: String) {
        progressBar.visibility = View.GONE
        reportContainer.visibility = View.GONE
        noDataText.visibility = View.VISIBLE
        noDataText.text = message
    }

    private fun formatDuration(milliseconds: Long): String {
        val hours = milliseconds / (1000 * 60 * 60)
        val minutes = (milliseconds % (1000 * 60 * 60)) / (1000 * 60)
        return "${hours}ч ${minutes}м"
    }
    
    private fun runDiagnostics() {
        android.util.Log.d("ReportActivity", "=== ДИАГНОСТИКА ===")
        
        // Проверяем текущего пользователя
        repository.getCurrentUser { user ->
            android.util.Log.d("ReportActivity", "Текущий пользователь: ${user?.firstName} ${user?.lastName} (${user?.uid}), роль: ${user?.role}")
            
            // Проверяем все запросы для этого менеджера
            val managerId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (managerId != null) {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("requests")
                    .whereEqualTo("managerId", managerId)
                    .get()
                    .addOnSuccessListener { requests ->
                        android.util.Log.d("ReportActivity", "Всего запросов для менеджера: ${requests.size()}")
                        requests.forEach { request ->
                            android.util.Log.d("ReportActivity", "Запрос: workerId=${request.getString("workerId")}, status=${request.getString("status")}")
                        }
                        
                        // Проверяем всех работников
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("users")
                            .whereEqualTo("role", "worker")
                            .get()
                            .addOnSuccessListener { workers ->
                                android.util.Log.d("ReportActivity", "Всего работников в системе: ${workers.size()}")
                                workers.forEach { worker ->
                                    android.util.Log.d("ReportActivity", "Работник: ${worker.getString("firstName")} ${worker.getString("lastName")} (${worker.id})")
                                    
                                    // Проверяем историю рабочего времени для каждого работника
                                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                        .collection("users")
                                        .document(worker.id)
                                        .collection("workTimeHistory")
                                        .get()
                                        .addOnSuccessListener { workTimeHistory ->
                                            android.util.Log.d("ReportActivity", "  - История рабочего времени: ${workTimeHistory.size()} записей")
                                            workTimeHistory.forEach { entry ->
                                                android.util.Log.d("ReportActivity", "    Запись: ${entry.data}")
                                            }
                                        }
                                }
                            }
                    }
            }
        }
        
        // Тестируем новый метод getAssignedWorkers
        android.util.Log.d("ReportActivity", "=== ТЕСТ getAssignedWorkers ===")
        repository.getAssignedWorkers { assignedWorkers ->
            android.util.Log.d("ReportActivity", "getAssignedWorkers вернул: ${assignedWorkers.size} работников")
            assignedWorkers.forEach { worker ->
                android.util.Log.d("ReportActivity", "Назначенный работник: ${worker.firstName} ${worker.lastName} (${worker.uid})")
            }
        }
        
        Toast.makeText(this, "Диагностика запущена, смотрите логи", Toast.LENGTH_SHORT).show()
    }
    
    private fun fixTimestamps() {
        android.util.Log.d("ReportActivity", "=== ИСПРАВЛЕНИЕ ВРЕМЕННЫХ МЕТОК ===")
        Toast.makeText(this, "Исправление временных меток...", Toast.LENGTH_SHORT).show()
        
        val currentTime = System.currentTimeMillis()
        val oneHour = 60 * 60 * 1000L // 1 час в миллисекундах
        
        // Получаем всех работников
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users")
            .whereEqualTo("role", "worker")
            .get()
            .addOnSuccessListener { workers ->
                workers.forEach { worker ->
                    // Проверяем историю рабочего времени каждого работника
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(worker.id)
                        .collection("workTimeHistory")
                        .get()
                        .addOnSuccessListener { workTimeHistory ->
                            workTimeHistory.forEach { entry ->
                                val startTime = entry.getLong("startTime") ?: 0L
                                val endTime = entry.getLong("endTime") ?: 0L
                                val duration = entry.getLong("duration") ?: 0L
                                
                                // Если временные метки в будущем (больше текущего времени + 1 день)
                                if (startTime > currentTime + 24 * 60 * 60 * 1000L) {
                                    android.util.Log.d("ReportActivity", "Исправляем запись ${entry.id}: startTime=$startTime")
                                    
                                    // Исправляем: делаем запись за вчера
                                    val correctedEndTime = currentTime - 24 * 60 * 60 * 1000L // Вчера
                                    val correctedStartTime = correctedEndTime - duration
                                    
                                    val updates = hashMapOf<String, Any>(
                                        "startTime" to correctedStartTime,
                                        "endTime" to correctedEndTime,
                                        "date" to java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(correctedStartTime))
                                    )
                                    
                                    entry.reference.update(updates)
                                        .addOnSuccessListener {
                                            android.util.Log.d("ReportActivity", "Запись ${entry.id} исправлена")
                                        }
                                        .addOnFailureListener { e ->
                                            android.util.Log.e("ReportActivity", "Ошибка исправления записи ${entry.id}", e)
                                        }
                                }
                            }
                        }
                }
                
                Toast.makeText(this, "Исправление завершено, попробуйте сгенерировать отчет", Toast.LENGTH_LONG).show()
            }
    }

    data class WorkerReportData(
        val worker: com.example.workmonitoring.model.User,
        val totalWorkTime: Long,
        val totalVerifications: Int,
        val successfulVerifications: Int,
        val workDays: Int,
        val averageWorkTime: Long
    )

    inner class WorkerReportAdapter(
        private val reports: List<WorkerReportData>
    ) : RecyclerView.Adapter<WorkerReportAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val nameText: TextView = itemView.findViewById(R.id.workerNameText)
            val workTimeText: TextView = itemView.findViewById(R.id.workTimeText)
            val verificationsText: TextView = itemView.findViewById(R.id.verificationsText)
            val workDaysText: TextView = itemView.findViewById(R.id.workDaysText)
            val averageTimeText: TextView = itemView.findViewById(R.id.averageTimeText)
            val card: MaterialCardView = itemView.findViewById(R.id.workerCard)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_worker_report, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val report = reports[position]
            
            holder.nameText.text = "${report.worker.lastName} ${report.worker.firstName}"
            holder.workTimeText.text = formatDuration(report.totalWorkTime)
            holder.verificationsText.text = "${report.successfulVerifications}/${report.totalVerifications}"
            holder.workDaysText.text = report.workDays.toString()
            holder.averageTimeText.text = formatDuration(report.averageWorkTime)
            
            // Цветовая индикация эффективности
            val efficiency = if (report.totalVerifications > 0) {
                report.successfulVerifications.toFloat() / report.totalVerifications
            } else 1f
            
            val cardColor = when {
                efficiency >= 0.9f -> android.R.color.holo_green_light
                efficiency >= 0.7f -> android.R.color.holo_orange_light
                else -> android.R.color.holo_red_light
            }
            
            holder.card.setCardBackgroundColor(getColor(cardColor))
        }

        override fun getItemCount() = reports.size
    }
}