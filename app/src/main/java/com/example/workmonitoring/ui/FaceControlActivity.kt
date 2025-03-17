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
    private lateinit var imageView: ImageView
    private lateinit var tvResult: TextView
    private val viewModel: FaceControlViewModel by viewModels {
        FaceControlViewModelFactory(FaceNetModel(assets), FirebaseRepository(), FirebaseAuth.getInstance())
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        bitmap?.let { viewModel.processCapturedImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_control)

        imageView = findViewById(R.id.imageView)
        tvResult = findViewById(R.id.tvResult)
        val btnStartCamera = findViewById<Button>(R.id.btnStartCamera)

        btnStartCamera.setOnClickListener { takePictureLauncher.launch(null) }

        viewModel.faceBitmap.observe(this) { bitmap ->
            bitmap?.let { imageView.setImageBitmap(it) }
        }

        viewModel.verificationResult.observe(this) { result ->
            result.onSuccess { similarity ->
                tvResult.text = "Схожесть: ${"%.2f".format(similarity)}%"
                if (similarity >= 70) {
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
