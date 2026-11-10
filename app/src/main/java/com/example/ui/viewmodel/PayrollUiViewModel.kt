package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AttendanceLog
import com.example.data.model.EmployeeProfile
import com.example.data.model.PayrollRecord
import com.example.data.repository.PayrollRepository
import com.example.domain.PayrollBreakdown
import com.example.domain.PayrollCalculator
import com.example.ui.util.FormatUtils
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DataState<out T> {
    data object Loading : DataState<Nothing>
    data class Success<T>(val data: T) : DataState<T>
    data class Error(val message: String) : DataState<Nothing>
}

enum class RoleMode(val label: String) {
    EMPLOYEE("Empleado"),
    ADMIN("Administrador"),
}

data class PayrollCalculatorInputs(
    val periodType: String = "quincenal",
    val periodLabel: String = FormatUtils.currentPeriodLabel("quincenal"),
    val overtimeHoursInput: String = "",
    val bonusesInput: String = "0",
    val otherDeductionsInput: String = "0",
    val useAttendanceOvertime: Boolean = true,
)

class PayrollUiViewModel(
    private val repository: PayrollRepository,
    private val currentUserId: String,
    private val userDisplayName: String?,
) : ViewModel() {

    val employeesState: StateFlow<DataState<List<EmployeeProfile>>> =
        repository.observeEmployees(currentUserId)
            .map<List<EmployeeProfile>, DataState<List<EmployeeProfile>>> { DataState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                Log.w(TAG, "Error observing employees", root)
                emit(DataState.Error(root.message ?: "No se pudo cargar el directorio de empleados."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = DataState.Loading,
            )

    val attendanceState: StateFlow<DataState<List<AttendanceLog>>> =
        repository.observeAttendanceLogs(currentUserId)
            .map<List<AttendanceLog>, DataState<List<AttendanceLog>>> { DataState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                Log.w(TAG, "Error observing attendance logs", root)
                emit(DataState.Error(root.message ?: "No se pudieron cargar las marcaciones."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = DataState.Loading,
            )

    val payrollsState: StateFlow<DataState<List<PayrollRecord>>> =
        repository.observePayrolls(currentUserId)
            .map<List<PayrollRecord>, DataState<List<PayrollRecord>>> { DataState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                Log.w(TAG, "Error observing payrolls", root)
                emit(DataState.Error(root.message ?: "No se pudo cargar el historial de nómina."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = DataState.Loading,
            )

    private val _selectedEmployeeId = MutableStateFlow("self")
    val selectedEmployeeId: StateFlow<String> = _selectedEmployeeId.asStateFlow()

    private val _roleMode = MutableStateFlow(RoleMode.EMPLOYEE)
    val roleMode: StateFlow<RoleMode> = _roleMode.asStateFlow()

    private val _nowMillis = MutableStateFlow(System.currentTimeMillis())
    val nowMillis: StateFlow<Long> = _nowMillis.asStateFlow()

    private val _calcInputs = MutableStateFlow(PayrollCalculatorInputs())
    val calcInputs: StateFlow<PayrollCalculatorInputs> = _calcInputs.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    val selectedEmployee: StateFlow<EmployeeProfile?> = combine(
        employeesState,
        _selectedEmployeeId,
    ) { state, selectedId ->
        val list = (state as? DataState.Success)?.data.orEmpty()
        list.firstOrNull { it.id == selectedId } ?: list.firstOrNull()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = null,
    )

    val selectedEmployeeOvertimeHours: StateFlow<Double> = combine(
        attendanceState,
        selectedEmployee,
    ) { attState, emp ->
        val logs = (attState as? DataState.Success)?.data.orEmpty()
        val empId = emp?.id ?: "self"
        PayrollCalculator.round2(
            logs.filter { it.employeeId == empId }.sumOf { it.overtimeHours }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = 0.0,
    )

    val liveBreakdown: StateFlow<PayrollBreakdown> = combine(
        selectedEmployee,
        selectedEmployeeOvertimeHours,
        _calcInputs,
    ) { emp, attendanceOt, inputs ->
        val salary = emp?.monthlyBaseSalary ?: 22000.0
        val dailyHours = emp?.dailyWorkHours ?: 8.0
        val otMult = emp?.overtimeMultiplier ?: 2.0
        val otHours = if (inputs.useAttendanceOvertime) {
            attendanceOt
        } else {
            inputs.overtimeHoursInput.toDoubleOrNull() ?: 0.0
        }
        val bonuses = inputs.bonusesInput.toDoubleOrNull() ?: 0.0
        val otherDed = inputs.otherDeductionsInput.toDoubleOrNull() ?: 0.0

        PayrollCalculator.calculatePayroll(
            monthlyBaseSalary = salary,
            periodType = inputs.periodType,
            overtimeHours = otHours,
            dailyWorkHours = dailyHours,
            overtimeMultiplier = otMult,
            bonuses = bonuses,
            otherDeductions = otherDed,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PayrollCalculator.calculatePayroll(22000.0, "quincenal"),
    )

    init {
        viewModelScope.launch {
            repository.ensureSelfProfile(userDisplayName)
                .onFailure { e ->
                    Log.e(TAG, "Failed to initialize self employee profile", e)
                }
        }
        viewModelScope.launch {
            while (true) {
                _nowMillis.value = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    fun selectEmployee(employeeId: String) {
        _selectedEmployeeId.value = employeeId
    }

    fun setRoleMode(mode: RoleMode) {
        _roleMode.value = mode
        if (mode == RoleMode.EMPLOYEE) {
            _selectedEmployeeId.value = "self"
        }
    }

    fun updateCalcInputs(transform: (PayrollCalculatorInputs) -> PayrollCalculatorInputs) {
        _calcInputs.value = transform(_calcInputs.value)
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun clockIn(modality: String, notes: String) {
        val emp = selectedEmployee.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.clockIn(emp, modality, notes)
                .onSuccess {
                    _statusBanner.value = "Entrada registrada a las ${FormatUtils.formatShortTime(System.currentTimeMillis())}"
                }
                .onFailure { e ->
                    _statusBanner.value = "Error al registrar entrada: ${e.localizedMessage ?: "intente nuevamente"}"
                }
            _isBusy.value = false
        }
    }

    fun clockOut(openLog: AttendanceLog) {
        val emp = selectedEmployee.value
        val dailyHours = emp?.dailyWorkHours ?: 8.0
        viewModelScope.launch {
            _isBusy.value = true
            repository.clockOut(openLog, dailyHours)
                .onSuccess {
                    _statusBanner.value = "Salida registrada a las ${FormatUtils.formatShortTime(System.currentTimeMillis())}"
                }
                .onFailure { e ->
                    _statusBanner.value = "Error al registrar salida: ${e.localizedMessage ?: "intente nuevamente"}"
                }
            _isBusy.value = false
        }
    }

    fun addManualAttendance(
        workDate: String,
        clockInMillis: Long,
        clockOutMillis: Long,
        modality: String,
        status: String,
        notes: String,
    ) {
        val emp = selectedEmployee.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            repository.createManualAttendance(
                employee = emp,
                workDate = workDate,
                clockInMillis = clockInMillis,
                clockOutMillis = clockOutMillis,
                modality = modality,
                status = status,
                notes = notes,
            ).onSuccess {
                _statusBanner.value = "Marcación guardada para $workDate."
            }.onFailure { e ->
                _statusBanner.value = "No se pudo guardar la marcación: ${e.localizedMessage}"
            }
            _isBusy.value = false
        }
    }

    fun deleteAttendance(logId: String) {
        viewModelScope.launch {
            repository.deleteAttendanceLog(logId)
                .onSuccess { _statusBanner.value = "Marcación eliminada." }
                .onFailure { e -> _statusBanner.value = "Error al eliminar: ${e.localizedMessage}" }
        }
    }

    fun saveEmployee(employee: EmployeeProfile) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.saveEmployee(employee)
                .onSuccess { id ->
                    _selectedEmployeeId.value = id
                    _statusBanner.value = "Perfil laboral de ${employee.fullName} actualizado."
                }
                .onFailure { e ->
                    _statusBanner.value = "No se pudo guardar el empleado: ${e.localizedMessage}"
                }
            _isBusy.value = false
        }
    }

    fun saveCurrentPayroll(markAsPaid: Boolean = false) {
        val emp = selectedEmployee.value ?: return
        val breakdown = liveBreakdown.value
        val inputs = _calcInputs.value
        val record = PayrollRecord(
            userId = currentUserId,
            employeeId = emp.id,
            employeeName = emp.fullName,
            periodType = breakdown.periodType,
            periodLabel = inputs.periodLabel.ifBlank { FormatUtils.currentPeriodLabel(breakdown.periodType) },
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
            status = if (markAsPaid) "pagado" else "borrador",
        )
        viewModelScope.launch {
            _isBusy.value = true
            repository.savePayroll(record)
                .onSuccess {
                    _statusBanner.value = if (markAsPaid) {
                        "Recibo de nómina emitido y marcado como pagado."
                    } else {
                        "Borrador de nómina guardado."
                    }
                }
                .onFailure { e ->
                    _statusBanner.value = "Error al emitir nómina: ${e.localizedMessage}"
                }
            _isBusy.value = false
        }
    }

    fun markPayrollPaid(payrollId: String) {
        viewModelScope.launch {
            repository.markPayrollPaid(payrollId)
                .onSuccess { _statusBanner.value = "Recibo marcado como Pagado (bloqueado)." }
                .onFailure { e -> _statusBanner.value = "Error al actualizar recibo: ${e.localizedMessage}" }
        }
    }

    fun deletePayroll(payrollId: String) {
        viewModelScope.launch {
            repository.deletePayroll(payrollId)
                .onSuccess { _statusBanner.value = "Recibo eliminado." }
                .onFailure { e -> _statusBanner.value = "Error al eliminar recibo: ${e.localizedMessage}" }
        }
    }

    private fun unwrapFirestoreError(error: Throwable): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        return firestoreError ?: error
    }

    private companion object {
        const val TAG = "PayrollUiVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
