package com.example.workmonitoring.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.workmonitoring.R
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.face.FaceNetModel
import com.example.workmonitoring.utils.ImageQualityChecker
import com.example.workmonitoring.viewmodel.FaceControlViewModel
import com.example.workmonitoring.viewmodel.FaceControlViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.button.MaterialButton

class FaceControlActivity : AppCompatActivity() {

    private lateinit var imagePreview: ImageView
    private lateinit var similarityTextView: TextView
    private lateinit var qualityTextView: TextView
    private lateinit var progressBar: View
    private val repository = FirebaseRepository()
    private var isPeriodicVerification = false

    private val viewModel: FaceControlViewModel by viewModels {
        FaceControlViewModelFactory(
            FaceNetModel(assets),
            FirebaseRepository(),
            FirebaseAuth.getInstance()
        )
    }

    // Камера
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { capturedBitmap: Bitmap? ->
        capturedBitmap?.let { bitmap ->
            progressBar.visibility = View.VISIBLE
            viewModel.processCapturedImage(bitmap, this)
        } ?: Toast.makeText(this, "Ошибка получения фото", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_control)

        initializeViews()
        setupClickListeners()
        observeViewModel()
        
        // Проверяем, это периодическая верификация или обычная
        isPeriodicVerification = intent.getBooleanExtra("periodic_verification", false)
        
        if (isPeriodicVerification) {
            findViewById<TextView>(R.id.titleText)?.text = "Периодическая верификация"
            findViewById<TextView>(R.id.instructionText)?.text = "Пройдите фотоконтроль для продолжения смены"
        }
    }

    private fun initializeViews() {
        imagePreview = findViewById(R.id.imagePreview)
        similarityTextView = findViewById(R.id.similarityTextView)
        qualityTextView = findViewById(R.id.qualityTextView)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupClickListeners() {
        findViewById<MaterialButton>(R.id.btnCaptureFace).setOnClickListener {
            cameraLauncher.launch(null)
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            if (isPeriodicVerification) {
                // При периодической верификации нельзя просто выйти
                showPeriodicVerificationWarning()
            } else {
                finish()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.faceBitmap.observe(this) { detectedFace ->
            detectedFace?.let { imagePreview.setImageBitmap(it) }
        }

        viewModel.qualityResult.observe(this) { result ->
            updateQualityDisplay(result)
        }

        viewModel.verificationResult.observe(this) { result ->
            progressBar.visibility = View.GONE
            
            result.onSuccess { resultData ->
                @Suppress("UNCHECKED_CAST")
                val metrics = resultData["metrics"] as Map<String, Float>
                val isVerified = resultData["isVerified"] as Boolean
                val bestMetric = resultData["bestMetric"] as String
                val bestScore = resultData["bestScore"] as Float
                
                displayVerificationResults(metrics, isVerified)
                
                // Сохраняем результат верификации
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId != null) {
                    repository.saveVerificationResult(
                        userId = userId,
                        success = isVerified,
                        similarity = bestScore.toDouble(),
                        method = bestMetric,
                        callback = { saved ->
                            if (saved && isVerified) {
                                handleSuccessfulVerification()
                            } else if (!isVerified) {
                                handleFailedVerification()
                            }
                        }
                    )
                }
                
            }.onFailure { error ->
                Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateQualityDisplay(result: ImageQualityChecker.QualityResult) {
        val qualityText = buildString {
            appendLine("Качество изображения: ${result.overallScore.toInt()}%")
            appendLine("Яркость: ${result.brightness.toInt()}")
            appendLine("Контраст: ${result.contrast.toInt()}")
            appendLine("Резкость: ${result.sharpness.toInt()}")
            if (result.issues.isNotEmpty()) {
                appendLine("Проблемы:")
                result.issues.forEach { appendLine("• $it") }
            }
        }
        qualityTextView.text = qualityText
    }

    private fun displayVerificationResults(results: Map<String, Float>, isVerified: Boolean) {
        val resultText = buildString {
            appendLine("Результаты сравнения:")
            results.forEach { (metric, score) ->
                appendLine("$metric: ${"%.2f".format(score)}%")
            }
            appendLine()
            val cosineScore = results["Косинусная"] ?: 0f
            appendLine("Верификация: ${if (isVerified) "УСПЕШНА" else "НЕУДАЧНА"}")
            appendLine("Допуск к смене: ${if (cosineScore >= 75f) "РАЗРЕШЕН" else "ЗАПРЕЩЕН"}")
            appendLine("(Требуется косинусная схожесть ≥ 75%)")
        }
        
        similarityTextView.text = resultText
    }

    private fun handleSuccessfulVerification() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        if (isPeriodicVerification) {
            // Возобновляем смену после успешной периодической верификации
            repository.resumeShift(userId) { success ->
                if (success) {
                    Toast.makeText(this, "Верификация пройдена! Смена возобновлена.", Toast.LENGTH_SHORT).show()
                    // Возвращаемся к WorkTimeActivity
                    val intent = Intent(this, WorkTimeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, "Ошибка возобновления смены", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            // Обычная верификация - показываем диалог начала смены
            showStartShiftDialog()
        }
    }

    private fun handleFailedVerification() {
        if (isPeriodicVerification) {
            AlertDialog.Builder(this)
                .setTitle("Верификация не пройдена")
                .setMessage("Смена приостановлена. Попробуйте еще раз или обратитесь к администратору.")
                .setPositiveButton("Попробовать снова") { _, _ ->
                    cameraLauncher.launch(null)
                }
                .setCancelable(false)
                .show()
        } else {
            Toast.makeText(this, "Верификация не пройдена", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPeriodicVerificationWarning() {
        AlertDialog.Builder(this)
            .setTitle("Внимание")
            .setMessage("Для продолжения смены необходимо пройти верификацию. Смена приостановлена до прохождения проверки.")
            .setPositiveButton("Пройти верификацию") { _, _ ->
                cameraLauncher.launch(null)
            }
            .setCancelable(false)
            .show()
    }

    private fun showStartShiftDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_start_shift, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<MaterialButton>(R.id.btnStartShift).setOnClickListener {
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (userId != null) {
                repository.startShift(userId) { success ->
                    if (success) {
                        Toast.makeText(this, "Смена начата", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        // Запускаем WorkTimeActivity
                        val intent = Intent(this, WorkTimeActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, "Ошибка при начале смены", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialogView.findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}