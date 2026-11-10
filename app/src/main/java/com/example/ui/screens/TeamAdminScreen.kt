package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.EmployeeProfile
import com.example.ui.theme.Emerald100
import com.example.ui.theme.Emerald950
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.util.FormatUtils
import com.example.ui.viewmodel.DataState
import com.example.ui.viewmodel.RoleMode

@Composable
fun TeamAdminScreen(
    userEmail: String?,
    roleMode: RoleMode,
    employeesState: DataState<List<EmployeeProfile>>,
    selectedEmployee: EmployeeProfile?,
    onRoleModeChanged: (RoleMode) -> Unit,
    onSelectEmployee: (String) -> Unit,
    onSaveEmployee: (EmployeeProfile) -> Unit,
    onSignOut: () -> Unit,
) {
    val employees = (employeesState as? DataState.Success)?.data.orEmpty()
    var editingEmployee by remember { mutableStateOf<EmployeeProfile?>(null) }
    var creatingNew by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Role & Account Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Perfil y Modo de Operación", style = MaterialTheme.typography.titleMedium)
                    if (!userEmail.isNullOrBlank()) {
                        Text(
                            text = "Sesión corporativa: $userEmail",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = roleMode == RoleMode.EMPLOYEE,
                            onClick = { onRoleModeChanged(RoleMode.EMPLOYEE) },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            label = { Text("Vista Empleado") },
                            modifier = Modifier.testTag("role_employee_chip"),
                        )
                        FilterChip(
                            selected = roleMode == RoleMode.ADMIN,
                            onClick = { onRoleModeChanged(RoleMode.ADMIN) },
                            leadingIcon = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            label = { Text("Vista Administrador") },
                            modifier = Modifier.testTag("role_admin_chip"),
                        )
                    }

                    Text(
                        text = if (roleMode == RoleMode.ADMIN) {
                            "Modo Administrador activo: puedes crear colaboradores, gestionar salarios y emitir nóminas del equipo."
                        } else {
                            "Modo Empleado activo: visualiza y edita tu perfil laboral, marcaciones y recibos de pago."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Team / Employee Roster Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (roleMode == RoleMode.ADMIN) "Directorio de Colaboradores" else "Mi Perfil Salarial",
                    style = MaterialTheme.typography.titleLarge,
                )
                if (roleMode == RoleMode.ADMIN) {
                    OutlinedButton(
                        onClick = { creatingNew = true },
                        modifier = Modifier.testTag("add_employee_button"),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Agregar")
                    }
                }
            }
        }

        val visibleEmployees = if (roleMode == RoleMode.ADMIN) {
            employees
        } else {
            employees.filter { it.isSelf }.ifEmpty { employees.take(1) }
        }

        items(visibleEmployees, key = { it.id }) { emp ->
            val isSelected = selectedEmployee?.id == emp.id
            Card(
                onClick = { onSelectEmployee(emp.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(emp.fullName, style = MaterialTheme.typography.titleMedium)
                                if (emp.isSelf) {
                                    Surface(color = Emerald100, shape = RoundedCornerShape(50)) {
                                        Text(
                                            text = "Principal",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Emerald950,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "${emp.employeeCode} • ${emp.roleTitle} (${emp.department})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { editingEmployee = emp }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar perfil salarial")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("SALARIO MENSUAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = FormatUtils.formatCurrency(emp.monthlyBaseSalary),
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column {
                            Text("FRECUENCIA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(emp.payFrequency.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("JORNADA / EXTRA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${emp.dailyWorkHours.toInt()}h / ${emp.overtimeMultiplier}x", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Statutory Tax Reference Table Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Referencia Legal: INSS Laboral e IR Anual", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "• INSS Laboral: 7.00% sobre el total de ingresos gravables (Salario Base + Horas Extras + Bonos).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Tabla Progresiva Anual IR (sobre Base Imponible Anualizada):", style = MaterialTheme.typography.labelMedium)
                    TaxBracketRow("C$ 0.01 – C$ 100,000.00", "Exento (0%)")
                    TaxBracketRow("C$ 100,000.01 – C$ 200,000.00", "15% s/ exceso C$ 100k")
                    TaxBracketRow("C$ 200,000.01 – C$ 350,000.00", "C$ 15,000 + 20% s/ exc.")
                    TaxBracketRow("C$ 350,000.01 – C$ 500,000.00", "C$ 45,000 + 25% s/ exc.")
                    TaxBracketRow("C$ 500,000.01 a más", "C$ 82,500 + 30% s/ exc.")
                }
            }
        }

        // Sign Out
        item {
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sign_out_button"),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cerrar sesión")
            }
        }
    }

    if (editingEmployee != null || creatingNew) {
        EmployeeEditorDialog(
            initial = editingEmployee,
            onDismiss = {
                editingEmployee = null
                creatingNew = false
            },
            onSave = { updated ->
                editingEmployee = null
                creatingNew = false
                onSaveEmployee(updated)
            },
        )
    }
}

@Composable
private fun TaxBracketRow(range: String, rate: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(range, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(rate, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmployeeEditorDialog(
    initial: EmployeeProfile?,
    onDismiss: () -> Unit,
    onSave: (EmployeeProfile) -> Unit,
) {
    var fullName by remember { mutableStateOf(initial?.fullName ?: "") }
    var roleTitle by remember { mutableStateOf(initial?.roleTitle ?: "Analista") }
    var department by remember { mutableStateOf(initial?.department ?: "Operaciones") }
    var code by remember { mutableStateOf(initial?.employeeCode ?: "EMP-${(100..999).random()}") }
    var salaryStr by remember { mutableStateOf((initial?.monthlyBaseSalary ?: 18000.0).toInt().toString()) }
    var frequency by remember { mutableStateOf(initial?.payFrequency ?: "quincenal") }
    var dailyHoursStr by remember { mutableStateOf((initial?.dailyWorkHours ?: 8.0).toInt().toString()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo Colaborador" else "Editar Perfil Laboral") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Nombre Completo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Código") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f),
                    )
                    OutlinedTextField(
                        value = department,
                        onValueChange = { department = it },
                        label = { Text("Área") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f),
                    )
                }
                OutlinedTextField(
                    value = roleTitle,
                    onValueChange = { roleTitle = it },
                    label = { Text("Cargo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = salaryStr,
                        onValueChange = { salaryStr = it },
                        label = { Text("Salario Mensual (C$)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1.3f),
                    )
                    OutlinedTextField(
                        value = dailyHoursStr,
                        onValueChange = { dailyHoursStr = it },
                        label = { Text("Jornada (h)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(0.7f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = frequency == "quincenal",
                        onClick = { frequency = "quincenal" },
                        label = { Text("Quincenal") },
                    )
                    FilterChip(
                        selected = frequency == "mensual",
                        onClick = { frequency = "mensual" },
                        label = { Text("Mensual") },
                    )
                }
                if (errorMsg != null) {
                    Text(errorMsg.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val salary = salaryStr.toDoubleOrNull()
                    val daily = dailyHoursStr.toDoubleOrNull()
                    if (fullName.isBlank() || roleTitle.isBlank() || code.isBlank() || salary == null || salary < 0 || daily == null || daily !in 1.0..24.0) {
                        errorMsg = "Completa los campos con valores salariales y de jornada válidos."
                        return@Button
                    }
                    val result = (initial ?: EmployeeProfile()).copy(
                        fullName = fullName.trim(),
                        roleTitle = roleTitle.trim(),
                        department = department.ifBlank { "General" }.trim(),
                        employeeCode = code.trim(),
                        monthlyBaseSalary = salary,
                        payFrequency = frequency,
                        dailyWorkHours = daily,
                        overtimeMultiplier = initial?.overtimeMultiplier ?: 2.0,
                        isSelf = initial?.isSelf ?: false,
                        active = true,
                    )
                    onSave(result)
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
