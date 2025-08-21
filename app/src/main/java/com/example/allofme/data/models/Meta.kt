package com.example.allofme.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "metas")
data class Meta(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,                // Room generará el ID automáticamente
    val titulo: String,
    val descripcion: String? = null,
    val tipoFrecuencia: String,     // "Hoy", "Semana", "Mes"
    val repeticiones: Int = 1,
    val vecesPorPeriodo: Int = 1,
    val fechaInicio: Long = 0L,
    val fechaFin: Long? = null,
    var completado: Boolean = false,
    val recordatorio: Boolean = false,
    val horaRecordatorio: String = "",
    val diasSemana: List<String>? = null, // Para metas semanales
    val diaMes: Int? = null,             // Para metas mensuales
    val esPredeterminada: Boolean = false,
    val todoElMes: Boolean = false,

)
