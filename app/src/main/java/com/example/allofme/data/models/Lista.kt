package com.example.allofme.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ListaPersonal(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val nombre: String
)

@Entity
data class ItemLista(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val listaId: String,
    val nombre: String,
    val completado: Boolean = false
)