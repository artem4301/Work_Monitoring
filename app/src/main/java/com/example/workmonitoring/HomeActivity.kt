package com.example.workmonitoring

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.facedetection.FaceNetModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.*

class HomeActivity : AppCompatActivity() {
    private lateinit var faceNetModel: FaceNetModel
    private lateinit var auth: FirebaseAuth
    private lateinit var imageView: ImageView

    private var croppedFaceBitmap: Bitmap? = null // Храним обрезанное изображение лица

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        faceNetModel = FaceNetModel(assets)
        auth = FirebaseAuth.getInstance()

        val btnSelectImage = findViewById<Button>(R.id.btnSelectImage)
        val btnSaveEmbeddings = findViewById<Button>(R.id.btnSaveEmbeddings)
        val logoutButton = findViewById<Button>(R.id.logoutButton)
        imageView = findViewById(R.id.imageView)
        val btnOnWork = findViewById<Button>(R.id.btnOnWork)

        btnOnWork.setOnClickListener {
            val db = FirebaseFirestore.getInstance()
            val userId = auth.currentUser?.uid ?: return@setOnClickListener

            db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // Учитываем только ключи, начинающиеся с "embedding_" и не заканчивающиеся на "_timestamp"
                        val embeddingCount = document.data?.keys
                            ?.filter { it.startsWith("embedding_") && !it.endsWith("_timestamp") }
                            ?.size ?: 0

                        if (embeddingCount >= 3) {
                            startActivity(Intent(this, FaceControlActivity::class.java))
                        } else {
                            Toast.makeText(
                                this,
                                "Для фотоконтроля нужно минимум 3 фотографии!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this,
                            "Пользователь не найден в базе данных!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Ошибка при проверке данных!", Toast.LENGTH_SHORT).show()
                }
        }

        // Настройка кнопки выхода
        logoutButton.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // Кнопка выбора изображения
        btnSelectImage.setOnClickListener {
            pickImageFromGallery()
        }

        // Кнопка сохранения эмбеддингов
        btnSaveEmbeddings.setOnClickListener {
            if (croppedFaceBitmap != null) {
                saveEmbeddingsToFirebase(croppedFaceBitmap!!)
            } else {
                Toast.makeText(this, "Сначала выберите изображение с лицом!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            val imageUri = data?.data ?: return
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)

            processImage(bitmap)
        }
    }

    private fun processImage(bitmap: Bitmap) {
        detectFaceWithMLKit(bitmap) { croppedFace ->
            if (croppedFace != null) {
                croppedFaceBitmap = croppedFace
                imageView.setImageBitmap(croppedFace)
                Toast.makeText(this, "Лицо успешно распознано!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Лицо не распознано!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun detectFaceWithMLKit(bitmap: Bitmap, callback: (Bitmap?) -> Unit) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build()
        )

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces[0]
                    val boundingBox = face.boundingBox

                    // Обрезаем лицо
                    val croppedFace = Bitmap.createBitmap(
                        bitmap,
                        boundingBox.left.coerceAtLeast(0),
                        boundingBox.top.coerceAtLeast(0),
                        boundingBox.width().coerceAtMost(bitmap.width - boundingBox.left),
                        boundingBox.height().coerceAtMost(bitmap.height - boundingBox.top)
                    )
                    callback(croppedFace)
                } else {
                    callback(null)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Ошибка распознавания лица: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                callback(null)
            }
    }

    private fun saveEmbeddingsToFirebase(faceBitmap: Bitmap) {
        val mutableBitmap = faceBitmap.copy(Bitmap.Config.ARGB_8888, true)

        val embeddings = faceNetModel.getFaceEmbeddings(mutableBitmap)
        if (embeddings == null || embeddings.isEmpty()) {
            Toast.makeText(this, "Ошибка при генерации эмбеддингов!", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseFirestore.getInstance()
        val userId = auth.currentUser?.uid ?: return Toast.makeText(this, "Пользователь не авторизован!", Toast.LENGTH_SHORT).show()

        // Генерация уникального ключа для нового поля
        val uniqueFieldName = "embedding_${System.currentTimeMillis()}"

        val data = mapOf(
            uniqueFieldName to embeddings.toList(),
            "${uniqueFieldName}_timestamp" to Date()
        )

        db.collection("users")
            .document(userId)
            .update(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Эмбеддинги успешно сохранены!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                // Если документа не существует, создаем его и добавляем данные
                db.collection("users")
                    .document(userId)
                    .set(data)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Эмбеддинги успешно сохранены!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { error ->
                        Toast.makeText(this, "Ошибка сохранения: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    companion object {
        private const val REQUEST_CODE_PICK_IMAGE = 101
    }
}
