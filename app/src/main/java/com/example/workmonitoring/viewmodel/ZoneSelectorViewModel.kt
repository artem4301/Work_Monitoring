package com.example.workmonitoring.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.workmonitoring.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldPath

class ZoneSelectorViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _workers = MutableLiveData<List<User>>()
    val workers: LiveData<List<User>> get() = _workers

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> get() = _error

    fun loadApprovedWorkers() {
        val managerId = auth.currentUser?.uid ?: return

        db.collection("requests")
            .whereEqualTo("managerId", managerId)
            .whereEqualTo("status", "approved")
            .get()
            .addOnSuccessListener { requestSnapshots ->
                val workerIds = requestSnapshots.documents.mapNotNull { it.getString("workerId") }

                if (workerIds.isEmpty()) {
                    _workers.postValue(emptyList())
                    return@addOnSuccessListener
                }

                db.collection("users")
                    .whereIn(FieldPath.documentId(), workerIds)
                    .get()
                    .addOnSuccessListener { userSnapshots ->
                        val workers = userSnapshots.map { doc ->
                            User(
                                uid = doc.id,
                                firstName = doc.getString("firstName") ?: "",
                                lastName = doc.getString("lastName") ?: "",
                                email = doc.getString("email") ?: "",
                                role = doc.getString("role") ?: "",
                                workZoneAddress = doc.getString("workZoneAddress"),
                                workZoneLatitude = doc.getDouble("workZoneLatitude"),
                                workZoneLongitude = doc.getDouble("workZoneLongitude"),
                                workZoneRadius = doc.getDouble("workZoneRadius"),
                                inZone = doc.getBoolean("inZone") ?: false
                            )
                        }

                        _workers.postValue(workers)
                    }
            }
            .addOnFailureListener { e ->
                _error.postValue("Ошибка загрузки запросов: ${e.localizedMessage}")
            }
    }
}
