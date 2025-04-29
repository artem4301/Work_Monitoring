package com.example.workmonitoring.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.workmonitoring.data.FirebaseRepository

class RegisterViewModel(private val firebaseRepository: FirebaseRepository) : ViewModel() {

    private val _registerResult = MutableLiveData<Result<String>>() // теперь строка = роль
    val registerResult: LiveData<Result<String>> get() = _registerResult

    fun register(firstName: String, lastName: String, email: String, password: String, role: String) {
        firebaseRepository.register(email, password, {
            firebaseRepository.saveUserData(firstName, lastName, email, role) { success ->
                if (success) {
                    _registerResult.postValue(Result.success(role)) // теперь возвращаем роль
                } else {
                    _registerResult.postValue(Result.failure(Exception("Ошибка сохранения данных пользователя")))
                }
            }
        }, { error ->
            _registerResult.postValue(Result.failure(Exception(error)))
        })
    }

}
