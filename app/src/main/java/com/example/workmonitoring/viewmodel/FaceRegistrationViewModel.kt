package com.example.workmonitoring.viewmodel

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.face.FaceNetModel
import com.google.firebase.auth.FirebaseAuth

class FaceRegistrationViewModel(
    private val assetManager: AssetManager,
    private val firebaseRepository: FirebaseRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val faceNetModel = FaceNetModel(assetManager)
    private val capturedEmbeddings = mutableListOf<List<Float>>()

    fun processCapturedImage(bitmap: Bitmap, onProcessed: (Boolean) -> Unit) {
        val embeddings = faceNetModel.getFaceEmbeddings(bitmap)

        if (embeddings != null && embeddings.isNotEmpty()) {
            capturedEmbeddings.add(embeddings.toList())
            Log.d("FaceRegistration", "Эмбеддинг добавлен, всего: ${capturedEmbeddings.size}")
            onProcessed(true)
        } else {
            Log.e("FaceRegistration", "Ошибка генерации эмбеддинга")
            onProcessed(false)
        }
    }

    fun saveEmbeddings(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val userId = auth.currentUser?.uid ?: return

        if (capturedEmbeddings.size < 10) {
            onFailure("Недостаточно эмбеддингов, нужно 10!")
            return
        }

        val data = mutableMapOf<String, Any>()
        capturedEmbeddings.forEachIndexed { index, embedding ->
            data["embedding_$index"] = embedding
        }

        // Сохраняем все эмбеддинги одним запросом (batch update)
        firebaseRepository.saveEmbeddings(userId, data,
            onSuccess = {
                onSuccess()
            },
            onFailure = { error ->
                onFailure(error)
            }
        )
    }
}


