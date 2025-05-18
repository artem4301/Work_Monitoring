package com.example.workmonitoring.data

import android.util.Log
import com.example.workmonitoring.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class FirebaseRepository {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    /**
     * Получает эмбеддинги пользователя из Firestore.
     */
    fun getUserEmbeddings(
        userId: String,
        onSuccess: (List<List<Float>>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val rawEmbeddings = document.data?.filterKeys { it.startsWith("embedding_raw_") }

                    if (rawEmbeddings != null && rawEmbeddings.isNotEmpty()) {
                        val embeddings = rawEmbeddings.entries.sortedBy { it.key }
                            .mapNotNull { entry ->
                                (entry.value as? List<*>)?.mapNotNull { it as? Number }?.map { it.toFloat() }
                            }

                        onSuccess(embeddings)
                    } else {
                        onFailure("Эмбеддинги не найдены")
                    }
                } else {
                    onFailure("Документ пользователя не найден")
                }
            }
            .addOnFailureListener { e ->
                onFailure("Ошибка загрузки: ${e.localizedMessage}")
            }
    }


    /**
     * Сохраняет эмбеддинги пользователя в Firestore.
     */
    fun saveEmbeddings(
        userId: String,
        embeddings: Map<String, Any>,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        db.collection("users")
            .document(userId)
            .update(embeddings) // Используем update вместо set с merge
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { error ->
                onFailure("Ошибка сохранения: ${error.localizedMessage}")
            }
    }


    /**
     * Аутентификация пользователя.
     */
    fun signIn(email: String, password: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FirebaseRepository", "Вход выполнен успешно")
                    onSuccess()
                } else {
                    Log.e("FirebaseRepository", "Ошибка входа: ${task.exception?.message}")
                    onFailure(task.exception?.message ?: "Ошибка входа")
                }
            }
    }

    /**
     * Регистрация нового пользователя.
     */
    fun register(email: String, password: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FirebaseRepository", "Регистрация успешна")
                    onSuccess()
                } else {
                    Log.e("FirebaseRepository", "Ошибка регистрации: ${task.exception?.message}")
                    onFailure(task.exception?.message ?: "Ошибка регистрации")
                }
            }
    }

    fun saveUserData(firstName: String, lastName: String, email: String, role: String, onComplete: (Boolean) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return onComplete(false)

        val userMap = mapOf(
            "firstName" to firstName,
            "lastName" to lastName,
            "email" to email,
            "role" to role
        )

        db.collection("users").document(userId)
            .set(userMap)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }


    /**
     * Сброс пароля по email.
     */
    fun resetPassword(email: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FirebaseRepository", "Письмо для сброса пароля отправлено")
                    onSuccess()
                } else {
                    Log.e("FirebaseRepository", "Ошибка сброса пароля: ${task.exception?.message}")
                    onFailure(task.exception?.message ?: "Ошибка сброса пароля")
                }
            }
    }

    fun getUserRole(uid: String, callback: (String?) -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                val role = document.getString("role")
                callback(role)
            }
            .addOnFailureListener {
                callback(null)
            }
    }



    fun sendRequest(workerId: String, callback: (Boolean) -> Unit) {
        val managerId = auth.currentUser?.uid ?: return callback(false)
        val request = hashMapOf(
            "managerId" to managerId,
            "workerId" to workerId,
            "status" to "pending"
        )
        db.collection("requests") // здесь было firestore
            .add(request)
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    fun getAvailableWorkers(callback: (List<User>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val managerId = auth.currentUser?.uid ?: return callback(emptyList())

        // Сначала загружаем всех работников
        db.collection("users")
            .whereEqualTo("role", "worker")
            .get()
            .addOnSuccessListener { workersSnapshot ->
                val allWorkers = workersSnapshot.documents.mapNotNull { doc ->
                    val user = doc.toObject(User::class.java)
                    user?.uid = doc.id
                    user
                }

                // Теперь загружаем все активные запросы для этого управляющего
                db.collection("requests")
                    .whereEqualTo("managerId", managerId)
                    .whereIn("status", listOf("pending", "approved"))
                    .get()
                    .addOnSuccessListener { requestsSnapshot ->
                        val requestedWorkerIds = requestsSnapshot.documents.mapNotNull { it.getString("workerId") }

                        // Фильтруем работников, у которых еще НЕТ активного запроса
                        val availableWorkers = allWorkers.filter { it.uid !in requestedWorkerIds }

                        callback(availableWorkers)
                    }
                    .addOnFailureListener {
                        callback(emptyList())
                    }
            }
            .addOnFailureListener {
                callback(emptyList())
            }
    }

    fun assignWorkZone(
        workerId: String,
        address: String,
        latitude: Double,
        longitude: Double,
        radius: Double,
        callback: (Boolean) -> Unit
    ) {
        val zoneData = mapOf(
            "workZoneAddress" to address,
            "workZoneLatitude" to latitude,
            "workZoneLongitude" to longitude,
            "workZoneRadius" to radius,
            "inZone" to false
        )

        db.collection("users")
            .document(workerId)
            .update(zoneData)
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    fun getUserWorkZone(userId: String, onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val address = document.getString("workZoneAddress") ?: "Зона не выбрана"
                    onSuccess(address)
                } else {
                    onFailure("Документ пользователя не найден")
                }
            }
            .addOnFailureListener { e ->
                onFailure(e.localizedMessage ?: "Ошибка при загрузке зоны")
            }
    }

    fun getUserName(userId: String, onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val firstName = document.getString("firstName") ?: ""
                    val lastName = document.getString("lastName") ?: ""
                    val fullName = "$lastName $firstName".trim()
                    onSuccess(fullName)
                } else {
                    onFailure("Пользователь не найден")
                }
            }
            .addOnFailureListener { e ->
                onFailure("Ошибка загрузки данных: ${e.localizedMessage}")
            }
    }

    fun getCurrentUser(callback: (User?) -> Unit) {
        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            db.collection("users")
                .document(firebaseUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val userData = document.data
                        if (userData != null) {
                            val user = User(
                                uid = firebaseUser.uid,
                                firstName = userData["firstName"] as? String ?: "",
                                lastName = userData["lastName"] as? String ?: "",
                                email = firebaseUser.email ?: "",
                                role = userData["role"] as? String ?: "",
                                workZoneAddress = userData["workZoneAddress"] as? String,
                                workZoneLatitude = userData["workZoneLatitude"] as? Double,
                                workZoneLongitude = userData["workZoneLongitude"] as? Double,
                                workZoneRadius = userData["workZoneRadius"] as? Double,
                                inZone = userData["inZone"] as? Boolean ?: false
                            )
                            callback(user)
                        } else {
                            callback(null)
                        }
                    } else {
                        callback(null)
                    }
                }
                .addOnFailureListener {
                    callback(null)
                }
        } else {
            callback(null)
        }
    }

    /**
     * Выход из аккаунта.
     */
    fun logout() {
        auth.signOut()
        Log.d("FirebaseRepository", "Пользователь вышел из системы")
    }

    fun updateWorkerLocationStatus(userId: String, inZone: Boolean) {
        db.collection("users")
            .document(userId)
            .update("inZone", inZone)
    }

    fun getWorkerLocationStatus(userId: String, callback: (Boolean) -> Unit) {
        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val inZone = document.getBoolean("inZone") ?: false
                    callback(inZone)
                } else {
                    callback(false)
                }
            }
            .addOnFailureListener {
                callback(false)
            }
    }
}
