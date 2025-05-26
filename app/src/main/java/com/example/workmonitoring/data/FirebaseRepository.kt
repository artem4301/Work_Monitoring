package com.example.workmonitoring.data

import android.util.Log
import com.example.workmonitoring.model.User
import com.example.workmonitoring.model.WorkTimeEntry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            .set(embeddings, SetOptions.merge())
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
        db.collection("requests")
            .add(request)
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    fun getAvailableWorkers(callback: (List<User>) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val managerId = auth.currentUser?.uid ?: return callback(emptyList())

        db.collection("users")
            .whereEqualTo("role", "worker")
            .get()
            .addOnSuccessListener { workersSnapshot ->
                val allWorkers = workersSnapshot.documents.mapNotNull { doc ->
                    val user = doc.toObject(User::class.java)
                    user?.uid = doc.id
                    user
                }

                db.collection("requests")
                    .whereEqualTo("managerId", managerId)
                    .whereIn("status", listOf("pending", "approved"))
                    .get()
                    .addOnSuccessListener { requestsSnapshot ->
                        val requestedWorkerIds = requestsSnapshot.documents.mapNotNull { it.getString("workerId") }
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

    /**
     * Получает список работников, связанных с текущим менеджером (для отчетов)
     */
    fun getAssignedWorkers(callback: (List<User>) -> Unit) {
        val managerId = auth.currentUser?.uid ?: return callback(emptyList())
        Log.d("FirebaseRepository", "Поиск назначенных работников для менеджера: $managerId")

        db.collection("requests")
            .whereEqualTo("managerId", managerId)
            .whereEqualTo("status", "approved")
            .get()
            .addOnSuccessListener { requestsSnapshot ->
                val workerIds = requestsSnapshot.documents.mapNotNull { it.getString("workerId") }
                Log.d("FirebaseRepository", "Найдено одобренных запросов: ${requestsSnapshot.size()}, ID работников: $workerIds")
                
                if (workerIds.isEmpty()) {
                    Log.d("FirebaseRepository", "Нет назначенных работников")
                    callback(emptyList())
                    return@addOnSuccessListener
                }

                db.collection("users")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), workerIds)
                    .get()
                    .addOnSuccessListener { workersSnapshot ->
                        val workers = workersSnapshot.documents.mapNotNull { doc ->
                            val user = doc.toObject(User::class.java)
                            user?.uid = doc.id
                            user
                        }
                        Log.d("FirebaseRepository", "Загружено работников: ${workers.size}")
                        workers.forEach { worker ->
                            Log.d("FirebaseRepository", "Работник: ${worker.firstName} ${worker.lastName} (${worker.uid})")
                        }
                        callback(workers)
                    }
                    .addOnFailureListener { e ->
                        Log.e("FirebaseRepository", "Ошибка загрузки данных работников", e)
                        callback(emptyList())
                    }
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseRepository", "Ошибка загрузки запросов", e)
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
            "isInZone" to false
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
                                isInZone = userData["isInZone"] as? Boolean ?: false,
                                isActive = userData["isActive"] as? Boolean ?: false,
                                activeShiftStartTime = userData["activeShiftStartTime"] as? Long,
                                shiftStartTime = userData["shiftStartTime"] as? Long,
                                totalPauseDuration = userData["totalPauseDuration"] as? Long,
                                pauseStartTime = userData["pauseStartTime"] as? Long,
                                lastVerificationTime = userData["lastVerificationTime"] as? Long,
                                verificationRequired = userData["verificationRequired"] as? Boolean ?: false,
                                shiftPaused = userData["shiftPaused"] as? Boolean ?: false,
                                pauseReason = userData["pauseReason"] as? String,
                                totalVerifications = (userData["totalVerifications"] as? Long)?.toInt() ?: 0,
                                failedVerifications = (userData["failedVerifications"] as? Long)?.toInt() ?: 0
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



    fun startShift(userId: String, callback: (Boolean) -> Unit) {
        val currentTime = System.currentTimeMillis()
        val updates = hashMapOf<String, Any>(
            "isActive" to true,
            "activeShiftStartTime" to currentTime,
            "shiftStartTime" to currentTime,
            "totalPauseDuration" to 0L,
            "shiftPaused" to false,
            "verificationRequired" to false,
            "lastVerificationTime" to currentTime
        )

        db.collection("users").document(userId)
            .update(updates)
            .addOnSuccessListener {
                android.util.Log.d("FirebaseRepository", "Shift started successfully for user: $userId")
                callback(true)
            }
            .addOnFailureListener { exception ->
                android.util.Log.e("FirebaseRepository", "Failed to start shift for user: $userId", exception)
                callback(false)
            }
    }

    fun endShift(userId: String, callback: (Boolean) -> Unit) {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val startTime = document.getLong("activeShiftStartTime") ?: 0L
                    val endTime = System.currentTimeMillis()
                    val duration = endTime - startTime

                    Log.d("FirebaseRepository", "Завершение смены: startTime=$startTime, endTime=$endTime, duration=$duration")
                    Log.d("FirebaseRepository", "Дата начала: ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(startTime))}")
                    Log.d("FirebaseRepository", "Дата окончания: ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(endTime))}")

                    val workTimeEntry = hashMapOf(
                        "startTime" to startTime,
                        "endTime" to endTime,
                        "duration" to duration,
                        "date" to SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(startTime))
                    )

                    val updates = hashMapOf<String, Any>(
                        "isActive" to false,
                        "activeShiftStartTime" to 0L,
                        "shiftStartTime" to com.google.firebase.firestore.FieldValue.delete(),
                        "totalPauseDuration" to 0L,
                        "shiftPaused" to false,
                        "verificationRequired" to false,
                        "isInZone" to false
                    )

                    // Сначала обновляем статус пользователя
                    db.collection("users").document(userId)
                        .update(updates)
                        .addOnSuccessListener {
                            // Затем добавляем запись в историю
                            db.collection("users").document(userId)
                                .collection("workTimeHistory")
                                .add(workTimeEntry)
                                .addOnSuccessListener {
                                    callback(true)
                                }
                                .addOnFailureListener {
                                    callback(false)
                                }
                        }
                        .addOnFailureListener {
                            callback(false)
                        }
                } else {
                    callback(false)
                }
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    /**
     * Сохраняет время работы в Firestore.
     */


    fun updateWorkerLocationStatus(userId: String, inZone: Boolean) {
        db.collection("users")
            .document(userId)
            .update("isInZone", inZone)
    }

    fun getWorkerLocationStatus(userId: String, callback: (Boolean) -> Unit) {
        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val inZone = document.getBoolean("isInZone") ?: false
                    callback(inZone)
                } else {
                    callback(false)
                }
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    /**
     * Устанавливает флаг необходимости верификации
     */
    fun setVerificationRequired(userId: String, required: Boolean, callback: (Boolean) -> Unit) {
        val updates = hashMapOf<String, Any>(
            "verificationRequired" to required
        )
        
        db.collection("users").document(userId)
            .update(updates)
            .addOnSuccessListener { callback(true) }
            .addOnFailureListener { callback(false) }
    }

    /**
     * Приостанавливает смену с указанием причины
     */
    fun pauseShift(userId: String, reason: String, callback: ((Boolean) -> Unit)? = null) {
        val updates = hashMapOf<String, Any>(
            "shiftPaused" to true,
            "pauseReason" to reason,
            "pauseStartTime" to System.currentTimeMillis()
        )
        
        db.collection("users").document(userId)
            .update(updates)
            .addOnSuccessListener { callback?.invoke(true) }
            .addOnFailureListener { callback?.invoke(false) }
    }

    /**
     * Возобновляет смену после успешной верификации
     */
    fun resumeShift(userId: String, callback: (Boolean) -> Unit) {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val pauseStartTime = document.getLong("pauseStartTime") ?: 0L
                    val pauseDuration = if (pauseStartTime > 0) {
                        System.currentTimeMillis() - pauseStartTime
                    } else 0L
                    
                    val updates = hashMapOf<String, Any>(
                        "shiftPaused" to false,
                        "pauseReason" to "",
                        "verificationRequired" to false,
                        "lastVerificationTime" to System.currentTimeMillis(),
                        "totalPauseDuration" to (document.getLong("totalPauseDuration") ?: 0L) + pauseDuration
                    )
                    
                    db.collection("users").document(userId)
                        .update(updates)
                        .addOnSuccessListener { callback(true) }
                        .addOnFailureListener { callback(false) }
                } else {
                    callback(false)
                }
            }
            .addOnFailureListener { callback(false) }
    }

    /**
     * Сохраняет результат верификации
     */
    fun saveVerificationResult(
        userId: String,
        success: Boolean,
        similarity: Double,
        method: String,
        reason: String? = null,
        callback: (Boolean) -> Unit
    ) {
        val verificationEntry = hashMapOf(
            "timestamp" to System.currentTimeMillis(),
            "success" to success,
            "similarity" to similarity,
            "method" to method,
            "reason" to reason
        )
        
        // Сохраняем в историю верификаций
        db.collection("users").document(userId)
            .collection("verificationHistory")
            .add(verificationEntry)
            .addOnSuccessListener {
                // Обновляем статистику пользователя
                updateVerificationStats(userId, success, callback)
            }
            .addOnFailureListener { callback(false) }
    }

    /**
     * Обновляет статистику верификаций пользователя
     */
    private fun updateVerificationStats(userId: String, success: Boolean, callback: (Boolean) -> Unit) {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val totalVerifications = (document.getLong("totalVerifications") ?: 0L) + 1
                    val failedVerifications = if (success) {
                        document.getLong("failedVerifications") ?: 0L
                    } else {
                        (document.getLong("failedVerifications") ?: 0L) + 1
                    }
                    
                    val updates = hashMapOf<String, Any>(
                        "totalVerifications" to totalVerifications,
                        "failedVerifications" to failedVerifications
                    )
                    
                    db.collection("users").document(userId)
                        .update(updates)
                        .addOnSuccessListener { callback(true) }
                        .addOnFailureListener { callback(false) }
                } else {
                    callback(false)
                }
            }
            .addOnFailureListener { callback(false) }
    }

    /**
     * Получает историю верификаций пользователя
     */
    fun getVerificationHistory(
        userId: String,
        callback: (List<Map<String, Any>>) -> Unit
    ) {
        db.collection("users").document(userId)
            .collection("verificationHistory")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { documents ->
                val history = documents.map { it.data }
                callback(history)
            }
            .addOnFailureListener {
                callback(emptyList())
            }
    }

    /**
     * Получает отчет по работнику для менеджера
     */
    fun getWorkerReport(
        workerId: String,
        startDate: Long,
        endDate: Long,
        callback: (Map<String, Any>) -> Unit
    ) {
        Log.d("FirebaseRepository", "Запрос отчета для работника: $workerId, период: ${java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(startDate))} - ${java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(endDate))}")
        
        // Получаем основную информацию о работнике
        db.collection("users").document(workerId)
            .get()
            .addOnSuccessListener { userDoc ->
                if (userDoc.exists()) {
                    val userData = userDoc.data ?: emptyMap()
                    Log.d("FirebaseRepository", "Найден пользователь: ${userData["firstName"]} ${userData["lastName"]}")
                    
                    // Получаем историю рабочего времени
                    // Ищем записи, которые пересекаются с заданным периодом
                    db.collection("users").document(workerId)
                        .collection("workTimeHistory")
                        .whereLessThanOrEqualTo("startTime", endDate)
                        .get()
                        .addOnSuccessListener { workTimeDoc ->
                            val allWorkTimeHistory = workTimeDoc.documents.map { it.data ?: emptyMap() }
                            Log.d("FirebaseRepository", "Найдено всего записей рабочего времени: ${allWorkTimeHistory.size}")
                            
                            // Фильтруем записи, которые пересекаются с заданным периодом
                            val workTimeHistory = allWorkTimeHistory.filter { entry ->
                                val entryStartTime = entry["startTime"] as? Long ?: 0L
                                val entryEndTime = entry["endTime"] as? Long ?: 0L
                                
                                // Запись пересекается с периодом, если:
                                // начало записи <= конец периода И конец записи >= начало периода
                                entryStartTime <= endDate && entryEndTime >= startDate
                            }
                            
                            Log.d("FirebaseRepository", "После фильтрации записей рабочего времени: ${workTimeHistory.size}")
                            workTimeHistory.forEach { entry ->
                                Log.d("FirebaseRepository", "Запись: startTime=${entry["startTime"]}, endTime=${entry["endTime"]}, duration=${entry["duration"]}")
                            }
                            
                            // Получаем историю верификаций
                            db.collection("users").document(workerId)
                                .collection("verificationHistory")
                                .whereGreaterThanOrEqualTo("timestamp", startDate)
                                .whereLessThanOrEqualTo("timestamp", endDate)
                                .get()
                                .addOnSuccessListener { verificationDoc ->
                                    val verificationHistory = verificationDoc.documents.map { it.data ?: emptyMap() }
                                    Log.d("FirebaseRepository", "Найдено записей верификации: ${verificationHistory.size}")
                                    
                                    val totalWorkTime = workTimeHistory.sumOf { 
                                        (it["duration"] as? Long) ?: 0L 
                                    }
                                    
                                    val report = mapOf(
                                        "user" to userData,
                                        "workTimeHistory" to workTimeHistory,
                                        "verificationHistory" to verificationHistory,
                                        "totalWorkTime" to totalWorkTime,
                                        "totalVerifications" to verificationHistory.size,
                                        "successfulVerifications" to verificationHistory.count { 
                                            (it["success"] as? Boolean) == true 
                                        }
                                    )
                                    
                                    Log.d("FirebaseRepository", "Отчет готов: totalWorkTime=$totalWorkTime, workDays=${workTimeHistory.size}")
                                    callback(report)
                                }
                                .addOnFailureListener { e ->
                                    Log.e("FirebaseRepository", "Ошибка загрузки истории верификаций", e)
                                    callback(mapOf("error" to "Ошибка загрузки истории верификаций"))
                                }
                        }
                        .addOnFailureListener { e ->
                            Log.e("FirebaseRepository", "Ошибка загрузки рабочего времени", e)
                            callback(mapOf("error" to "Ошибка загрузки рабочего времени"))
                        }
                } else {
                    Log.w("FirebaseRepository", "Работник не найден: $workerId")
                    callback(mapOf("error" to "Работник не найден"))
                }
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseRepository", "Ошибка загрузки данных работника", e)
                callback(mapOf("error" to "Ошибка загрузки данных работника"))
            }
    }
}