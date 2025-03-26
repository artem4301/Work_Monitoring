package com.example.workmonitoring.viewmodel

import android.content.res.AssetManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.workmonitoring.data.FirebaseRepository
import com.google.firebase.auth.FirebaseAuth

class HomeViewModelFactory(
    private val assetManager: AssetManager,  // Добавляем AssetManager
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

