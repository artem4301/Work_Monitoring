package com.example.workmonitoring.model

data class User(
    var uid: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val role: String = "",
    val workZoneAddress: String? = null,
    val workZoneLatitude: Double? = null,
    val workZoneLongitude: Double? = null,
    val workZoneRadius: Double? = null,
    val inZone: Boolean = false
)
