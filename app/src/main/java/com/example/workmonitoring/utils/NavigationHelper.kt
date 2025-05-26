package com.example.workmonitoring.utils

import android.content.Context
import android.content.Intent
import com.example.workmonitoring.data.FirebaseRepository
import com.example.workmonitoring.ui.HomeActivity
import com.example.workmonitoring.ui.ManagerHomeActivity
import com.example.workmonitoring.ui.WorkTimeActivity
import com.google.firebase.auth.FirebaseAuth

object NavigationHelper {
    
    /**
     * Определяет правильную активность для навигации в зависимости от роли пользователя и состояния смены
     */
    fun navigateToAppropriateActivity(context: Context, finishCurrent: Boolean = true) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val repository = FirebaseRepository()
        
        repository.getCurrentUser { user ->
            if (user != null) {
                val intent = when {
                    user.role == "manager" -> {
                        Intent(context, ManagerHomeActivity::class.java)
                    }
                    user.isActive == true && user.shiftStartTime != null -> {
                        // Смена активна - переходим к WorkTimeActivity
                        Intent(context, WorkTimeActivity::class.java)
                    }
                    else -> {
                        // Смена не активна - переходим к HomeActivity
                        Intent(context, HomeActivity::class.java)
                    }
                }
                
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                
                if (finishCurrent && context is android.app.Activity) {
                    context.finish()
                }
            }
        }
    }
    
    /**
     * Проверяет, активна ли смена у текущего пользователя
     */
    fun checkActiveShift(callback: (Boolean) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            callback(false)
            return
        }
        
        val repository = FirebaseRepository()
        repository.getCurrentUser { user ->
            val hasActiveShift = user?.isActive == true && user.shiftStartTime != null
            callback(hasActiveShift)
        }
    }
    
    /**
     * Возвращает Intent для правильной активности в зависимости от состояния пользователя
     */
    fun getAppropriateIntent(context: Context, user: com.example.workmonitoring.model.User): Intent {
        android.util.Log.d("NavigationHelper", "Determining intent for user:")
        android.util.Log.d("NavigationHelper", "role: ${user.role}")
        android.util.Log.d("NavigationHelper", "isActive: ${user.isActive}")
        android.util.Log.d("NavigationHelper", "shiftStartTime: ${user.shiftStartTime}")
        android.util.Log.d("NavigationHelper", "activeShiftStartTime: ${user.activeShiftStartTime}")
        
        return when {
            user.role == "manager" -> {
                android.util.Log.d("NavigationHelper", "Navigating to ManagerHomeActivity")
                Intent(context, ManagerHomeActivity::class.java)
            }
            user.isActive == true && user.shiftStartTime != null -> {
                android.util.Log.d("NavigationHelper", "Navigating to WorkTimeActivity")
                Intent(context, WorkTimeActivity::class.java)
            }
            else -> {
                android.util.Log.d("NavigationHelper", "Navigating to HomeActivity")
                Intent(context, HomeActivity::class.java)
            }
        }
    }
} 