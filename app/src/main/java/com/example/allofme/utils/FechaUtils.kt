package com.example.allofme.utils

import java.util.Calendar

object FechaUtils {

    fun obtenerInicioFinDia(fecha: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = fecha
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val inicio = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val fin = cal.timeInMillis

        return Pair(inicio, fin)
    }

    fun obtenerInicioFinSemana(fecha: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = fecha
        // ajustar al primer día de la semana (depende configuración local)
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val inicio = cal.timeInMillis

        cal.add(Calendar.DAY_OF_WEEK, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val fin = cal.timeInMillis

        return Pair(inicio, fin)
    }

    fun obtenerInicioFinQuincena(fecha: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = fecha
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val dia = cal.get(Calendar.DAY_OF_MONTH)
        return if (dia <= 15) {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val inicio = cal.timeInMillis

            cal.set(Calendar.DAY_OF_MONTH, 15)
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val fin = cal.timeInMillis

            Pair(inicio, fin)
        } else {
            cal.set(Calendar.DAY_OF_MONTH, 16)
            val inicio = cal.timeInMillis

            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val fin = cal.timeInMillis

            Pair(inicio, fin)
        }
    }

    fun obtenerInicioFinMes(fecha: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = fecha

        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val inicio = cal.timeInMillis

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val fin = cal.timeInMillis

        return Pair(inicio, fin)
    }
}
