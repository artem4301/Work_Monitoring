package com.example.workmonitoring.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.workmonitoring.R
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.face.FaceNetModel
import com.google.firebase.auth.FirebaseAuth

class FaceControlActivity : AppCompatActivity() {
    private lateinit var imagePreview: ImageView
    private lateinit var similarityTextView: TextView
    private val viewModel: FaceControlViewModel by viewModels {
        FaceControlViewModelFactory(FaceNetModel(assets), FirebaseRepository(), FirebaseAuth.getInstance())
    }

    // Запуск камеры
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { capturedBitmap: Bitmap? ->
        capturedBitmap?.let { viewModel.processCapturedImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_control)

        imagePreview = findViewById(R.id.imagePreview)
        similarityTextView = findViewById(R.id.similarityTextView)
        val btnCaptureFace = findViewById<Button>(R.id.btnCaptureFace)
        val btnBack = findViewById<Button>(R.id.btnBack)

        // Запуск камеры
        btnCaptureFace.setOnClickListener { cameraLauncher.launch(null) }

        // Назад
        btnBack.setOnClickListener { finish() }

        // Отображение захваченного изображения
        viewModel.faceBitmap.observe(this) { detectedFace ->
            detectedFace?.let { imagePreview.setImageBitmap(it) }
        }

        // Отображение результата распознавания
        viewModel.verificationResult.observe(this) { result ->
            result.onSuccess { similarityScore ->
                similarityTextView.text = "Схожесть: ${"%.2f".format(similarityScore)}%"
                if (similarityScore >= 70) {
                    Toast.makeText(this, "Фотоконтроль пройден!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Фотоконтроль не пройден!", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { error ->
                Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
