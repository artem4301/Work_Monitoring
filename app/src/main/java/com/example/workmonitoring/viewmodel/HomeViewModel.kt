package com.example.workmonitoring.viewmodel

import android.content.res.AssetManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.workmonitoring.data.FirebaseRepository
import com.google.firebase.auth.FirebaseAuth

class HomeViewModel(
    private val assetManager: AssetManager,
    private val firebaseRepository: FirebaseRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    fun getUserEmail(): String? {
        return auth.currentUser?.email
    }

    fun checkUserEmbeddings(
        onRegistered: () -> Unit,
        onNotRegistered: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val userId = firebaseRepository.getCurrentUserId()
        if (userId == null) {
            onFailure("Пользователь не авторизован!")
            return
        }

        firebaseRepository.getUserEmbeddings(userId, { embeddings ->
            Log.d("HomeViewModel", "Загружено эмбеддингов: ${embeddings.size}") // Логируем данные
            if (embeddings.isNotEmpty() && embeddings.size >= 10) {
                onRegistered()
            } else {
                onNotRegistered()
            }
        }, { error ->
            onFailure(error)
        })
    }

    fun getUserWorkZone(onResult: (String) -> Unit, onFailure: (String) -> Unit) {
        val userId = firebaseRepository.getCurrentUserId()
        if (userId == null) {
            onFailure("Пользователь не авторизован!")
            return
        }

        firebaseRepository.getUserWorkZone(userId, { zone ->
            onResult(zone)
        }, { error ->
            onFailure(error)
        })
    }


    fun logout() {
        firebaseRepository.logout()
    }

    class Factory(
        private val assetManager: AssetManager,
        private val firebaseRepository: FirebaseRepository,
        private val auth: FirebaseAuth
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(assetManager, firebaseRepository, auth) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
