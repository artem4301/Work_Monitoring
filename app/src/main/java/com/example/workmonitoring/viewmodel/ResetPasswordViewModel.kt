package com.example.workmonitoring.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.workmonitoring.data.FirebaseRepository

class ResetPasswordViewModel(private val repository: FirebaseRepository) : ViewModel() {

    private val _resetPasswordStatus = MutableLiveData<Result<Boolean>>()
    val resetPasswordStatus: LiveData<Result<Boolean>> = _resetPasswordStatus

    fun resetPassword(email: String) {
        repository.resetPassword(email,
            onSuccess = { _resetPasswordStatus.postValue(Result.success(true)) },
            onFailure = { error -> _resetPasswordStatus.postValue(Result.failure(Exception(error))) }
        )
    }
}

