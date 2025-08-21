package com.example.allofme.data.dao

import androidx.room.*
import com.example.allofme.data.models.Meta
import kotlinx.coroutines.flow.Flow

@Dao
interface MetaDao {

    @Query("SELECT * FROM metas ORDER BY fechaInicio ASC")
    fun getAllMetas(): Flow<List<Meta>>

    @Query("SELECT * FROM metas WHERE id = :id")
    fun getMetaById(id: Int): Flow<Meta?>

    @Query("SELECT * FROM metas WHERE tipoFrecuencia = :tipo ORDER BY fechaInicio ASC")
    fun getMetasByTipo(tipo: String): Flow<List<Meta>>

    @Query("SELECT * FROM metas WHERE :dia IN (diasSemana)")
    fun getMetasByDiaSemana(dia: String): Flow<List<Meta>>

    @Query("SELECT * FROM metas WHERE diaMes = :dia OR todoElMes = 1")
    fun getMetasByDiaMes(dia: Int): Flow<List<Meta>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(meta: Meta)

    @Update
    suspend fun update(meta: Meta)

    @Delete
    suspend fun delete(meta: Meta)


    // Método para eliminar todas las metas
    @Query("DELETE FROM metas")
    suspend fun eliminarTodas()

    // Método para desactivar todas las metas (por ejemplo, campo 'activa')
    @Query("UPDATE metas SET completado = 0")
    suspend fun desactivarTodas()
}
