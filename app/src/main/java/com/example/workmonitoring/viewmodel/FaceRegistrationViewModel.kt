package com.example.workmonitoring.viewmodel

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.face.FaceNetModel
import com.example.workmonitoring.utils.ImageQualityChecker
import com.example.workmonitoring.utils.SecurityUtils
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

class FaceRegistrationViewModel(
    private val assetManager: AssetManager,
    private val firebaseRepository: FirebaseRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val faceNetModel = FaceNetModel(assetManager)
    private val capturedEmbeddings = mutableListOf<Pair<List<Float>, List<Float>>>()
    private val requiredEmbeddings = 10

    private val _registrationProgress = MutableLiveData<Int>()
    val registrationProgress: LiveData<Int> = _registrationProgress

    private val _processingState = MutableLiveData<ProcessingState>()
    val processingState: LiveData<ProcessingState> = _processingState

    private val _lastFailedIndex = MutableLiveData<Int?>()
    val lastFailedIndex: LiveData<Int?> = _lastFailedIndex

    init {
        _registrationProgress.value = 0
        _processingState.value = ProcessingState.IDLE
        _lastFailedIndex.value = null
    }

    /**
     * Process a captured image for face registration
     * Performs quality checks and generates face embeddings on a background thread
     */
    fun processCapturedImage(bitmap: Bitmap, index: Int, context: Context, onFail: (String) -> Unit) {
        viewModelScope.launch {
            try {
                _processingState.value = ProcessingState.PROCESSING
                _lastFailedIndex.value = null
                
                val result = withContext(Dispatchers.Default) {
                    processImageInBackground(bitmap, onFail)
                }

                if (result != null) {
                    val (rawEmbedding, normEmbedding) = result
                    
                    // Encrypt embeddings before storing
                    val encryptedRaw = SecurityUtils.encryptData(floatListToByteArray(rawEmbedding))
                    val encryptedNorm = SecurityUtils.encryptData(floatListToByteArray(normEmbedding))
                    
                    // Если это повторная попытка для того же индекса, заменяем старое фото
                    if (index < capturedEmbeddings.size) {
                        capturedEmbeddings[index] = Pair(rawEmbedding, normEmbedding)
                    } else {
                        capturedEmbeddings.add(Pair(rawEmbedding, normEmbedding))
                    }
                    
                    _registrationProgress.value = capturedEmbeddings.size
                    Log.d("FaceRegistration", "Эмбеддинг №$index добавлен, всего: ${capturedEmbeddings.size}")
                } else {
                    _lastFailedIndex.value = index
                }
                
                _processingState.value = ProcessingState.IDLE
            } catch (e: Exception) {
                Log.e("FaceRegistration", "Ошибка при обработке изображения", e)
                onFail("Произошла ошибка при обработке изображения: ${e.localizedMessage}")
                _processingState.value = ProcessingState.ERROR
                _lastFailedIndex.value = index
            }
        }
    }

    private suspend fun processImageInBackground(bitmap: Bitmap, onFail: (String) -> Unit): Pair<List<Float>, List<Float>>? {
        return withContext(Dispatchers.Default) {
            // Image quality validation
            when {
                bitmap.width < 120 || bitmap.height < 120 -> {
                    onFail("Изображение слишком маленькое. Минимальный размер 120x120 пикселей")
                    return@withContext null
                }
                ImageQualityChecker.isImageBlurred(bitmap) -> {
                    onFail("Фото размытое, пожалуйста сделайте фото заново")
                    return@withContext null
                }
                ImageQualityChecker.isImageTooDark(bitmap) -> {
                    onFail("Плохое освещение, пожалуйста сделайте фото при лучшем свете")
                    return@withContext null
                }
            }

            // Generate face embeddings
            val embeddings = faceNetModel.getFaceEmbeddings(bitmap)
            if (embeddings == null) {
                onFail("Не удалось обнаружить лицо на фото")
                return@withContext null
            }

            val (rawEmbedding, normEmbedding) = embeddings
            
            // Validate embedding quality
            if (rawEmbedding.any { it.isNaN() } || normEmbedding.any { it.isNaN() }) {
                onFail("Ошибка в генерации эмбеддинга: некорректные значения")
                return@withContext null
            }

            return@withContext Pair(rawEmbedding.toList(), normEmbedding.toList())
        }
    }

    /**
     * Save the collected face embeddings to Firebase
     */
    fun saveEmbeddings(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            try {
                _processingState.value = ProcessingState.SAVING
                
                val userId = auth.currentUser?.uid
                if (userId == null) {
                    onFailure("Пользователь не авторизован")
                    return@launch
                }

                if (capturedEmbeddings.size < requiredEmbeddings) {
                    onFailure("Необходимо сделать как минимум $requiredEmbeddings фотографий")
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    // Convert embeddings to the format expected by Firebase
                    val embeddingsMap = capturedEmbeddings.mapIndexed { index, (raw, norm) ->
                        mapOf(
                            "embedding_raw_$index" to raw,
                            "embedding_norm_$index" to norm
                        )
                    }.flatMap { it.entries }.associate { it.key to it.value }

                    firebaseRepository.saveEmbeddings(
                        userId,
                        embeddingsMap,
                        onSuccess = {
                            viewModelScope.launch {
                                capturedEmbeddings.clear()
                                _registrationProgress.value = 0
                                _processingState.value = ProcessingState.IDLE
                                onSuccess()
                            }
                        },
                        onFailure = { error ->
                            _processingState.value = ProcessingState.ERROR
                            onFailure("Ошибка сохранения данных: $error")
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("FaceRegistration", "Ошибка при сохранении эмбеддингов", e)
                _processingState.value = ProcessingState.ERROR
                onFailure("Произошла ошибка при сохранении данных: ${e.localizedMessage}")
            }
        }
    }

    private fun floatListToByteArray(floatList: List<Float>): ByteArray {
        val buffer = ByteBuffer.allocate(4 * floatList.size)
        floatList.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    /**
     * Get the current progress of face registration
     */
    fun getRegistrationProgress(): Int {
        return capturedEmbeddings.size
    }

    /**
     * Check if enough face samples have been collected
     */
    fun isRegistrationComplete(): Boolean {
        return capturedEmbeddings.size >= requiredEmbeddings
    }

    /**
     * Remove the last captured embedding
     */
    fun removeLastEmbedding() {
        if (capturedEmbeddings.isNotEmpty()) {
            capturedEmbeddings.removeAt(capturedEmbeddings.size - 1)
            _registrationProgress.value = capturedEmbeddings.size
        }
    }

    /**
     * Remove embedding at specific index
     */
    fun removeEmbeddingAt(index: Int) {
        if (index in capturedEmbeddings.indices) {
            capturedEmbeddings.removeAt(index)
            _registrationProgress.value = capturedEmbeddings.size
        }
    }

    /**
     * Clear all captured embeddings and start over
     */
    fun clearAllEmbeddings() {
        capturedEmbeddings.clear()
        _registrationProgress.value = 0
        _lastFailedIndex.value = null
    }

    override fun onCleared() {
        super.onCleared()
        faceNetModel.close()
    }

    enum class ProcessingState {
        IDLE,
        PROCESSING,
        SAVING,
        ERROR
    }
}


