package com.example.allofme.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "periodo")
data class Periodo(
    @PrimaryKey val id: String,
    val nombre: String,
    val fechaInicio: Long,
    val fechaFin: Long,

    // ⚠️ En tu app tú lo usas como "día del mes" (1..31), NO como 0=Domingo.
    val diasIncluidos: List<Int>,

    val ingreso: Double = 0.0,

    // IDs de predeterminados globales elegidos al crear la hoja (solo historial)
    val predeterminadosUsados: List<String> = emptyList()
)