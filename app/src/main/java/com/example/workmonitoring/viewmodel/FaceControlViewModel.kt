package com.example.workmonitoring.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.face.FaceNetModel
import com.example.workmonitoring.utils.FaceDetectionHelper
import com.example.workmonitoring.utils.ImageQualityChecker
import com.example.workmonitoring.utils.SimilarityMetrics
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class FaceControlViewModel(
    private val faceNetModel: FaceNetModel,
    private val firebaseRepository: FirebaseRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _verificationResult = MutableLiveData<Result<Map<String, Any>>>()
    val verificationResult: LiveData<Result<Map<String, Any>>> = _verificationResult

    private val _faceBitmap = MutableLiveData<Bitmap?>()
    val faceBitmap: LiveData<Bitmap?> = _faceBitmap

    private val _qualityResult = MutableLiveData<ImageQualityChecker.QualityResult>()
    val qualityResult: LiveData<ImageQualityChecker.QualityResult> = _qualityResult

    fun processCapturedImage(bitmap: Bitmap, context: Context) {
        viewModelScope.launch {
            try {
                // 1. Проверка качества изображения
                val qualityResult = ImageQualityChecker.checkImageQuality(bitmap)
                _qualityResult.postValue(qualityResult)
                
                if (!qualityResult.isGoodQuality) {
                    _verificationResult.postValue(
                        Result.failure(Exception("Качество изображения недостаточное: ${qualityResult.issues.joinToString(", ")}"))
                    )
                    return@launch
                }

                // 2. Детекция лица
                val croppedFace = FaceDetectionHelper.detectFace(context, bitmap)
                _faceBitmap.postValue(croppedFace)
                
                if (croppedFace != null) {
                    performFaceVerification(croppedFace)
                } else {
                    _verificationResult.postValue(Result.failure(Exception("Лицо не обнаружено")))
                }
            } catch (e: Exception) {
                _verificationResult.postValue(Result.failure(e))
            }
        }
    }

    private fun performFaceVerification(faceBitmap: Bitmap) {
        val embeddings = faceNetModel.getFaceEmbeddings(faceBitmap) ?: run {
            _verificationResult.postValue(Result.failure(Exception("Ошибка генерации эмбеддингов")))
            return
        }

        val userId = auth.currentUser?.uid ?: run {
            _verificationResult.postValue(Result.failure(Exception("Пользователь не авторизован")))
            return
        }

        firebaseRepository.getUserEmbeddings(userId, { storedEmbeddings ->
            if (storedEmbeddings.isEmpty()) {
                _verificationResult.postValue(Result.failure(Exception("Нет сохраненных данных для сравнения")))
                return@getUserEmbeddings
            }

            // Используем сырые эмбеддинги для сравнения
            val currentEmbedding = embeddings.first.toList()
            val results = mutableMapOf<String, Float>()
            
            // Сравниваем с каждым сохраненным эмбеддингом используя все метрики
            storedEmbeddings.forEach { stored ->
                val cosine = SimilarityMetrics.calculateSimilarity(currentEmbedding, stored, SimilarityMetrics.SimilarityMetric.COSINE)
                val euclidean = SimilarityMetrics.calculateSimilarity(currentEmbedding, stored, SimilarityMetrics.SimilarityMetric.EUCLIDEAN)
                val manhattan = SimilarityMetrics.calculateSimilarity(currentEmbedding, stored, SimilarityMetrics.SimilarityMetric.MANHATTAN)
                val pearson = SimilarityMetrics.calculateSimilarity(currentEmbedding, stored, SimilarityMetrics.SimilarityMetric.PEARSON)
                val jaccard = SimilarityMetrics.calculateSimilarity(currentEmbedding, stored, SimilarityMetrics.SimilarityMetric.JACCARD)
                
                results["Косинусная"] = maxOf(results["Косинусная"] ?: 0f, cosine)
                results["Евклидова"] = maxOf(results["Евклидова"] ?: 0f, euclidean)
                results["Манхэттенская"] = maxOf(results["Манхэттенская"] ?: 0f, manhattan)
                results["Пирсона"] = maxOf(results["Пирсона"] ?: 0f, pearson)
                results["Жаккара"] = maxOf(results["Жаккара"] ?: 0f, jaccard)
            }
            
            // Верификация проходит только по косинусной метрике
            val cosineScore = results["Косинусная"] ?: 0f
            val isVerified = cosineScore >= 75f
            
            val resultData = mapOf(
                "metrics" to results,
                "isVerified" to isVerified,
                "bestMetric" to "Косинусная",
                "bestScore" to cosineScore
            )
            
            _verificationResult.postValue(Result.success(resultData))
            
        }, { error ->
            _verificationResult.postValue(Result.failure(Exception(error)))
        })
    }
}
