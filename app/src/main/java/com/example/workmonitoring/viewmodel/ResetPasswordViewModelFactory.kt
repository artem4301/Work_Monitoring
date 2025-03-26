package com.example.workmonitoring.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.ui.ResetPasswordViewModel

class ResetPasswordViewModelFactory(
    private val repository: FirebaseRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ResetPasswordViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ResetPasswordViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

