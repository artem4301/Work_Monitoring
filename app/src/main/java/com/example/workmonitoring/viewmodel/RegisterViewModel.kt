package com.example.workmonitoring.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.workmonitoring.data.FirebaseRepository

class RegisterViewModel(private val firebaseRepository: FirebaseRepository) : ViewModel() {

    private val _registerResult = MutableLiveData<Result<Unit>>()
    val registerResult: LiveData<Result<Unit>> get() = _registerResult

    fun register(firstName: String, lastName: String, email: String, password: String) {
        firebaseRepository.register(email, password, {
            firebaseRepository.saveUserData(firstName, lastName, email) { success ->
                if (success) {
                    _registerResult.postValue(Result.success(Unit))
                } else {
                    _registerResult.postValue(Result.failure(Exception("Ошибка сохранения данных пользователя")))
                }
            }
        }, { error ->
            _registerResult.postValue(Result.failure(Exception(error)))
        })
    }
}
