package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class EmployeeProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val roleTitle: String = "",
    val department: String = "",
    val employeeCode: String = "",
    val monthlyBaseSalary: Double = 0.0,
    val payFrequency: String = "quincenal", // "quincenal" | "mensual"
    val dailyWorkHours: Double = 8.0,
    val overtimeMultiplier: Double = 2.0,
    val isSelf: Boolean = false,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)
