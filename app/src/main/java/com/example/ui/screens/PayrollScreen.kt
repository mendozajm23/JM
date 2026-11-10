package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EmployeeProfile
import com.example.data.model.PayrollRecord
import com.example.domain.PayrollBreakdown
import com.example.ui.theme.Amber100
import com.example.ui.theme.Amber900
import com.example.ui.theme.Emerald100
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Emerald600
import com.example.ui.theme.Emerald950
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.Navy800
import com.example.ui.theme.Navy900
import com.example.ui.util.FormatUtils
import com.example.ui.viewmodel.DataState
import com.example.ui.viewmodel.PayrollCalculatorInputs

@Composable
fun PayrollScreen(
    selectedEmployee: EmployeeProfile?,
    attendanceOvertimeHours: Double,
    inputs: PayrollCalculatorInputs,
    breakdown: PayrollBreakdown,
    payrollsState: DataState<List<PayrollRecord>>,
    isBusy: Boolean,
    onUpdateInputs: ((PayrollCalculatorInputs) -> PayrollCalculatorInputs) -> Unit,
    onSavePayroll: (markAsPaid: Boolean) -> Unit,
    onMarkPayrollPaid: (String) -> Unit,
    onDeletePayroll: (String) -> Unit,
    onExplainCurrentBreakdownWithAi: () -> Unit,
    onExplainSavedPayrollWithAi: (PayrollRecord) -> Unit,
) {
    val savedList = (payrollsState as? DataState.Success)?.data.orEmpty()
    val filteredPayrolls = if (selectedEmployee != null) {
        savedList.filter { it.employeeId == selectedEmployee.id }
    } else savedList

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Hero Net Pay Card
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
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = "NETO A RECIBIR (${breakdown.periodType.uppercase()})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF94A3B8),
                                )
                                Text(
                                    text = selectedEmployee?.fullName ?: "Colaborador",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                )
                            }
                            Surface(
                                color = Emerald500.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(50),
                            ) {
                                Text(
                                    text = "INSS 7% + IR Proyectado",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF6EE7B7),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }

                        Text(
                            text = FormatUtils.formatCurrency(breakdown.netPay),
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = Color(0xFF34D399),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            MiniPayrollStat("Ingreso Bruto", FormatUtils.formatCurrency(breakdown.grossPay))
                            MiniPayrollStat("INSS (7%)", "-${FormatUtils.formatCurrency(breakdown.inssEmployee)}")
                            MiniPayrollStat("IR Período", "-${FormatUtils.formatCurrency(breakdown.irPeriod)}")
                        }
                    }
                }
            }
        }

        // Configuration & Period Inputs Card
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
                    Text("Configuración del Período", style = MaterialTheme.typography.titleMedium)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = inputs.periodType == "quincenal",
                            onClick = {
                                onUpdateInputs {
                                    it.copy(
                                        periodType = "quincenal",
                                        periodLabel = FormatUtils.currentPeriodLabel("quincenal"),
                                    )
                                }
                            },
                            label = { Text("Quincenal (15 días)") },
                            modifier = Modifier.testTag("period_quincenal_chip"),
                        )
                        FilterChip(
                            selected = inputs.periodType == "mensual",
                            onClick = {
                                onUpdateInputs {
                                    it.copy(
                                        periodType = "mensual",
                                        periodLabel = FormatUtils.currentPeriodLabel("mensual"),
                                    )
                                }
                            },
                            label = { Text("Mensual (30 días)") },
                            modifier = Modifier.testTag("period_mensual_chip"),
                        )
                    }

                    OutlinedTextField(
                        value = inputs.periodLabel,
                        onValueChange = { v -> onUpdateInputs { it.copy(periodLabel = v) } },
                        label = { Text("Etiqueta del Período") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sincronizar Horas Extras de Marcaciones",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "Detectadas: ${FormatUtils.formatHours(attendanceOvertimeHours)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        Switch(
                            checked = inputs.useAttendanceOvertime,
                            onCheckedChange = { checked ->
                                onUpdateInputs { it.copy(useAttendanceOvertime = checked) }
                            },
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!inputs.useAttendanceOvertime) {
                            OutlinedTextField(
                                value = inputs.overtimeHoursInput,
                                onValueChange = { v -> onUpdateInputs { it.copy(overtimeHoursInput = v) } },
                                label = { Text("Horas Extras") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        OutlinedTextField(
                            value = inputs.bonusesInput,
                            onValueChange = { v -> onUpdateInputs { it.copy(bonusesInput = v) } },
                            label = { Text("Bonos (C$)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = inputs.otherDeductionsInput,
                            onValueChange = { v -> onUpdateInputs { it.copy(otherDeductionsInput = v) } },
                            label = { Text("Otras Ded. (C$)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Complete Statutory Breakdown Card
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
                    Text("Desglose Detallado de Nómina", style = MaterialTheme.typography.titleMedium)

                    BreakdownRow("Salario Base Mensual", FormatUtils.formatCurrency(breakdown.baseSalaryMonthly))
                    BreakdownRow(
                        "Salario Base del Período (${breakdown.periodType})",
                        FormatUtils.formatCurrency(breakdown.baseSalaryPeriod),
                    )
                    BreakdownRow(
                        "Horas Extras (${FormatUtils.formatHours(breakdown.overtimeHours)} × ${breakdown.overtimeMultiplier}x)",
                        FormatUtils.formatCurrency(breakdown.overtimePay),
                    )
                    if (breakdown.bonuses > 0.0) {
                        BreakdownRow("Bonificaciones / Comisiones", FormatUtils.formatCurrency(breakdown.bonuses))
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    BreakdownRow(
                        "Total Ingreso Bruto",
                        FormatUtils.formatCurrency(breakdown.grossPay),
                        bold = true,
                    )

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "DEDUCCIONES LEGALES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BreakdownRow(
                        "INSS Laboral (7.00%)",
                        "- ${FormatUtils.formatCurrency(breakdown.inssEmployee)}",
                        valueColor = MaterialTheme.colorScheme.error,
                    )
                    BreakdownRow(
                        "Base Imponible del Período",
                        FormatUtils.formatCurrency(breakdown.taxableBasePeriod),
                    )
                    BreakdownRow(
                        "Proyección Anual Imponible",
                        FormatUtils.formatCurrency(breakdown.projectedAnnualIncome),
                    )
                    Text(
                        text = breakdown.irBracketLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    BreakdownRow(
                        "IR Anual Proyectado",
                        FormatUtils.formatCurrency(breakdown.irAnnualProjected),
                    )
                    BreakdownRow(
                        "Retención IR del Período",
                        "- ${FormatUtils.formatCurrency(breakdown.irPeriod)}",
                        valueColor = MaterialTheme.colorScheme.error,
                    )
                    if (breakdown.otherDeductions > 0.0) {
                        BreakdownRow(
                            "Otras Deducciones",
                            "- ${FormatUtils.formatCurrency(breakdown.otherDeductions)}",
                            valueColor = MaterialTheme.colorScheme.error,
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    BreakdownRow(
                        "SALARIO NETO A RECIBIR",
                        FormatUtils.formatCurrency(breakdown.netPay),
                        bold = true,
                        valueColor = MaterialTheme.colorScheme.secondary,
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onSavePayroll(false) },
                            enabled = !isBusy && selectedEmployee != null,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("save_payroll_draft_button"),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Borrador")
                        }
                        Button(
                            onClick = { onSavePayroll(true) },
                            enabled = !isBusy && selectedEmployee != null,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("save_payroll_paid_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Emitir Pagado")
                        }
                    }

                    OutlinedButton(
                        onClick = onExplainCurrentBreakdownWithAi,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("explain_payroll_ai_button"),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Explicar desglose e IR con Asistente IA")
                    }
                }
            }
        }

        // Saved Payroll History
        item {
            Text(
                text = "Recibos Emitidos (${filteredPayrolls.size})",
                style = MaterialTheme.typography.titleLarge,
            )
        }

        when (payrollsState) {
            is DataState.Loading -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            is DataState.Error -> {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            text = payrollsState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            is DataState.Success -> {
                if (filteredPayrolls.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ReceiptLong,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp),
                                )
                                Text("Aún no hay recibos guardados", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Guarda un borrador o emite un pago para conservar el historial de nómina.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(filteredPayrolls, key = { it.id }) { receipt ->
                        SavedPayrollCard(
                            record = receipt,
                            onMarkPaid = { onMarkPayrollPaid(receipt.id) },
                            onExplainWithAi = { onExplainSavedPayrollWithAi(receipt) },
                            onDelete = { onDeletePayroll(receipt.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPayrollStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
        Text(
            text = value,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = Color.White,
        )
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    value: String,
    bold: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            fontSize = if (bold) 15.sp else 14.sp,
            color = valueColor,
        )
    }
}

@Composable
private fun SavedPayrollCard(
    record: PayrollRecord,
    onMarkPaid: () -> Unit,
    onExplainWithAi: () -> Unit,
    onDelete: () -> Unit,
) {
    val isPaid = record.status == "pagado"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                    Text(record.periodLabel, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${record.employeeName} • ${record.periodType.replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    color = if (isPaid) Emerald100 else Amber100,
                    shape = RoundedCornerShape(50),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (isPaid) {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(12.dp), tint = Emerald950)
                        }
                        Text(
                            text = if (isPaid) "Pagado" else "Borrador",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPaid) Emerald950 else Amber900,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Bruto: ${FormatUtils.formatCurrency(record.grossPay)}", style = MaterialTheme.typography.bodySmall)
                Text("INSS 7%: ${FormatUtils.formatCurrency(record.inssEmployee)}", style = MaterialTheme.typography.bodySmall)
                Text("IR: ${FormatUtils.formatCurrency(record.irPeriod)}", style = MaterialTheme.typography.bodySmall)
            }

            Text(
                text = "Neto: ${FormatUtils.formatCurrency(record.netPay)}",
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.secondary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isPaid) {
                    TextButton(onClick = onMarkPaid) {
                        Text("Marcar Pagado")
                    }
                }
                TextButton(onClick = onExplainWithAi) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Explicar")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Eliminar recibo")
                }
            }
        }
    }
}
