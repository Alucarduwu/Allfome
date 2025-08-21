package com.example.allofme.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.allofme.data.dao.GastoDao
import com.example.allofme.data.dao.ListaDao
import com.example.allofme.data.dao.PeriodoDao
import com.example.allofme.data.dao.MetaDao
import com.example.allofme.data.models.Gasto
import com.example.allofme.data.models.ItemLista
import com.example.allofme.data.models.ListaPersonal
import com.example.allofme.data.models.Periodo
import com.example.allofme.data.models.Meta

@Database(
    entities = [Gasto::class, Periodo::class, Meta::class, ListaPersonal::class, ItemLista::class],
    version = 4, // Incremented from 3 to 4 due to new entities
    exportSchema = false
)
@TypeConverters(ListConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun gastoDao(): GastoDao
    abstract fun periodoDao(): PeriodoDao
    abstract fun metaDao(): MetaDao
    abstract fun ListaDao(): ListaDao // Added ListaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}