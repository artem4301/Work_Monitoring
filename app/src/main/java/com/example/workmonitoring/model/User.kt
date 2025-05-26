package com.example.workmonitoring.model

data class User(
    var uid: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val role: String = "",
    // Рабочая зона
    val workZoneAddress: String? = null,
    val workZoneLatitude: Double? = null,
    val workZoneLongitude: Double? = null,
    val workZoneRadius: Double? = null,
    // Статус нахождения в зоне
    var isInZone: Boolean = false,
    // Информация о смене
    val workTime: List<WorkTimeEntry> = emptyList(),
    var isActive: Boolean = false,
    var activeShiftStartTime: Long? = null,
    var shiftStartTime: Long? = null,
    var totalPauseDuration: Long? = null,
    var pauseStartTime: Long? = null,
    // Периодическая верификация
    var lastVerificationTime: Long? = null,
    var verificationRequired: Boolean = false,
    var shiftPaused: Boolean = false,
    var pauseReason: String? = null,
    // Статистика верификаций
    var totalVerifications: Int = 0,
    var failedVerifications: Int = 0
)

data class WorkTimeEntry(
    val startTime: Long,
    val endTime: Long,
    val date: String,
    val duration: Long,
    val verificationCount: Int = 0,
    val zoneExitCount: Int = 0,
    val pauseDuration: Long = 0
)

data class VerificationEntry(
    val timestamp: Long,
    val success: Boolean,
    val similarity: Double,
    val method: String,
    val reason: String? = null
)