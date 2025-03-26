package com.example.workmonitoring.viewmodel

import android.content.res.AssetManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.workmonitoring.data.FirebaseRepository
import com.google.firebase.auth.FirebaseAuth

class FaceRegistrationViewModelFactory(
    private val assetManager: AssetManager,
    private val firebaseRepository: FirebaseRepository,
    private val auth: FirebaseAuth
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FaceRegistrationViewModel::class.java)) {
            return FaceRegistrationViewModel(assetManager, firebaseRepository, auth) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
