package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AttendanceLog
import com.example.data.model.EmployeeProfile
import com.example.data.model.PayrollRecord
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.domain.PayrollCalculator
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PayrollRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("El usuario debe iniciar sesión con Google antes de acceder a Firestore.")
    }

    private fun employeesCollection(userId: String) =
        db.collection("users").document(userId).collection("employees")

    private fun attendanceCollection(userId: String) =
        db.collection("users").document(userId).collection("attendance_logs")

    private fun payrollsCollection(userId: String) =
        db.collection("users").document(userId).collection("payrolls")

    fun observeEmployees(userId: String): Flow<List<EmployeeProfile>> = flow {
        val path = "users/$userId/employees"
        emitAll(
            employeesCollection(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(EmployeeProfile::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(compareByDescending<EmployeeProfile> { it.isSelf }.thenBy { it.fullName })
                }
                .catch { error ->
                    logStreamErrorIfAuthenticated(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeAttendanceLogs(userId: String): Flow<List<AttendanceLog>> = flow {
        val path = "users/$userId/attendance_logs"
        emitAll(
            attendanceCollection(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(AttendanceLog::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.clockInMillis }
                }
                .catch { error ->
                    logStreamErrorIfAuthenticated(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observePayrolls(userId: String): Flow<List<PayrollRecord>> = flow {
        val path = "users/$userId/payrolls"
        emitAll(
            payrollsCollection(userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(PayrollRecord::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamErrorIfAuthenticated(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    private fun logStreamErrorIfAuthenticated(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String,
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    suspend fun ensureSelfProfile(displayName: String?): Result<String> {
        return try {
            val uid = requireUserId()
            val docRef = employeesCollection(uid).document("self")
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val safeName = displayName?.takeIf { it.isNotBlank() }?.take(100) ?: "Colaborador Principal"
                val payload = mapOf(
                    "userId" to uid,
                    "fullName" to safeName,
                    "roleTitle" to "Especialista Administrativo",
                    "department" to "Administración",
                    "employeeCode" to "EMP-001",
                    "monthlyBaseSalary" to 22000.0,
                    "payFrequency" to "quincenal",
                    "dailyWorkHours" to 8.0,
                    "overtimeMultiplier" to 2.0,
                    "isSelf" to true,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
            }
            Result.success("self")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/employees/self")
            Result.failure(e)
        }
    }

    suspend fun saveEmployee(employee: EmployeeProfile): Result<String> {
        return try {
            val uid = requireUserId()
            val col = employeesCollection(uid)
            if (employee.id.isBlank()) {
                val docRef = col.document()
                val payload = mapOf(
                    "userId" to uid,
                    "fullName" to employee.fullName.trim().take(100),
                    "roleTitle" to employee.roleTitle.trim().take(80),
                    "department" to employee.department.trim().take(60),
                    "employeeCode" to employee.employeeCode.trim().take(30),
                    "monthlyBaseSalary" to employee.monthlyBaseSalary.coerceAtLeast(0.0),
                    "payFrequency" to if (employee.payFrequency == "mensual") "mensual" else "quincenal",
                    "dailyWorkHours" to employee.dailyWorkHours.coerceIn(1.0, 24.0),
                    "overtimeMultiplier" to employee.overtimeMultiplier.coerceIn(1.0, 4.0),
                    "isSelf" to employee.isSelf,
                    "active" to employee.active,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
                Result.success(docRef.id)
            } else {
                val docRef = col.document(employee.id)
                requireNotNull(employee.createdAt) { "createdAt must not be null when updating EmployeeProfile" }
                docRef.set(
                    employee.copy(
                        userId = uid,
                        fullName = employee.fullName.trim().take(100),
                        roleTitle = employee.roleTitle.trim().take(80),
                        department = employee.department.trim().take(60),
                        employeeCode = employee.employeeCode.trim().take(30),
                        updatedAt = null,
                    )
                ).await()
                Result.success(docRef.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}/employees")
            Result.failure(e)
        }
    }

    suspend fun getEmployeeById(employeeId: String): Result<EmployeeProfile?> {
        return try {
            val uid = requireUserId()
            val doc = employeesCollection(uid).document(employeeId).get().await()
            Result.success(doc.toObject(EmployeeProfile::class.java, ServerTimestampBehavior.ESTIMATE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/${auth.currentUser?.uid}/employees/$employeeId")
            Result.failure(e)
        }
    }

    suspend fun clockIn(
        employee: EmployeeProfile,
        modality: String,
        notes: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Result<String> {
        return try {
            val uid = requireUserId()
            require(employee.id.isNotBlank()) { "Employee ID is required for clock-in" }
            val workDate = formatWorkDate(nowMillis)
            val docRef = attendanceCollection(uid).document()
            val payload = mapOf(
                "userId" to uid,
                "employeeId" to employee.id,
                "employeeName" to employee.fullName,
                "workDate" to workDate,
                "clockInMillis" to nowMillis,
                "clockOutMillis" to 0L,
                "modality" to sanitizeModality(modality),
                "status" to "abierto",
                "regularHours" to 0.0,
                "overtimeHours" to 0.0,
                "notes" to notes.trim().take(300),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/${auth.currentUser?.uid}/attendance_logs")
            Result.failure(e)
        }
    }

    suspend fun clockOut(
        log: AttendanceLog,
        dailyWorkHours: Double,
        nowMillis: Long = System.currentTimeMillis(),
    ): Result<Unit> {
        return try {
            val uid = requireUserId()
            require(log.id.isNotBlank()) { "AttendanceLog ID is required for clock-out" }
            val safeClockOut = maxOf(nowMillis, log.clockInMillis)
            val hours = PayrollCalculator.calculateShiftHours(
                clockInMillis = log.clockInMillis,
                clockOutMillis = safeClockOut,
                dailyWorkHours = dailyWorkHours,
            )
            val docRef = attendanceCollection(uid).document(log.id)
            docRef.update(
                mapOf(
                    "clockOutMillis" to safeClockOut,
                    "status" to "completado",
                    "regularHours" to hours.regularHours,
                    "overtimeHours" to hours.overtimeHours,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}/attendance_logs/${log.id}")
            Result.failure(e)
        }
    }

    suspend fun createManualAttendance(
        employee: EmployeeProfile,
        workDate: String,
        clockInMillis: Long,
        clockOutMillis: Long,
        modality: String,
        status: String,
        notes: String,
    ): Result<String> {
        return try {
            val uid = requireUserId()
            require(employee.id.isNotBlank()) { "Employee ID is required" }
            val hours = if (clockOutMillis > clockInMillis) {
                PayrollCalculator.calculateShiftHours(clockInMillis, clockOutMillis, employee.dailyWorkHours)
            } else {
                com.example.domain.ShiftHoursResult(0.0, 0.0, 0.0)
            }
            val validStatus = if (status in listOf("abierto", "completado", "tardanza", "incompleto")) {
                status
            } else if (clockOutMillis == 0L) "incompleto" else "completado"

            val docRef = attendanceCollection(uid).document()
            val payload = mapOf(
                "userId" to uid,
                "employeeId" to employee.id,
                "employeeName" to employee.fullName,
                "workDate" to workDate,
                "clockInMillis" to clockInMillis,
                "clockOutMillis" to clockOutMillis,
                "modality" to sanitizeModality(modality),
                "status" to validStatus,
                "regularHours" to hours.regularHours,
                "overtimeHours" to hours.overtimeHours,
                "notes" to notes.trim().take(300),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/${auth.currentUser?.uid}/attendance_logs")
            Result.failure(e)
        }
    }

    suspend fun deleteAttendanceLog(logId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            attendanceCollection(uid).document(logId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/${auth.currentUser?.uid}/attendance_logs/$logId")
            Result.failure(e)
        }
    }

    suspend fun savePayroll(payroll: PayrollRecord): Result<String> {
        return try {
            val uid = requireUserId()
            val docRef = payrollsCollection(uid).document()
            val payload = mapOf(
                "userId" to uid,
                "employeeId" to payroll.employeeId,
                "employeeName" to payroll.employeeName.trim().take(100),
                "periodType" to if (payroll.periodType == "mensual") "mensual" else "quincenal",
                "periodLabel" to payroll.periodLabel.trim().take(60),
                "baseSalaryPeriod" to payroll.baseSalaryPeriod,
                "overtimeHours" to payroll.overtimeHours,
                "overtimePay" to payroll.overtimePay,
                "bonuses" to payroll.bonuses,
                "grossPay" to payroll.grossPay,
                "inssEmployee" to payroll.inssEmployee,
                "taxableBasePeriod" to payroll.taxableBasePeriod,
                "projectedAnnualIncome" to payroll.projectedAnnualIncome,
                "irBracketLabel" to payroll.irBracketLabel.take(60),
                "irPeriod" to payroll.irPeriod,
                "otherDeductions" to payroll.otherDeductions,
                "netPay" to payroll.netPay,
                "status" to if (payroll.status == "pagado") "pagado" else "borrador",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/${auth.currentUser?.uid}/payrolls")
            Result.failure(e)
        }
    }

    suspend fun markPayrollPaid(payrollId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            payrollsCollection(uid).document(payrollId).update(
                mapOf(
                    "status" to "pagado",
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/${auth.currentUser?.uid}/payrolls/$payrollId")
            Result.failure(e)
        }
    }

    suspend fun deletePayroll(payrollId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            payrollsCollection(uid).document(payrollId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/${auth.currentUser?.uid}/payrolls/$payrollId")
            Result.failure(e)
        }
    }

    private fun sanitizeModality(modality: String): String {
        return if (modality in listOf("presencial", "remoto", "campo")) modality else "presencial"
    }

    private fun formatWorkDate(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date(millis))
    }
}
