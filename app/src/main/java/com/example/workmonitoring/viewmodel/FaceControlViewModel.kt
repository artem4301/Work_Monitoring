package com.example.workmonitoring.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.face.FaceNetModel
import com.example.workmonitoring.utils.FaceDetectionHelper
import com.example.workmonitoring.utils.ImageQualityChecker
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.sqrt

class FaceControlViewModel(
    private val faceNetModel: FaceNetModel,
    private val firebaseRepository: FirebaseRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _verificationResult = MutableLiveData<Result<String>>()
    val verificationResult: LiveData<Result<String>> = _verificationResult

    private val _faceBitmap = MutableLiveData<Bitmap?>()
    val faceBitmap: LiveData<Bitmap?> = _faceBitmap

    fun processCapturedImage(bitmap: Bitmap, context: Context) {
        // 1. Сначала проверяем качество снимка
        if (ImageQualityChecker.isImageBlurred(bitmap)) {
            _verificationResult.postValue(
                Result.failure(Exception("Фото размытое. Попробуйте снова."))
            )
            return
        }

        if (ImageQualityChecker.isImageTooDark(bitmap)) {
            _verificationResult.postValue(
                Result.failure(Exception("Слишком тёмное фото. Включите свет или подойдите ближе к свету."))
            )
            return
        }

        // 2. Если качество OK - переходим к поиску лица
        FaceDetectionHelper.detectFace(context, bitmap) { croppedFace ->
            _faceBitmap.postValue(croppedFace)
            if (croppedFace != null) {
                performFaceVerification(croppedFace)
            } else {
                _verificationResult.postValue(
                    Result.failure(Exception("Лицо не распознано!"))
                )
            }
        }
    }

    private fun performFaceVerification(faceBitmap: Bitmap) {
        val newEmbedding = faceNetModel.getFaceEmbeddings(faceBitmap)
        if (newEmbedding == null || newEmbedding.isEmpty()) {
            _verificationResult.postValue(
                Result.failure(Exception("Ошибка генерации эмбеддингов!"))
            )
            return
        }

        val userId = auth.currentUser?.uid
        if (userId == null) {
            _verificationResult.postValue(
                Result.failure(Exception("Пользователь не авторизован!"))
            )
            return
        }

        firebaseRepository.getUserEmbeddings(userId, { embeddings ->
            val newDoubles = newEmbedding.map { it.toDouble() }

            var bestCosine = 0.0
            var bestEuclidean = 0.0
            var bestManhattan = 0.0

            // Максимумы для L2-нормированных векторов
            val maxL2Dist  = 2.0
            val maxManDist = 2.0 * sqrt(128.0) // ~22.627

            embeddings.forEachIndexed { index, storedEmbedding ->
                val storedDoubles = storedEmbedding.map { it.toDouble() }

                // Сырые расстояния
                val cosDist = MathUtils.calculateCosineDistance(storedDoubles, newDoubles)
                val l2Dist  = MathUtils.calculateEuclideanDistance(storedDoubles, newDoubles)
                val manDist = MathUtils.calculateManhattanDistance(storedDoubles, newDoubles)

                // (A) Косинусное сходство = (1 - cosDist)*100, clamp [0..100]
                val cosSim = ((1.0 - cosDist).coerceIn(0.0, 1.0)) * 100.0

                // (B) Евклидовое сходство = ((2 - l2Dist)/2)*100
                val euclSim = ((maxL2Dist - l2Dist) / maxL2Dist).coerceIn(0.0, 1.0) * 100.0

                // (C) Манхэттенское сходство = ((maxManDist - manDist)/maxManDist)*100
                val manSim = ((maxManDist - manDist) / maxManDist).coerceIn(0.0, 1.0) * 100.0

                bestCosine = maxOf(bestCosine, cosSim)
                bestEuclidean = maxOf(bestEuclidean, euclSim)
                bestManhattan = maxOf(bestManhattan, manSim)
            }

            val resultText = """
                Косинусное сходство: ${"%.2f".format(bestCosine)}%
                Евклидово сходство: ${"%.2f".format(bestEuclidean)}%
                Манхэттенское сходство: ${"%.2f".format(bestManhattan)}%
            """.trimIndent()

            _verificationResult.postValue(Result.success(resultText))

        }, { error ->
            _verificationResult.postValue(Result.failure(Exception(error)))
        })
    }
}
