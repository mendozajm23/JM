package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AttendanceLog
import com.example.data.model.EmployeeProfile
import com.example.domain.PayrollCalculator
import com.example.ui.theme.Amber100
import com.example.ui.theme.Amber900
import com.example.ui.theme.Emerald100
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Emerald600
import com.example.ui.theme.Emerald950
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.Navy800
import com.example.ui.theme.Navy900
import com.example.ui.theme.Rose100
import com.example.ui.theme.Rose900
import com.example.ui.util.FormatUtils
import com.example.ui.viewmodel.DataState
import com.example.ui.viewmodel.RoleMode
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AttendanceScreen(
    employeesState: DataState<List<EmployeeProfile>>,
    attendanceState: DataState<List<AttendanceLog>>,
    selectedEmployee: EmployeeProfile?,
    roleMode: RoleMode,
    nowMillis: Long,
    isBusy: Boolean,
    onSelectEmployee: (String) -> Unit,
    onClockIn: (modality: String, notes: String) -> Unit,
    onClockOut: (AttendanceLog) -> Unit,
    onAddManualAttendance: (
        workDate: String,
        clockInMillis: Long,
        clockOutMillis: Long,
        modality: String,
        status: String,
        notes: String,
    ) -> Unit,
    onDeleteAttendance: (String) -> Unit,
    onAskAssistantAboutAttendance: (AttendanceLog) -> Unit,
) {
    var selectedModality by remember { mutableStateOf("presencial") }
    var shiftNotes by remember { mutableStateOf("") }
    var showManualDialog by remember { mutableStateOf(false) }

    val employees = (employeesState as? DataState.Success)?.data.orEmpty()
    val allLogs = (attendanceState as? DataState.Success)?.data.orEmpty()
    val currentEmpId = selectedEmployee?.id ?: "self"
    val employeeLogs = remember(allLogs, currentEmpId) {
        allLogs.filter { it.employeeId == currentEmpId }
    }
    val openLog = remember(employeeLogs) {
        employeeLogs.firstOrNull { it.clockOutMillis == 0L }
    }

    val totalRegular = remember(employeeLogs) {
        PayrollCalculator.round2(employeeLogs.sumOf { it.regularHours })
    }
    val totalOvertime = remember(employeeLogs) {
        PayrollCalculator.round2(employeeLogs.sumOf { it.overtimeHours })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Admin employee selector strip
        if (roleMode == RoleMode.ADMIN && employees.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Colaborador seleccionado (Modo Administrador)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(employees, key = { it.id }) { emp ->
                            FilterChip(
                                selected = emp.id == currentEmpId,
                                onClick = { onSelectEmployee(emp.id) },
                                label = { Text("${emp.fullName} (${emp.employeeCode})") },
                            )
                        }
                    }
                }
            }
        }

        // Hero Clock & Punch Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Navy900, Navy800)))
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = selectedEmployee?.fullName ?: "Cargando perfil...",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                )
                                Text(
                                    text = FormatUtils.formatFriendlyDate(FormatUtils.currentWorkDate()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8),
                                )
                            }
                            Surface(
                                color = if (openLog != null) Emerald500.copy(alpha = 0.22f)
                                else Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(50),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (openLog != null) Emerald500 else Color(0xFF94A3B8))
                                    )
                                    Text(
                                        text = if (openLog != null) "Jornada activa" else "Fuera de turno",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                    )
                                }
                            }
                        }

                        // Live digital clock
                        Text(
                            text = FormatUtils.formatTime(nowMillis),
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 38.sp,
                            color = Color.White,
                        )

                        AnimatedVisibility(visible = openLog != null) {
                            if (openLog != null) {
                                val elapsedMs = (nowMillis - openLog.clockInMillis).coerceAtLeast(0L)
                                val hrs = elapsedMs / 3_600_000L
                                val mins = (elapsedMs % 3_600_000L) / 60_000L
                                val secs = (elapsedMs % 60_000L) / 1000L
                                Surface(
                                    color = Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "Entrada: ${FormatUtils.formatTime(openLog.clockInMillis)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFFCBD5E1),
                                        )
                                        Text(
                                            text = String.format(Locale.US, "Transcurrido %02d:%02d:%02d", hrs, mins, secs),
                                            fontFamily = JetBrainsMonoFamily,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF6EE7B7),
                                        )
                                    }
                                }
                            }
                        }

                        if (openLog == null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("presencial" to "Presencial", "remoto" to "Remoto", "campo" to "Campo").forEach { (key, label) ->
                                    FilterChip(
                                        selected = selectedModality == key,
                                        onClick = { selectedModality = key },
                                        label = { Text(label) },
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = shiftNotes,
                                onValueChange = { shiftNotes = it },
                                placeholder = { Text("Nota opcional (ej. Turno oficina central)", color = Color(0xFF94A3B8)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                            )

                            Button(
                                onClick = {
                                    onClockIn(selectedModality, shiftNotes)
                                    shiftNotes = ""
                                },
                                enabled = !isBusy && selectedEmployee != null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("clock_in_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Emerald600,
                                    contentColor = Color.White,
                                ),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Marcar Entrada", style = MaterialTheme.typography.labelLarge)
                            }
                        } else {
                            Button(
                                onClick = { onClockOut(openLog) },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("clock_out_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEF4444),
                                    contentColor = Color.White,
                                ),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Marcar Salida", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }

        // KPI Summary Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricStatCard(
                    label = "Horas Ordinarias",
                    value = FormatUtils.formatHours(totalRegular),
                    modifier = Modifier.weight(1f),
                )
                MetricStatCard(
                    label = "Horas Extras",
                    value = FormatUtils.formatHours(totalOvertime),
                    accentColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
                MetricStatCard(
                    label = "Registros",
                    value = "${employeeLogs.size}",
                    modifier = Modifier.weight(0.8f),
                )
            }
        }

        // Ledger Header + Manual Record button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Historial de Marcaciones",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                OutlinedButton(
                    onClick = { showManualDialog = true },
                    enabled = selectedEmployee != null,
                    modifier = Modifier.testTag("add_manual_attendance_button"),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Registrar")
                }
            }
        }

        when (attendanceState) {
            is DataState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
            is DataState.Error -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            text = attendanceState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            is DataState.Success -> {
                if (employeeLogs.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(40.dp),
                                )
                                Text(
                                    text = "Sin marcaciones registradas",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "Usa el botón Marcar Entrada o Registrar para crear tu primer registro de asistencia.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(employeeLogs, key = { it.id }) { log ->
                        AttendanceLogCard(
                            log = log,
                            onAskAi = { onAskAssistantAboutAttendance(log) },
                            onDelete = { onDeleteAttendance(log.id) },
                        )
                    }
                }
            }
        }
    }

    if (showManualDialog) {
        ManualAttendanceDialog(
            onDismiss = { showManualDialog = false },
            onConfirm = { workDate, inMillis, outMillis, modality, status, notes ->
                showManualDialog = false
                onAddManualAttendance(workDate, inMillis, outMillis, modality, status, notes)
            },
        )
    }
}

