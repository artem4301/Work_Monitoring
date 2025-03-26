package com.example.workmonitoring.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.*

class FirebaseRepository {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    /**
     * Получает эмбеддинги пользователя из Firestore.
     */
    fun getUserEmbeddings(userId: String, onSuccess: (List<List<Float>>) -> Unit, onFailure: (String) -> Unit) {
        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val embeddings = document.data?.filterKeys { key ->
                        key.startsWith("embedding_") && !key.endsWith("_timestamp")
                    }?.mapNotNull { it.value as? List<Float> } ?: emptyList()

                    if (embeddings.isNotEmpty()) {
                        Log.d("FirebaseRepository", "Загружено ${embeddings.size} эмбеддингов")
                        onSuccess(embeddings)
                    } else {
                        Log.e("FirebaseRepository", "Эмбеддинги не найдены!")
                        onFailure("Эмбеддинги не найдены в базе данных!")
                    }
                } else {
                    Log.e("FirebaseRepository", "Документ пользователя не найден!")
                    onFailure("Эмбеддинги не найдены!")
                }
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseRepository", "Ошибка при загрузке эмбеддингов: ${e.localizedMessage}")
                onFailure("Ошибка при загрузке данных: ${e.localizedMessage}")
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
            .set(embeddings, SetOptions.merge()) // Добавляем, а не перезаписываем!
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

    fun saveUserData(firstName: String, lastName: String, email: String, onComplete: (Boolean) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        val userData = mapOf(
            "firstName" to firstName,
            "lastName" to lastName,
            "email" to email
        )

        db.collection("users").document(userId)
            .set(userData)
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

    /**
     * Выход из аккаунта.
     */
    fun logout() {
        auth.signOut()
        Log.d("FirebaseRepository", "Пользователь вышел из системы")
    }
}
