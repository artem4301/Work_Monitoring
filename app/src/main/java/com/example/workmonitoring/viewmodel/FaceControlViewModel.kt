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
import com.example.workmonitoring.utils.FaceSimilarityAnalyzer
import com.example.workmonitoring.utils.ImageQualityChecker
import com.example.workmonitoring.utils.SimilarityMetrics
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class FaceControlViewModel(
    private val faceNetModel: FaceNetModel,
    private val firebaseRepository: FirebaseRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val similarityAnalyzer = FaceSimilarityAnalyzer()
    private val _verificationResult = MutableLiveData<Result<String>>()
    val verificationResult: LiveData<Result<String>> = _verificationResult

    private val _faceBitmap = MutableLiveData<Bitmap?>()
    val faceBitmap: LiveData<Bitmap?> = _faceBitmap

    fun processCapturedImage(bitmap: Bitmap, context: Context) {
        if (ImageQualityChecker.isImageBlurred(bitmap)) {
            _verificationResult.postValue(Result.failure(Exception("Фото размытое. Попробуйте снова.")))
            return
        }

        if (ImageQualityChecker.isImageTooDark(bitmap)) {
            _verificationResult.postValue(Result.failure(Exception("Слишком тёмное фото. Включите свет или подойдите ближе к свету.")))
            return
        }

        viewModelScope.launch {
            val croppedFace = FaceDetectionHelper.detectFace(context, bitmap)
            _faceBitmap.postValue(croppedFace)
            if (croppedFace != null) {
                performFaceVerification(croppedFace)
            } else {
                _verificationResult.postValue(Result.failure(Exception("Лицо не распознано!")))
            }
        }
    }

    private fun performFaceVerification(faceBitmap: Bitmap) {
        val embeddings = faceNetModel.getFaceEmbeddings(faceBitmap) ?: run {
            _verificationResult.postValue(Result.failure(Exception("Ошибка генерации эмбеддингов!")))
            return
        }

        val userId = auth.currentUser?.uid ?: run {
            _verificationResult.postValue(Result.failure(Exception("Пользователь не авторизован!")))
            return
        }

        firebaseRepository.getUserEmbeddings(userId, { storedEmbeddings ->
            if (storedEmbeddings.isEmpty()) {
                _verificationResult.postValue(Result.failure(Exception("Нет сохраненных данных для сравнения!")))
                return@getUserEmbeddings
            }

            var bestMatch: FaceSimilarityAnalyzer.SimilarityResult? = null
            var bestMatchIndex = -1

            // Сравниваем с каждым сохраненным эмбеддингом
            storedEmbeddings.forEachIndexed { index, storedEmbedding ->
                val result = similarityAnalyzer.analyzeSimilarity(
                    embedding1 = embeddings.first.toList(),
                    embedding2 = storedEmbedding,
                    threshold = 75f
                )

                if (bestMatch == null || result.cosine > (bestMatch?.cosine ?: 0f)) {
                    bestMatch = result
                    bestMatchIndex = index
                }
            }

            bestMatch?.let { match ->
                val detailedResult = buildString {
                    appendLine("Результаты сравнения (эмбеддинг #${bestMatchIndex + 1}):")
                    appendLine("Косинусное сходство: ${String.format("%.2f", match.cosine)}% (порог: 75%)")
                    appendLine("Евклидово сходство: ${String.format("%.2f", match.euclidean)}%")
                    appendLine("\nВерификация: ${if (match.isMatch) "УСПЕШНА" else "НЕУДАЧНА"}")
                }
                _verificationResult.postValue(Result.success(detailedResult))
            } ?: run {
                _verificationResult.postValue(Result.failure(Exception("Не удалось найти совпадение!")))
            }
        }, { error ->
            _verificationResult.postValue(Result.failure(Exception(error)))
        })
    }
}
