package com.example.workmonitoring.model

data class User(
    var uid: String = "",
    var firstName: String = "",
    var lastName: String = "",
    var role: String = "",
    var workZone: String? = null
)
