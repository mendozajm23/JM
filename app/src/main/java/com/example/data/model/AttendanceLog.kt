package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class AttendanceLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val employeeId: String = "",
    val employeeName: String = "",
    val workDate: String = "", // YYYY-MM-DD
    val clockInMillis: Long = 0L,
    val clockOutMillis: Long = 0L, // 0L when shift is still open
    val modality: String = "presencial", // "presencial" | "remoto" | "campo"
    val status: String = "abierto", // "abierto" | "completado" | "tardanza" | "incompleto"
    val regularHours: Double = 0.0,
    val overtimeHours: Double = 0.0,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)
