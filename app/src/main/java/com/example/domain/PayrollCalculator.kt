package com.example.domain

import kotlin.math.roundToLong

data class PayrollBreakdown(
    val periodType: String,
    val baseSalaryMonthly: Double,
    val baseSalaryPeriod: Double,
    val hourlyRate: Double,
    val overtimeHours: Double,
    val overtimeMultiplier: Double,
    val overtimePay: Double,
    val bonuses: Double,
    val grossPay: Double,
    val inssEmployee: Double, // 7% INSS Laboral
    val taxableBasePeriod: Double, // Gross - INSS
    val projectedAnnualIncome: Double,
    val irBracketLabel: String,
    val irBaseTaxAnnual: Double,
    val irExcessAnnual: Double,
    val irRatePercent: Int,
    val irAnnualProjected: Double,
    val irPeriod: Double,
    val otherDeductions: Double,
    val netPay: Double,
)

data class ShiftHoursResult(
    val totalHours: Double,
    val regularHours: Double,
    val overtimeHours: Double,
)

object PayrollCalculator {

    private const val INSS_LABORAL_RATE = 0.07

    fun calculateShiftHours(
        clockInMillis: Long,
        clockOutMillis: Long,
        dailyWorkHours: Double = 8.0,
    ): ShiftHoursResult {
        if (clockOutMillis <= clockInMillis || clockInMillis <= 0L) {
            return ShiftHoursResult(0.0, 0.0, 0.0)
        }
        val totalHours = round2(((clockOutMillis - clockInMillis).toDouble() / 3_600_000.0).coerceAtMost(24.0))
        val regular = round2(minOf(totalHours, dailyWorkHours))
        val overtime = round2((totalHours - dailyWorkHours).coerceAtLeast(0.0))
        return ShiftHoursResult(totalHours, regular, overtime)
    }

    /**
     * Calcula la nómina quincenal o mensual conforme a la normativa laboral y tributaria:
     * - Salario Base del período (Mensual / 2 para quincenal, o Mensual completo)
     * - Horas Extras = Horas * (Salario Mensual / (30 * jornadaDiaria)) * factor (2.0x por defecto)
     * - INSS Laboral = 7% sobre Total Ingresos Brutos
     * - Base Imponible = Ingreso Bruto - INSS Laboral
     * - Proyección Anual = Base Imponible * (24 si quincenal, 12 si mensual)
     * - Tabla Progresiva Anual IR (Art. 23 LCT):
     *   * Hasta C$ 100,000.00 -> Exento (0%)
     *   * C$ 100,000.01 a C$ 200,000.00 -> 15% sobre exceso de C$ 100,000
     *   * C$ 200,000.01 a C$ 350,000.00 -> C$ 15,000 + 20% sobre exceso de C$ 200,000
     *   * C$ 350,000.01 a C$ 500,000.00 -> C$ 45,000 + 25% sobre exceso de C$ 350,000
     *   * Más de C$ 500,000.00 -> C$ 82,500 + 30% sobre exceso de C$ 500,000
     */
    fun calculatePayroll(
        monthlyBaseSalary: Double,
        periodType: String,
        overtimeHours: Double = 0.0,
        dailyWorkHours: Double = 8.0,
        overtimeMultiplier: Double = 2.0,
        bonuses: Double = 0.0,
        otherDeductions: Double = 0.0,
    ): PayrollBreakdown {
        val safeMonthly = monthlyBaseSalary.coerceAtLeast(0.0)
        val safeDaily = dailyWorkHours.coerceIn(1.0, 24.0)
        val periodsPerYear = if (periodType == "quincenal") 24.0 else 12.0
        val baseSalaryPeriod = round2(if (periodType == "quincenal") safeMonthly / 2.0 else safeMonthly)

        val hourlyRate = round2(safeMonthly / (30.0 * safeDaily))
        val safeOvertime = overtimeHours.coerceAtLeast(0.0)
        val overtimePay = round2(safeOvertime * hourlyRate * overtimeMultiplier)
        val safeBonuses = bonuses.coerceAtLeast(0.0)
        val grossPay = round2(baseSalaryPeriod + overtimePay + safeBonuses)

        val inssEmployee = round2(grossPay * INSS_LABORAL_RATE)
        val taxableBasePeriod = round2((grossPay - inssEmployee).coerceAtLeast(0.0))
        val projectedAnnual = round2(taxableBasePeriod * periodsPerYear)

        val irRatePercent: Int
        val irBaseTax: Double
        val irExcessThreshold: Double
        val bracketLabel: String

        when {
            projectedAnnual <= 100_000.0 -> {
                irRatePercent = 0
                irBaseTax = 0.0
                irExcessThreshold = 0.0
                bracketLabel = "Tramo 1: Exento (hasta C$ 100,000 anuales)"
            }
            projectedAnnual <= 200_000.0 -> {
                irRatePercent = 15
                irBaseTax = 0.0
                irExcessThreshold = 100_000.0
                bracketLabel = "Tramo 2: 15% sobre exceso de C$ 100,000"
            }
            projectedAnnual <= 350_000.0 -> {
                irRatePercent = 20
                irBaseTax = 15_000.0
                irExcessThreshold = 200_000.0
                bracketLabel = "Tramo 3: 20% sobre exceso de C$ 200,000"
            }
            projectedAnnual <= 500_000.0 -> {
                irRatePercent = 25
                irBaseTax = 45_000.0
                irExcessThreshold = 350_000.0
                bracketLabel = "Tramo 4: 25% sobre exceso de C$ 350,000"
            }
            else -> {
                irRatePercent = 30
                irBaseTax = 82_500.0
                irExcessThreshold = 500_000.0
                bracketLabel = "Tramo 5: 30% sobre exceso de C$ 500,000"
            }
        }

        val irExcessAnnual = round2((projectedAnnual - irExcessThreshold).coerceAtLeast(0.0))
        val irAnnual = round2(irBaseTax + irExcessAnnual * (irRatePercent / 100.0))
        val irPeriod = round2(irAnnual / periodsPerYear)
        val safeOther = otherDeductions.coerceAtLeast(0.0)
        val netPay = round2((grossPay - inssEmployee - irPeriod - safeOther).coerceAtLeast(0.0))

        return PayrollBreakdown(
            periodType = periodType,
            baseSalaryMonthly = round2(safeMonthly),
            baseSalaryPeriod = baseSalaryPeriod,
            hourlyRate = hourlyRate,
            overtimeHours = round2(safeOvertime),
            overtimeMultiplier = overtimeMultiplier,
            overtimePay = overtimePay,
            bonuses = round2(safeBonuses),
            grossPay = grossPay,
            inssEmployee = inssEmployee,
            taxableBasePeriod = taxableBasePeriod,
            projectedAnnualIncome = projectedAnnual,
            irBracketLabel = bracketLabel,
            irBaseTaxAnnual = irBaseTax,
            irExcessAnnual = irExcessAnnual,
            irRatePercent = irRatePercent,
            irAnnualProjected = irAnnual,
            irPeriod = irPeriod,
            otherDeductions = round2(safeOther),
            netPay = netPay,
        )
    }

    fun round2(value: Double): Double = (value * 100.0).roundToLong() / 100.0
}
