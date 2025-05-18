package com.example.workmonitoring.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.abs

object FaceDetectionHelper {
    private const val TAG = "FaceDetectionHelper"
    private const val MIN_FACE_SIZE = 80 // уменьшаем минимальный размер лица
    private const val MAX_FACE_ANGLE = 25f // увеличиваем допустимый угол поворота
    private const val CACHE_SIZE = 10 // размер кэша для результатов детекции

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST) // меняем на быстрый режим
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f) // устанавливаем минимальный размер лица относительно размера изображения
            .build()
        FaceDetection.getClient(options)
    }

    private val cache = object : LruCache<String, Bitmap>(CACHE_SIZE) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return 1
        }
    }

    /**
     * Detect face in image using coroutines
     */
    suspend fun detectFace(context: Context, bitmap: Bitmap): Bitmap? {
        return withContext(Dispatchers.Default) {
            try {
                // Check cache first
                val cacheKey = bitmap.hashCode().toString()
                cache.get(cacheKey)?.let { cachedResult ->
                    Log.d(TAG, "Using cached result for face detection")
                    return@withContext cachedResult
                }

                val result = processFaceDetection(context, bitmap)
                if (result != null) {
                    cache.put(cacheKey, result)
                }
                result
            } catch (e: Exception) {
                Log.e(TAG, "Неожиданная ошибка при обработке изображения", e)
                showToast(context, "Произошла неожиданная ошибка")
                null
            }
        }
    }

    private suspend fun processFaceDetection(context: Context, bitmap: Bitmap): Bitmap? {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        
        return suspendCancellableCoroutine { continuation ->
            detector.process(inputImage)
                .addOnSuccessListener { faces ->
                    Log.d(TAG, "Обнаружено лиц: ${faces.size}")
                    
                    when {
                        faces.isEmpty() -> {
                            Log.w(TAG, "Лицо не обнаружено")
                            showToast(context, "Лицо не обнаружено на фото")
                            continuation.resume(null)
                            return@addOnSuccessListener
                        }
                        faces.size > 1 -> {
                            Log.w(TAG, "Обнаружено несколько лиц")
                            showToast(context, "Пожалуйста, убедитесь что на фото только одно лицо")
                            continuation.resume(null)
                            return@addOnSuccessListener
                        }
                    }

                    val face = faces[0]
                    Log.d(TAG, "Размер лица: ${face.boundingBox.width()}x${face.boundingBox.height()}")
                    Log.d(TAG, "Угол поворота: ${face.headEulerAngleY}")
                    
                    // Проверка размера лица
                    if (face.boundingBox.width() < MIN_FACE_SIZE || face.boundingBox.height() < MIN_FACE_SIZE) {
                        Log.w(TAG, "Лицо слишком маленькое: ${face.boundingBox.width()}x${face.boundingBox.height()}")
                        showToast(context, "Пожалуйста, приблизьтесь к камере")
                        continuation.resume(null)
                        return@addOnSuccessListener
                    }

                    // Проверка угла поворота лица
                    face.headEulerAngleY?.let { angleY ->
                        if (abs(angleY) > MAX_FACE_ANGLE) {
                            Log.w(TAG, "Лицо повернуто слишком сильно: $angleY градусов")
                            showToast(context, "Пожалуйста, смотрите прямо в камеру")
                            continuation.resume(null)
                            return@addOnSuccessListener
                        }
                    }

                    // Проверка качества освещения
                    val leftEyeOpen = face.leftEyeOpenProbability?.let { it > 0.3f } ?: true // уменьшаем порог
                    val rightEyeOpen = face.rightEyeOpenProbability?.let { it > 0.3f } ?: true // уменьшаем порог
                    if (!leftEyeOpen || !rightEyeOpen) {
                        Log.w(TAG, "Глаза закрыты: левый=${face.leftEyeOpenProbability}, правый=${face.rightEyeOpenProbability}")
                        showToast(context, "Пожалуйста, откройте глаза")
                        continuation.resume(null)
                        return@addOnSuccessListener
                    }

                    try {
                        // Добавляем отступ вокруг лица для лучшего распознавания
                        val padding = (face.boundingBox.width() * 0.2).toInt()
                        val left = (face.boundingBox.left - padding).coerceAtLeast(0)
                        val top = (face.boundingBox.top - padding).coerceAtLeast(0)
                        val width = (face.boundingBox.width() + 2 * padding)
                            .coerceAtMost(bitmap.width - left)
                        val height = (face.boundingBox.height() + 2 * padding)
                            .coerceAtMost(bitmap.height - top)

                        val croppedFace = Bitmap.createBitmap(
                            bitmap,
                            left,
                            top,
                            width,
                            height
                        )
                        continuation.resume(croppedFace)
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка при обрезке изображения", e)
                        showToast(context, "Ошибка при обработке изображения")
                        continuation.resume(null)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Ошибка при распознавании лица", e)
                    showToast(context, "Ошибка распознавания лица: ${e.localizedMessage}")
                    continuation.resume(null)
                }

            continuation.invokeOnCancellation {
                // Cleanup if needed
            }
        }
    }

    private fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
