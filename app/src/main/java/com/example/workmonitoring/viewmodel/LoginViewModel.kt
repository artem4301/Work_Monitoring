package com.example.workmonitoring.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.workmonitoring.data.FirebaseRepository

class LoginViewModel(private val firebaseRepository: FirebaseRepository) : ViewModel() {

    private val _loginResult = MutableLiveData<Result<Boolean>>()
    val loginResult: LiveData<Result<Boolean>> = _loginResult

    fun login(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            _loginResult.value = Result.failure(Exception("Пожалуйста, заполните все поля"))
            return
        }

        firebaseRepository.signIn(email, password, {
            _loginResult.value = Result.success(true)
        }, { error ->
            _loginResult.value = Result.failure(Exception(error))
        })
    }

    fun loadUserRole(uid: String, callback: (String) -> Unit) {
        firebaseRepository.getUserRole(uid) { role ->
            callback(role ?: "worker")  // если что-то пойдет не так — по умолчанию worker
        }
    }

}
