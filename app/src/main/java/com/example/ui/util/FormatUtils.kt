package com.example.ui.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {
    private val esLocale = Locale.Builder().setLanguage("es").setRegion("NI").build()

    fun formatCurrency(amount: Double): String {
        val nf = NumberFormat.getNumberInstance(esLocale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        return "C$ ${nf.format(amount)}"
    }

    fun formatHours(hours: Double): String {
        return String.format(Locale.US, "%.2f h", hours)
    }

    fun formatTime(millis: Long): String {
        if (millis <= 0L) return "--:--"
        val sdf = SimpleDateFormat("HH:mm:ss", esLocale)
        return sdf.format(Date(millis))
    }

    fun formatShortTime(millis: Long): String {
        if (millis <= 0L) return "Pendiente"
        val sdf = SimpleDateFormat("HH:mm", esLocale)
        return sdf.format(Date(millis))
    }

    fun formatDateTime(millis: Long): String {
        if (millis <= 0L) return "Sin registro"
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", esLocale)
        return sdf.format(Date(millis))
    }

    fun formatFriendlyDate(workDate: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = parser.parse(workDate) ?: return workDate
            val formatter = SimpleDateFormat("EEE, dd MMM yyyy", esLocale)
            formatter.format(date).replaceFirstChar { it.uppercase(esLocale) }
        } catch (_: Exception) {
            workDate
        }
    }

    fun currentWorkDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun currentPeriodLabel(periodType: String): String {
        val cal = java.util.Calendar.getInstance()
        val monthName = SimpleDateFormat("MMM yyyy", esLocale)
            .format(cal.time)
            .replaceFirstChar { it.uppercase(esLocale) }
        return if (periodType == "quincenal") {
            val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
            if (day <= 15) "1ra Quincena $monthName" else "2da Quincena $monthName"
        } else {
            "Mes Completo $monthName"
        }
    }
}
