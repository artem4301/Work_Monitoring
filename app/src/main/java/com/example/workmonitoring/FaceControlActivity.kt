package com.example.workmonitoring

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.facedetection.FaceNetModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.sqrt

class FaceControlActivity : AppCompatActivity() {
    private lateinit var faceNetModel: FaceNetModel
    private lateinit var auth: FirebaseAuth
    private lateinit var imageView: ImageView
    private lateinit var tvResult: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_control)

        faceNetModel = FaceNetModel(assets)
        auth = FirebaseAuth.getInstance()

        val btnStartCamera = findViewById<Button>(R.id.btnStartCamera)
        imageView = findViewById(R.id.imageView)
        tvResult = findViewById(R.id.tvResult)

        btnStartCamera.setOnClickListener {
            openCamera()
        }
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, REQUEST_CODE_CAMERA)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_CAMERA && resultCode == Activity.RESULT_OK) {
            val photo = data?.extras?.get("data") as Bitmap
            imageView.setImageBitmap(photo)

            detectFaceWithMLKit(photo) { croppedFace ->
                if (croppedFace != null) {
                    performFaceVerification(croppedFace)
                } else {
                    Toast.makeText(this, "Лицо не распознано!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun detectFaceWithMLKit(bitmap: Bitmap, callback: (Bitmap?) -> Unit) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build()
        )

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces[0]
                    val boundingBox = face.boundingBox

                    val croppedFace = Bitmap.createBitmap(
                        bitmap,
                        boundingBox.left.coerceAtLeast(0),
                        boundingBox.top.coerceAtLeast(0),
                        boundingBox.width().coerceAtMost(bitmap.width - boundingBox.left),
                        boundingBox.height().coerceAtMost(bitmap.height - boundingBox.top)
                    )

                    imageView.setImageBitmap(croppedFace)

                    callback(croppedFace)
                } else {
                    callback(null)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Ошибка распознавания лица!", Toast.LENGTH_SHORT).show()
                callback(null)
            }
    }

    private fun performFaceVerification(faceBitmap: Bitmap) {
        val mutableBitmap = faceBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val newEmbedding = faceNetModel.getFaceEmbeddings(mutableBitmap)

        if (newEmbedding == null || newEmbedding.isEmpty()) {
            Toast.makeText(this, "Ошибка генерации эмбеддингов!", Toast.LENGTH_SHORT).show()
            return
        }

        val db = FirebaseFirestore.getInstance()
        val userId = auth.currentUser?.uid ?: return Toast.makeText(this, "Пользователь не авторизован!", Toast.LENGTH_SHORT).show()

        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val embeddings = document.data?.filterKeys { it.startsWith("embedding_") && !it.endsWith("_timestamp") }
                        ?.mapNotNull { it.value as? List<Float> } ?: emptyList()

                    var bestSimilarity = 0.0
                    val match = embeddings.any { storedEmbedding ->

                        val embeddingList = newEmbedding.map { it.toDouble() }
                        val distance = calculateCosineDistance(storedEmbedding.map { it.toDouble() }, embeddingList)
                        val similarity = (1 - distance).coerceIn(0.0, 1.0) * 100
                        bestSimilarity = maxOf(bestSimilarity, similarity)
                        distance < THRESHOLD
                    }

                    tvResult.text = "Схожесть: ${"%.2f".format(bestSimilarity)}%"
                    if (match) {
                        Toast.makeText(this, "Фотоконтроль пройден!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Фотоконтроль не пройден!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Эмбеддинги не найдены в базе данных!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Ошибка при загрузке данных из базы!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun calculateCosineDistance(embedding1: List<Double>, embedding2: List<Double>): Double {
        if (embedding1.size != embedding2.size) {
            return Double.MAX_VALUE
        }

        val dotProduct = embedding1.zip(embedding2).sumOf { (a, b) -> a * b }

        val norm1 = sqrt(embedding1.sumOf { it * it })
        val norm2 = sqrt(embedding2.sumOf { it * it })

        val cosineSimilarity = dotProduct / (norm1 * norm2)

        return 1.0 - cosineSimilarity
    }


    companion object {
        private const val REQUEST_CODE_CAMERA = 102
        private const val THRESHOLD = 0.7
    }
}
