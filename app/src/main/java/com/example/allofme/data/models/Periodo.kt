package com.example.allofme.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "periodo")
data class Periodo(
    @PrimaryKey val id: String,
    val nombre: String,
    val fechaInicio: Long,
    val fechaFin: Long,
    val diasIncluidos: List<Int>, // 0=Domingo, 1=Lunes...
    val ingreso: Double = 0.0,
    val predeterminadosUsados: List<String> = emptyList() // IDs de gastos predeterminados
)


