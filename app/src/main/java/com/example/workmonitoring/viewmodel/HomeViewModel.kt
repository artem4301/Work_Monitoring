package com.example.workmonitoring.viewmodel

import android.content.res.AssetManager
import androidx.lifecycle.ViewModel
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.face.FaceNetModel
import com.google.firebase.auth.FirebaseAuth

class HomeViewModel(
    private val assets: AssetManager,
    private val repository: FirebaseRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val faceNetModel = FaceNetModel(assets)

    fun getUserFullName(onResult: (String) -> Unit, onFailure: (String) -> Unit) {
        repository.getCurrentUser { user ->
            if (user != null) {
                onResult("${user.firstName} ${user.lastName}")
            } else {
                onFailure("Не удалось загрузить данные пользователя")
            }
        }
    }

    fun getUserEmail(): String {
        return auth.currentUser?.email ?: "Не указан"
    }

    fun getUserWorkZone(onResult: (String) -> Unit, onFailure: () -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        repository.getUserWorkZone(
            userId = userId,
            onSuccess = onResult,
            onFailure = { onFailure() }
        )
    }

    fun checkUserEmbeddings(
        onRegistered: () -> Unit,
        onNotRegistered: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val userId = auth.currentUser?.uid ?: return
        repository.getUserEmbeddings(
            userId = userId,
            onSuccess = { embeddings ->
                if (embeddings.isNotEmpty()) {
                    onRegistered()
                } else {
                    onNotRegistered()
                }
            },
            onFailure = { error ->
                onFailure(error)
                onNotRegistered() // Показываем кнопку регистрации при ошибке
            }
        )
    }

    fun logout() {
        auth.signOut()
    }

    override fun onCleared() {
        super.onCleared()
        faceNetModel.close()
    }
}