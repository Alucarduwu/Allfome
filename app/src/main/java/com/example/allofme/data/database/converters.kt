package com.example.allofme.data.database

import androidx.room.TypeConverter

class ListConverters {

    // ---- Int List ----
    @TypeConverter
    fun fromIntList(value: List<Int>?): String {
        return value?.joinToString(",") ?: ""
    }

    @TypeConverter
    fun toIntList(value: String): List<Int> {
        if (value.isBlank()) return emptyList()
        return value.split(",").mapNotNull { it.toIntOrNull() }
    }

    // ---- String List ----
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return value?.joinToString(",") ?: ""
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return value.split(",")
    }
}

