package com.example.workmonitoring.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.workmonitoring.R
import com.example.workmonitoring.viewmodel.FaceRegistrationViewModel
import com.example.workmonitoring.viewmodel.FaceRegistrationViewModelFactory
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.utils.FaceDetectionHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class FaceRegistrationActivity : AppCompatActivity() {
    private lateinit var instructionTextView: TextView
    private lateinit var progressTextView: TextView
    private lateinit var imagePreview: ImageView
    private lateinit var btnStartRegistration: Button
    private lateinit var btnRetake: Button
    private lateinit var btnSave: Button
    private lateinit var progressBar: ProgressBar

    private val viewModel: FaceRegistrationViewModel by viewModels {
        FaceRegistrationViewModelFactory(assets, FirebaseRepository(), FirebaseAuth.getInstance())
    }

    private var photoIndex = 0
    private var isRetaking = false
    private val instructions = listOf(
        "Смотри прямо в камеру и сделай фото",
        "Поверни голову чуть влево и сделай фото",
        "Поверни голову чуть вправо и сделай фото",
        "Наклони голову вниз и сделай фото",
        "Подними голову вверх и сделай фото",
        "Сделай легкую улыбку и сделай фото",
        "Закрой глаза на секунду и сделай фото",
        "Поверни голову сильнее влево и сделай фото",
        "Поверни голову сильнее вправо и сделай фото",
        "Вернись в исходное положение и сделай фото"
    )

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { capturedBitmap: Bitmap? ->
        if (capturedBitmap == null) {
            Log.e("FaceRegistration", "Ошибка: Фото не получено")
            enableButtons(true)
            return@registerForActivityResult
        }

        lifecycleScope.launch {
            val croppedFace = FaceDetectionHelper.detectFace(this@FaceRegistrationActivity, capturedBitmap)
            if (croppedFace == null) {
                Toast.makeText(this@FaceRegistrationActivity, "Лицо не найдено! Попробуйте снова.", Toast.LENGTH_SHORT).show()
                Log.e("FaceRegistration", "Лицо не обнаружено")
                enableButtons(true)
            } else {
                imagePreview.setImageBitmap(croppedFace)

                viewModel.processCapturedImage(
                    bitmap = croppedFace,
                    index = photoIndex,
                    context = this@FaceRegistrationActivity,
                    onFail = { error ->
                        Toast.makeText(this@FaceRegistrationActivity, error, Toast.LENGTH_SHORT).show()
                        enableButtons(true)
                        showRetakeButton()
                    }
                )

                // Увеличиваем индекс только если это не перефотографирование
                if (!isRetaking) {
                    photoIndex++
                }
                isRetaking = false
                updateProgress()
                
                if (photoIndex < instructions.size) {
                    instructionTextView.text = instructions[photoIndex]
                    showNextPhotoButton()
                    enableButtons(true)
                } else {
                    showSaveButton()
                    enableButtons(true)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_registration)

        instructionTextView = findViewById(R.id.instructionText)
        progressTextView = findViewById(R.id.progressText)
        imagePreview = findViewById(R.id.imagePreview)
        btnStartRegistration = findViewById(R.id.btnStartRegistration)
        btnRetake = findViewById(R.id.btnRetake)
        btnSave = findViewById(R.id.btnSave)
        progressBar = findViewById(R.id.progressBar)

        btnStartRegistration.setOnClickListener {
            enableButtons(false)
            hideRetakeButton()
            cameraLauncher.launch(null)
        }

        btnRetake.setOnClickListener {
            enableButtons(false)
            isRetaking = true
            // При перефотографировании уменьшаем индекс
            photoIndex--
            updateProgress()
            instructionTextView.text = instructions[photoIndex]
            cameraLauncher.launch(null)
        }

        btnSave.setOnClickListener {
            enableButtons(false)
            progressBar.visibility = View.VISIBLE
            viewModel.saveEmbeddings(
                onSuccess = {
                    Toast.makeText(this, "Регистрация завершена!", Toast.LENGTH_SHORT).show()
                    finish()
                },
                onFailure = { error ->
                    Toast.makeText(this, "Ошибка сохранения: $error", Toast.LENGTH_SHORT).show()
                    enableButtons(true)
                    progressBar.visibility = View.GONE
                }
            )
        }

        instructionTextView.text = instructions[photoIndex]
        updateProgress()
    }

    private fun updateProgress() {
        progressTextView.text = "Фото ${photoIndex + 1}/10"
    }

    private fun showRetakeButton() {
        btnRetake.visibility = View.VISIBLE
        btnStartRegistration.visibility = View.GONE
    }

    private fun hideRetakeButton() {
        btnRetake.visibility = View.GONE
        btnStartRegistration.visibility = View.VISIBLE
    }

    private fun showNextPhotoButton() {
        btnRetake.visibility = View.GONE
        btnStartRegistration.visibility = View.VISIBLE
        btnStartRegistration.text = "Сделать следующее фото"
    }

    private fun showSaveButton() {
        btnSave.visibility = View.VISIBLE
        btnStartRegistration.visibility = View.GONE
        btnRetake.visibility = View.GONE
    }

    private fun enableButtons(enabled: Boolean) {
        btnStartRegistration.isEnabled = enabled
        btnRetake.isEnabled = enabled
        btnSave.isEnabled = enabled
    }

    private fun saveEmbeddingsToFirestore(embeddings: List<FloatArray>) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val embeddingsList = embeddings.map { it.toList() }
            val userData = hashMapOf(
                "embeddings" to embeddingsList,
                "hasEmbeddings" to true
            )

            FirebaseFirestore.getInstance().collection("users").document(currentUser.uid)
                .update(userData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Фотографии успешно зарегистрированы", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
