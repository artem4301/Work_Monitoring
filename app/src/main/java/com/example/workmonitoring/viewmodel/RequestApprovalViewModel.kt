package com.example.workmonitoring.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RequestApprovalViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _requestStatus = MutableLiveData<RequestState>()
    val requestStatus: LiveData<RequestState> = _requestStatus

    sealed class RequestState {
        data class Success(val requestId: String, val managerId: String?) : RequestState()
        object NoRequest : RequestState()
        data class Error(val message: String) : RequestState()
        object Updated : RequestState()
    }

    fun loadRequest() {
        val currentUserId = auth.currentUser?.uid ?: return
        db.collection("requests")
            .whereEqualTo("workerId", currentUserId)
            .whereEqualTo("status", "pending")
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val request = documents.documents[0]
                    _requestStatus.value = RequestState.Success(request.id, request.getString("managerId"))
                } else {
                    _requestStatus.value = RequestState.NoRequest
                }
            }
            .addOnFailureListener { error ->
                _requestStatus.value = RequestState.Error(error.message ?: "Ошибка")
            }
    }

    fun updateRequestStatus(requestId: String, status: String) {
        db.collection("requests")
            .document(requestId)
            .update("status", status)
            .addOnSuccessListener {
                _requestStatus.value = RequestState.Updated
            }
            .addOnFailureListener { error ->
                _requestStatus.value = RequestState.Error(error.message ?: "Ошибка обновления")
            }
    }
}
