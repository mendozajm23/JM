package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AttendanceLog
import com.example.data.model.EmployeeProfile
import com.example.data.model.PayrollRecord
import com.example.domain.PayrollBreakdown
import com.example.ui.util.FormatUtils
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class ChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
)

sealed interface AssistantStatus {
    data object Idle : AssistantStatus
    data object Generating : AssistantStatus
    data class Error(val message: String) : AssistantStatus
}

class AssistantViewModel : ViewModel() {

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(SYSTEM_INSTRUCTION)
            },
        )
    }

    private val _messages = MutableStateFlow(
        listOf(
            ChatMessage(
                id = "welcome",
                isUser = false,
                text = "Bienvenido. Soy el Asistente de Control de Asistencia y Nómina. " +
                    "Puedo interpretar sus registros de marcaciones (Entrada/Salida), explicar en detalle " +
                    "su pago quincenal o mensual (Salario Base, Horas Extras, INSS Laboral 7% e IR según " +
                    "tabla proyectada anual) y responder consultas laborales con transparencia. ¿En qué le puedo apoyar hoy?",
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _status = MutableStateFlow<AssistantStatus>(AssistantStatus.Idle)
    val status: StateFlow<AssistantStatus> = _status.asStateFlow()

    fun sendMessage(
        userQuestion: String,
        roleMode: RoleMode,
        selectedEmployee: EmployeeProfile?,
        attendanceLogs: List<AttendanceLog>,
        currentBreakdown: PayrollBreakdown,
        savedPayrolls: List<PayrollRecord>,
        allEmployees: List<EmployeeProfile>,
    ) {
        val trimmed = userQuestion.trim()
        if (trimmed.isEmpty() || _status.value is AssistantStatus.Generating) return

        val userMsgId = "u_${System.currentTimeMillis()}"
        val modelMsgId = "m_${System.currentTimeMillis() + 1}"

        _messages.update { list ->
            list + ChatMessage(id = userMsgId, isUser = true, text = trimmed) +
                ChatMessage(id = modelMsgId, isUser = false, text = "")
        }
        _status.value = AssistantStatus.Generating

        val contextBlock = buildLiveContextBlock(
            roleMode = roleMode,
            selectedEmployee = selectedEmployee,
            attendanceLogs = attendanceLogs,
            currentBreakdown = currentBreakdown,
            savedPayrolls = savedPayrolls,
            allEmployees = allEmployees,
        )

        val fullPrompt = """
            $contextBlock

            CONSULTA DEL USUARIO (${roleMode.label}):
            $trimmed
        """.trimIndent()

        viewModelScope.launch {
            val accumulated = StringBuilder()
            try {
                withTimeout(60_000L) {
                    model.generateContentStream(fullPrompt).collect { chunk ->
                        val chunkText = chunk.text.orEmpty()
                        accumulated.append(chunkText)
                        updateAssistantMessage(modelMsgId, accumulated.toString(), isError = false)
                    }
                }
                if (accumulated.isNotBlank()) {
                    _status.value = AssistantStatus.Idle
                } else {
                    val emptyMsg = "El asistente no devolvió contenido. Por favor intente reformular su consulta."
                    updateAssistantMessage(modelMsgId, emptyMsg, isError = true)
                    _status.value = AssistantStatus.Error(emptyMsg)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI Assistant streaming timed out", e)
                val timeoutMsg = "La consulta excedió el tiempo de espera de 60 segundos. Por favor intente nuevamente."
                updateAssistantMessage(modelMsgId, timeoutMsg, isError = true)
                _status.value = AssistantStatus.Error(timeoutMsg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI generation failed", e)
                val errMsg = e.localizedMessage ?: "No se pudo completar la consulta con el asistente."
                updateAssistantMessage(
                    modelMsgId,
                    "Error al consultar el Asistente IA: $errMsg",
                    isError = true,
                )
                _status.value = AssistantStatus.Error(errMsg)
            }
        }
    }

    fun clearConversation() {
        _status.value = AssistantStatus.Idle
        _messages.value = listOf(
            ChatMessage(
                id = "welcome_${System.currentTimeMillis()}",
                isUser = false,
                text = "Conversación reiniciada. Estoy listo para analizar sus marcaciones, desglosar su nómina (INSS 7% e IR proyectado) o resolver dudas laborales.",
            )
        )
    }

    private fun updateAssistantMessage(id: String, text: String, isError: Boolean) {
        _messages.update { list ->
            list.map { msg ->
                if (msg.id == id) msg.copy(text = text, isError = isError) else msg
            }
        }
    }

    private fun buildLiveContextBlock(
        roleMode: RoleMode,
        selectedEmployee: EmployeeProfile?,
        attendanceLogs: List<AttendanceLog>,
        currentBreakdown: PayrollBreakdown,
        savedPayrolls: List<PayrollRecord>,
        allEmployees: List<EmployeeProfile>,
    ): String {
        val empName = selectedEmployee?.fullName ?: "Colaborador Principal"
        val empCode = selectedEmployee?.employeeCode ?: "EMP-001"
        val empRole = selectedEmployee?.roleTitle ?: "Especialista"
        val empLogs = attendanceLogs.filter {
            selectedEmployee == null || it.employeeId == selectedEmployee.id
        }.take(10)

        val logsSummary = if (empLogs.isEmpty()) {
            "Sin marcaciones registradas para este colaborador."
        } else {
            empLogs.joinToString("\n") { log ->
                val inTime = FormatUtils.formatDateTime(log.clockInMillis)
                val outTime = if (log.clockOutMillis > 0L) FormatUtils.formatDateTime(log.clockOutMillis) else "Sin salida (Turno abierto)"
                "- Fecha: ${log.workDate} | Entrada: $inTime | Salida: $outTime | Ordinarias: ${FormatUtils.formatHours(log.regularHours)} | Extras: ${FormatUtils.formatHours(log.overtimeHours)} | Modalidad: ${log.modality} | Estado: ${log.status} | Nota: ${log.notes.ifBlank { "N/A" }}"
            }
        }

        val recentPayrollsSummary = if (savedPayrolls.isEmpty()) {
            "Sin recibos emitidos guardados aún."
        } else {
            savedPayrolls.take(5).joinToString("\n") { p ->
                "- ${p.employeeName} | ${p.periodLabel} (${p.periodType}) | Bruto: ${FormatUtils.formatCurrency(p.grossPay)} | INSS 7%: ${FormatUtils.formatCurrency(p.inssEmployee)} | IR: ${FormatUtils.formatCurrency(p.irPeriod)} | Neto: ${FormatUtils.formatCurrency(p.netPay)} | Estado: ${p.status}"
            }
        }

        val adminTeamSummary = if (roleMode == RoleMode.ADMIN) {
            "Equipo registrado (${allEmployees.size} colaboradores):\n" +
                allEmployees.joinToString("\n") { e ->
                    "- ${e.fullName} (${e.employeeCode}, ${e.roleTitle}) | Salario Mensual: ${FormatUtils.formatCurrency(e.monthlyBaseSalary)} | Frecuencia: ${e.payFrequency}"
                }
        } else ""

        return """
            DATOS REALES DEL SISTEMA EN TIEMPO REAL:
            Rol activo: ${roleMode.label}
            Colaborador seleccionado: $empName ($empCode - $empRole)
            Salario Base Mensual configurado: ${FormatUtils.formatCurrency(currentBreakdown.baseSalaryMonthly)}

            DESGLOSE VIGENTE DEL PERÍODO (${currentBreakdown.periodType.uppercase()}):
            - Salario Base del Período: ${FormatUtils.formatCurrency(currentBreakdown.baseSalaryPeriod)}
            - Horas Extras: ${FormatUtils.formatHours(currentBreakdown.overtimeHours)} (Tarifa/h ${FormatUtils.formatCurrency(currentBreakdown.hourlyRate)} x ${currentBreakdown.overtimeMultiplier}x = ${FormatUtils.formatCurrency(currentBreakdown.overtimePay)})
            - Bonificaciones: ${FormatUtils.formatCurrency(currentBreakdown.bonuses)}
            - Total Ingreso Bruto: ${FormatUtils.formatCurrency(currentBreakdown.grossPay)}
            - INSS Laboral (7%): ${FormatUtils.formatCurrency(currentBreakdown.inssEmployee)}
            - Base Imponible del Período: ${FormatUtils.formatCurrency(currentBreakdown.taxableBasePeriod)}
            - Proyección Anual Imponible: ${FormatUtils.formatCurrency(currentBreakdown.projectedAnnualIncome)}
            - Tramo IR aplicado: ${currentBreakdown.irBracketLabel}
            - IR Anual Proyectado: ${FormatUtils.formatCurrency(currentBreakdown.irAnnualProjected)}
            - IR Retenido del Período: ${FormatUtils.formatCurrency(currentBreakdown.irPeriod)}
            - Otras Deducciones: ${FormatUtils.formatCurrency(currentBreakdown.otherDeductions)}
            - Salario Neto a Recibir: ${FormatUtils.formatCurrency(currentBreakdown.netPay)}

            ÚLTIMAS MARCACIONES (ENTRADA/SALIDA):
            $logsSummary

            HISTORIAL RECIENTE DE RECIBOS:
            $recentPayrollsSummary

            $adminTeamSummary
        """.trimIndent()
    }

    private companion object {
        const val TAG = "AssistantVM"
        const val SYSTEM_INSTRUCTION =
            "Eres el Asistente de Control de Asistencia y Nómina de la aplicación. " +
                "Tu objetivo es ayudar a empleados y administradores a: " +
                "1. Interpretar registros de marcaciones (Entrada/Salida). " +
                "2. Explicar el desglose de su pago quincenal/mensual (Salario Base, Horas Extras, INSS 7%, e Impuesto sobre la Renta IR). " +
                "3. Responder dudas laborales de forma clara, profesional y transparente. " +
                "Reglas: " +
                "- Sé preciso con las fechas y horas. " +
                "- Desglosa siempre las deducciones: INSS Laboral (7%) e IR (según tabla proyectada anual). " +
                "- Mantén un tono cordial y corporativo."
    }
}
