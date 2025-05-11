package com.example.workmonitoring.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.model.User

class AddWorkerViewModel(private val firebaseRepository: FirebaseRepository) : ViewModel() {

    private val _workers = MutableLiveData<List<User>>()
    val workers: LiveData<List<User>> get() = _workers

    fun loadWorkers() {
        firebaseRepository.getAvailableWorkers { workerList ->
            _workers.postValue(workerList)
        }
    }

    fun sendRequest(workerId: String, callback: (Boolean) -> Unit) {
        firebaseRepository.sendRequest(workerId, callback)
    }
}
