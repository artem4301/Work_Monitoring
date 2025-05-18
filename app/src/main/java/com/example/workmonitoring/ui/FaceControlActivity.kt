package com.example.workmonitoring.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.workmonitoring.R
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.face.FaceNetModel
import com.example.workmonitoring.viewmodel.FaceControlViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.button.MaterialButton

class FaceControlActivity : AppCompatActivity() {

    private lateinit var imagePreview: ImageView
    private lateinit var similarityTextView: TextView

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
            // Нет проверок качества - сразу отправляем во ViewModel
            viewModel.processCapturedImage(bitmap, this)
        } ?: Toast.makeText(this, "Ошибка получения фото", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_control)

        imagePreview = findViewById(R.id.imagePreview)
        similarityTextView = findViewById(R.id.similarityTextView)
        val btnCaptureFace = findViewById<MaterialButton>(R.id.btnCaptureFace)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        btnCaptureFace.setOnClickListener {
            cameraLauncher.launch(null)
        }

        btnBack.setOnClickListener {
            finish()
        }

        // Подписываемся на LiveData из ViewModel
        viewModel.faceBitmap.observe(this) { detectedFace ->
            detectedFace?.let { imagePreview.setImageBitmap(it) }
        }

        viewModel.verificationResult.observe(this) { result ->
            result.onSuccess { resultText ->
                similarityTextView.text = resultText.toString()
            }.onFailure { error ->
                Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
