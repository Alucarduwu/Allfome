package com.example.allofme.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Query
import com.example.allofme.data.models.Gasto
import kotlinx.coroutines.flow.Flow

@Dao
interface GastoDao {

    // Inserciones
    @Insert
    suspend fun insertarGastos(vararg gastos: Gasto)

    @Insert
    suspend fun insertarGastosLista(gastos: List<Gasto>)

    // Actualización
    @Update
    suspend fun actualizarGasto(gasto: Gasto)

    // Eliminación física individual
    @Delete
    suspend fun eliminarGasto(gasto: Gasto)

    // Obtención de gastos por hoja y periodo (flujo)
    @Query("SELECT * FROM gastos WHERE hojaId = :hojaId AND fecha BETWEEN :inicio AND :fin AND eliminado = 0")
    fun getGastosByHojaYFecha(hojaId: String, inicio: Long, fin: Long): Flow<List<Gasto>>

    // Obtención de gastos por hoja y periodo (suspend)
    @Query("SELECT * FROM gastos WHERE hojaId = :hojaId AND fecha BETWEEN :inicio AND :fin AND eliminado = 0")
    suspend fun obtenerGastosPorHojaYPeriodoSuspend(hojaId: String, inicio: Long, fin: Long): List<Gasto>

    // Conteo de predeterminados
    @Query("SELECT COUNT(*) FROM gastos WHERE hojaId = :hojaId AND fecha BETWEEN :inicio AND :fin AND esPredeterminado = 1 AND eliminado = 0")
    suspend fun countGastosPredeterminadosByHojaYFecha(hojaId: String, inicio: Long, fin: Long): Int

    // Gastos predeterminados globales
    @Query("SELECT * FROM gastos WHERE hojaId = '' AND esPredeterminado = 1 AND eliminado = 0")
    suspend fun obtenerGastosPredeterminadosGlobales(): List<Gasto>

    // Hojas con predeterminados por descripción
    @Query("SELECT DISTINCT hojaId FROM gastos WHERE esPredeterminado = 1 AND descripcion = :descripcion AND hojaId != '' AND eliminado = 0")
    suspend fun obtenerHojasConGastosPredeterminados(descripcion: String): List<String>

    // Gastos por descripción
    @Query("SELECT * FROM gastos WHERE descripcion = :descripcion AND esPredeterminado = 1 AND hojaId != '' AND eliminado = 0")
    suspend fun obtenerGastosPorDescripcion(descripcion: String): List<Gasto>

    // Marcado como eliminado (lógico)
    @Query("UPDATE gastos SET eliminado = 1 WHERE descripcion = :descripcion AND esPredeterminado = 1 AND hojaId != ''")
    suspend fun marcarGastosPorDescripcionComoEliminados(descripcion: String)

    @Query("UPDATE gastos SET eliminado = 1 WHERE hojaId = :hojaId AND fecha BETWEEN :inicio AND :fin")
    suspend fun marcarGastosPorRangoYHojaComoEliminados(hojaId: String, inicio: Long, fin: Long)

    // Predeterminados por hoja y periodo
    @Query("SELECT * FROM gastos WHERE hojaId = :hojaId AND fecha BETWEEN :inicio AND :fin AND esPredeterminado = 1 AND eliminado = 0")
    suspend fun obtenerGastosPredeterminadosPorHojaYPeriodo(hojaId: String, inicio: Long, fin: Long): List<Gasto>

    // Actualizar predeterminados por descripción
    @Query("UPDATE gastos SET monto = :nuevoMonto, porcentaje = :nuevoPorcentaje, eliminado = 0 WHERE descripcion = :descripcion AND esPredeterminado = 1 AND hojaId != ''")
    suspend fun actualizarGastosPorDescripcion(descripcion: String, nuevoMonto: Double, nuevoPorcentaje: Boolean)

    // Eliminaciones físicas completas
    @Delete
    suspend fun eliminarGastoFisico(gasto: Gasto)

    @Query("DELETE FROM gastos WHERE descripcion = :descripcion")
    suspend fun eliminarGastosPorDescripcion(descripcion: String)

    @Query("DELETE FROM gastos WHERE hojaId = :hojaId AND fecha BETWEEN :inicio AND :fin")
    suspend fun eliminarGastosPorRangoYHojaFisico(hojaId: String, inicio: Long, fin: Long)

    @Query("SELECT DISTINCT hojaId FROM gastos WHERE hojaId != ''")
    suspend fun obtenerTodasLasHojas(): List<String>

}
