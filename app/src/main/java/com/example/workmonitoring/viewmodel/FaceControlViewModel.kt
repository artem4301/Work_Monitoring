package com.example.workmonitoring.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.face.FaceNetModel
import com.example.workmonitoring.utils.FaceDetectionHelper
import com.example.workmonitoring.utils.MathUtils
import com.google.firebase.auth.FirebaseAuth

class FaceControlViewModel(
    private val faceNetModel: FaceNetModel,
    private val firebaseRepository: FirebaseRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _verificationResult = MutableLiveData<Result<Double>>()
    val verificationResult: LiveData<Result<Double>> = _verificationResult

    private val _faceBitmap = MutableLiveData<Bitmap?>()
    val faceBitmap: LiveData<Bitmap?> = _faceBitmap

    fun processCapturedImage(context: Context, bitmap: Bitmap) {
        FaceDetectionHelper.detectFace(context, bitmap) { croppedFace ->
            _faceBitmap.postValue(croppedFace)
            if (croppedFace != null) {
                performFaceVerification(croppedFace)
            } else {
                _verificationResult.postValue(Result.failure(Exception("Лицо не распознано!")))
            }
        }
    }

    private fun performFaceVerification(faceBitmap: Bitmap) {
        val newEmbedding = faceNetModel.getFaceEmbeddings(faceBitmap)
        if (newEmbedding == null || newEmbedding.isEmpty()) {
            _verificationResult.postValue(Result.failure(Exception("Ошибка генерации эмбеддингов!")))
            return
        }

        val userId = auth.currentUser?.uid
        if (userId == null) {
            _verificationResult.postValue(Result.failure(Exception("Пользователь не авторизован!")))
            return
        }

        firebaseRepository.getUserEmbeddings(userId, { embeddings ->
            var bestSimilarity = 0.0
            val newEmbeddingDoubles = newEmbedding.map { it.toDouble() }

            embeddings.forEach { storedEmbedding ->
                val storedEmbeddingDoubles = storedEmbedding.map { it.toDouble() }
                val distance = MathUtils.calculateCosineDistance(storedEmbeddingDoubles, newEmbeddingDoubles)
                val similarity = ((1 - distance).coerceIn(0.0, 1.0)) * 100
                bestSimilarity = maxOf(bestSimilarity, similarity)
            }

            _verificationResult.postValue(Result.success(bestSimilarity))
        }, { error ->
            _verificationResult.postValue(Result.failure(Exception(error)))
        })
    }
}
