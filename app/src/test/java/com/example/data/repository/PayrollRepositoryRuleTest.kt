package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.EmployeeProfile
import com.example.data.model.PayrollRecord
import com.example.domain.PayrollCalculator
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PayrollRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureSelfProfile_authenticatedOwner_createsAndEmitsEmployee() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = PayrollRepository(firestore, auth)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) { repository.ensureSelfProfile("Alice Mendoza") }
        assertTrue(result.isSuccess)

        val employees = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeEmployees(uid).first { list -> list.any { it.id == "self" } }
        }
        val selfProfile = employees.first { it.id == "self" }
        assertEquals("Alice Mendoza", selfProfile.fullName)
        assertEquals(uid, selfProfile.userId)
    }

    @Test
    fun clockInAndClockOut_validEmployee_persistsAttendanceAndCalculatesOvertime() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = PayrollRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) { repository.ensureSelfProfile("Alice Mendoza").getOrThrow() }

        val emp = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getEmployeeById("self").getOrThrow()
        }
        assertNotNull(emp)

        val now = 1_762_700_000_000L
        val logId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.clockIn(
                employee = emp!!,
                modality = "presencial",
                notes = "Turno matutino",
                nowMillis = now
            ).getOrThrow()
        }

        val logsAfterIn = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeAttendanceLogs(uid).first { list -> list.any { it.id == logId } }
        }
        val openLog = logsAfterIn.first { it.id == logId }
        assertEquals("abierto", openLog.status)

        // Clock out 9.5 hours later -> 8.0 regular + 1.5 overtime
        val clockOutTime = now + (9.5 * 3_600_000.0).toLong()
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.clockOut(openLog, emp!!.dailyWorkHours, clockOutTime).getOrThrow()
        }

        val logsAfterOut = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeAttendanceLogs(uid).first { list ->
                list.any { it.id == logId && it.status == "completado" }
            }
        }
        val closedLog = logsAfterOut.first { it.id == logId }
        assertEquals(8.0, closedLog.regularHours, 0.01)
        assertEquals(1.5, closedLog.overtimeHours, 0.01)
    }

    @Test
    fun observeEmployees_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = PayrollRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) { aliceRepo.ensureSelfProfile("Alice Mendoza").getOrThrow() }

        signInTestUser(BOB_EMAIL)
        val bobRepo = PayrollRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeEmployees(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED for cross-user read")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
        }
    }

    @Test
    fun observePayrolls_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = PayrollRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observePayrolls("some_uid_123").first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated read")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
        }
    }

    @Test
    fun saveAndMarkPayrollPaid_enforcesTerminalStateLock() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = PayrollRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) { repository.ensureSelfProfile("Alice Mendoza").getOrThrow() }

        val breakdown = PayrollCalculator.calculatePayroll(
            monthlyBaseSalary = 24000.0,
            periodType = "quincenal",
            overtimeHours = 4.0,
        )
        val record = PayrollRecord(
            userId = uid,
            employeeId = "self",
            employeeName = "Alice Mendoza",
            periodType = "quincenal",
            periodLabel = "1ra Quincena Nov 2026",
            baseSalaryPeriod = breakdown.baseSalaryPeriod,
            overtimeHours = breakdown.overtimeHours,
            overtimePay = breakdown.overtimePay,
            bonuses = breakdown.bonuses,
            grossPay = breakdown.grossPay,
            inssEmployee = breakdown.inssEmployee,
            taxableBasePeriod = breakdown.taxableBasePeriod,
            projectedAnnualIncome = breakdown.projectedAnnualIncome,
            irBracketLabel = breakdown.irBracketLabel,
            irPeriod = breakdown.irPeriod,
            otherDeductions = breakdown.otherDeductions,
            netPay = breakdown.netPay,
            status = "borrador",
        )

        val payrollId = withTimeout(DEFAULT_TIMEOUT_MS) { repository.savePayroll(record).getOrThrow() }
        withTimeout(DEFAULT_TIMEOUT_MS) { repository.markPayrollPaid(payrollId).getOrThrow() }

        // Updating a 'pagado' payroll must be rejected by terminal state lock
        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                firestore.collection("users").document(uid)
                    .collection("payrolls").document(payrollId)
                    .update(mapOf("netPay" to 99999.0, "updatedAt" to FieldValue.serverTimestamp()))
                    .await()
            }
            fail("Expected PERMISSION_DENIED when updating a paid payroll")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_payroll@test.com"
        const val BOB_EMAIL = "bob_payroll@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
