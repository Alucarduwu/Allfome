    package com.example.allofme.data.dao


    import androidx.room.*
    import com.example.allofme.data.models.Periodo
    import kotlinx.coroutines.flow.Flow

    @Dao
    interface PeriodoDao {
        @Query("SELECT * FROM periodo ORDER BY fechaInicio DESC")
        fun obtenerTodosLosPeriodos(): Flow<List<Periodo>>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertarPeriodo(periodo: Periodo)

        @Delete
        suspend fun eliminarPeriodo(periodo: Periodo)

        @Query("SELECT * FROM periodo WHERE id = :id LIMIT 1")
        suspend fun obtenerPeriodoPorId(id: String): Periodo?

        // 🔹 Nuevo: actualizar ingreso
        @Query("UPDATE periodo SET ingreso = :nuevoIngreso WHERE id = :periodoId")
        suspend fun actualizarIngreso(periodoId: String, nuevoIngreso: Double)

        // 🔹 Nuevo: actualizar predeterminados usados
        @Query("UPDATE periodo SET predeterminadosUsados = :predeterminados WHERE id = :periodoId")
        suspend fun actualizarPredeterminados(periodoId: String, predeterminados: List<String>)
    }
