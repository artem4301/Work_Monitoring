package com.example.workmonitoring.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.face.FaceNetModel
import com.example.workmonitoring.viewmodel.FaceControlViewModel
import com.google.firebase.auth.FirebaseAuth

class FaceControlViewModelFactory(
    private val faceNetModel: FaceNetModel,
    private val firebaseRepository: FirebaseRepository,
    private val auth: FirebaseAuth
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FaceControlViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FaceControlViewModel(faceNetModel, firebaseRepository, auth) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