@Composable
private fun MetricStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = accentColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AttendanceLogCard(
    log: AttendanceLog,
    onAskAi: () -> Unit,
    onDelete: () -> Unit,
) {
    val (badgeBg, badgeFg, badgeLabel) = when (log.status) {
        "completado" -> Triple(Emerald100, Emerald950, "Completado")
        "tardanza" -> Triple(Amber100, Amber900, "Tardanza")
        "incompleto" -> Triple(Rose100, Rose900, "Incompleto")
        else -> Triple(Color(0xFFDBEAFE), Color(0xFF1E3A8A), "Abierto")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = FormatUtils.formatFriendlyDate(log.workDate),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "${log.modality.replaceFirstChar { it.uppercase() }} • ${log.workDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(color = badgeBg, shape = RoundedCornerShape(50)) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("ENTRADA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = FormatUtils.formatTime(log.clockInMillis),
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
                Column {
                    Text("SALIDA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (log.clockOutMillis > 0L) FormatUtils.formatTime(log.clockOutMillis) else "Pendiente",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("ORDINARIAS / EXTRAS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${FormatUtils.formatHours(log.regularHours)} / +${FormatUtils.formatHours(log.overtimeHours)}",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (log.overtimeHours > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (log.notes.isNotBlank()) {
                Text(
                    text = "Nota: ${log.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onAskAi) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Interpretar con IA")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Eliminar marcación",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualAttendanceDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        workDate: String,
        clockInMillis: Long,
        clockOutMillis: Long,
        modality: String,
        status: String,
        notes: String,
    ) -> Unit,
) {
    var workDate by remember { mutableStateOf(FormatUtils.currentWorkDate()) }
    var startHour by remember { mutableStateOf("08:00") }
    var endHour by remember { mutableStateOf("17:30") }
    var modality by remember { mutableStateOf("presencial") }
    var status by remember { mutableStateOf("completado") }
    var notes by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Marcación Manual") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = workDate,
                    onValueChange = { workDate = it },
                    label = { Text("Fecha (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startHour,
                        onValueChange = { startHour = it },
                        label = { Text("Entrada (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endHour,
                        onValueChange = { endHour = it },
                        label = { Text("Salida (HH:mm)") },
                        placeholder = { Text("Vacío si abierta") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("completado", "tardanza", "incompleto").forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("presencial", "remoto", "campo").forEach { mod ->
                        FilterChip(
                            selected = modality == mod,
                            onClick = { modality = mod },
                            label = { Text(mod.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observaciones") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorMsg != null) {
                    Text(
                        text = errorMsg.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }
                    try {
                        val inDate = parser.parse("$workDate $startHour")
                            ?: throw IllegalArgumentException("Formato inválido")
                        val outMillis = if (endHour.isBlank()) {
                            0L
                        } else {
                            val outDate = parser.parse("$workDate $endHour")
                                ?: throw IllegalArgumentException("Hora salida inválida")
                            if (outDate.time < inDate.time) {
                                errorMsg = "La hora de salida debe ser posterior a la entrada."
                                return@Button
                            }
                            outDate.time
                        }
                        val resolvedStatus = if (outMillis == 0L && status == "completado") "incompleto" else status
                        onConfirm(workDate, inDate.time, outMillis, modality, resolvedStatus, notes)
                    } catch (_: Exception) {
                        errorMsg = "Verifica el formato Fecha (YYYY-MM-DD) y Hora (HH:mm)."
                    }
                },
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}
