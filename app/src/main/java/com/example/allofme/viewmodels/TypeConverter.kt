package com.example.allofme.viewmodels

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromList(lista: List<String>?): String? {
        return lista?.joinToString(",")
    }

    @TypeConverter
    fun toList(data: String?): List<String>? {
        return data?.split(",")
    }
}
