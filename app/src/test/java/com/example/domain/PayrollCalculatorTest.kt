package com.example.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PayrollCalculatorTest {

    @Test
    fun calculatePayroll_exemptBracket_calculatesInss7PercentAndZeroIr() {
        // Monthly 16,000 -> Quincenal 8,000 -> INSS 7% = 560 -> Taxable = 7,440 * 24 = 178,560 (wait: 178k is > 100k!)
        // Let's test monthly 8,000: Bruto 8,000 -> INSS 560 -> Taxable 7,440 * 12 = 89,280 <= 100,000 -> IR = 0
        val res = PayrollCalculator.calculatePayroll(
            monthlyBaseSalary = 8000.0,
            periodType = "mensual",
        )
        assertEquals(8000.0, res.grossPay, 0.01)
        assertEquals(560.0, res.inssEmployee, 0.01)
        assertEquals(7440.0, res.taxableBasePeriod, 0.01)
        assertEquals(89280.0, res.projectedAnnualIncome, 0.01)
        assertEquals(0.0, res.irPeriod, 0.01)
        assertEquals(7440.0, res.netPay, 0.01)
    }

    @Test
    fun calculatePayroll_quincenalWithOvertime_calculatesInssAndProgressiveIr() {
        // Monthly 24,000 -> hourlyRate = 24000 / 240 = 100 -> 4h OT * 100 * 2.0 = 800
        // Quincenal base = 12,000 -> gross = 12,800 -> INSS 7% = 896 -> taxable = 11,904
        // Projected annual = 11,904 * 24 = 285,696 (Tramo 3: 15,000 + 20% * 85,696 = 32,139.20 / 24 = 1,339.13)
        val res = PayrollCalculator.calculatePayroll(
            monthlyBaseSalary = 24000.0,
            periodType = "quincenal",
            overtimeHours = 4.0,
        )
        assertEquals(12000.0, res.baseSalaryPeriod, 0.01)
        assertEquals(800.0, res.overtimePay, 0.01)
        assertEquals(12800.0, res.grossPay, 0.01)
        assertEquals(896.0, res.inssEmployee, 0.01)
        assertEquals(285696.0, res.projectedAnnualIncome, 0.01)
        assertEquals(1339.13, res.irPeriod, 0.01)
        assertEquals(10564.87, res.netPay, 0.01)
    }

    @Test
    fun calculateShiftHours_splitsRegularAndOvertime() {
        val start = 1_762_700_000_000L
        val end = start + (10.5 * 3_600_000.0).toLong()
        val shift = PayrollCalculator.calculateShiftHours(start, end, dailyWorkHours = 8.0)
        assertEquals(10.5, shift.totalHours, 0.01)
        assertEquals(8.0, shift.regularHours, 0.01)
        assertEquals(2.5, shift.overtimeHours, 0.01)
    }
}
