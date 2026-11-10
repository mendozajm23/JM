package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class PayrollRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val employeeId: String = "",
    val employeeName: String = "",
    val periodType: String = "quincenal", // "quincenal" | "mensual"
    val periodLabel: String = "",
    val baseSalaryPeriod: Double = 0.0,
    val overtimeHours: Double = 0.0,
    val overtimePay: Double = 0.0,
    val bonuses: Double = 0.0,
    val grossPay: Double = 0.0,
    val inssEmployee: Double = 0.0,
    val taxableBasePeriod: Double = 0.0,
    val projectedAnnualIncome: Double = 0.0,
    val irBracketLabel: String = "",
    val irPeriod: Double = 0.0,
    val otherDeductions: Double = 0.0,
    val netPay: Double = 0.0,
    val status: String = "borrador", // "borrador" | "pagado"
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)
