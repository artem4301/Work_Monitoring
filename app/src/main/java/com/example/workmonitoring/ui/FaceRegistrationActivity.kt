package com.example.workmonitoring.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.workmonitoring.R
import com.example.workmonitoring.viewmodel.FaceRegistrationViewModel
import com.example.workmonitoring.viewmodel.FaceRegistrationViewModelFactory
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.utils.FaceDetectionHelper
import com.google.firebase.auth.FirebaseAuth

class FaceRegistrationActivity : AppCompatActivity() {
    private lateinit var instructionTextView: TextView
    private lateinit var imagePreview: ImageView
    private lateinit var btnStartRegistration: Button

    private val viewModel: FaceRegistrationViewModel by viewModels {
        FaceRegistrationViewModelFactory(assets, FirebaseRepository(), FirebaseAuth.getInstance())
    }

    private var photoIndex = 0
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
            btnStartRegistration.isEnabled = true
            return@registerForActivityResult
        }

        FaceDetectionHelper.detectFace(this, capturedBitmap) { croppedFace ->
            if (croppedFace == null) {
                Toast.makeText(this, "Лицо не найдено! Попробуйте снова.", Toast.LENGTH_SHORT).show()
                Log.e("FaceRegistration", "Лицо не обнаружено")
                btnStartRegistration.isEnabled = true
            } else {
                imagePreview.setImageBitmap(croppedFace)

                viewModel.processCapturedImage(croppedFace) { success ->
                    if (success) {
                        photoIndex++
                        if (photoIndex < instructions.size) {
                            instructionTextView.text = instructions[photoIndex]
                            btnStartRegistration.isEnabled = true
                        } else {
                            Log.d("FaceRegistration", "Сохраняем эмбеддинги...")
                            viewModel.saveEmbeddings(
                                onSuccess = {
                                    Toast.makeText(this, "Регистрация завершена!", Toast.LENGTH_SHORT).show()
                                    finish()
                                },
                                onFailure = { error ->
                                    Toast.makeText(this, "Ошибка сохранения: $error", Toast.LENGTH_SHORT).show()
                                }
                            )

                        }
                    } else {
                        Toast.makeText(this, "Ошибка обработки лица, попробуйте снова!", Toast.LENGTH_SHORT).show()
                        btnStartRegistration.isEnabled = true
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_registration)

        instructionTextView = findViewById(R.id.instructionText)
        imagePreview = findViewById(R.id.imagePreview)
        btnStartRegistration = findViewById(R.id.btnStartRegistration)

        btnStartRegistration.setOnClickListener {
            btnStartRegistration.isEnabled = false
            cameraLauncher.launch(null)
        }

        instructionTextView.text = instructions[photoIndex]
    }
}
