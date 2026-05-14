package com.example.allofme.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "gastos")
data class Gasto(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val descripcion: String,
    val monto: Double,
    val porcentaje: Boolean = false,
    val fecha: Long,
    val esPredeterminado: Boolean = false,
    val hojaId: String? = null,   // null = global / plantilla, no pertenece a una hoja
    val eliminado: Boolean = false // soft delete
)
