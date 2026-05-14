package com.example.allofme.data.repository

import com.example.allofme.data.dao.GastoDao
import com.example.allofme.data.models.Gasto
import kotlinx.coroutines.flow.Flow

class GastoRepository(private val gastoDao: GastoDao) {

    // -------------------------------
    // Inserción
    // -------------------------------
    suspend fun insertarGastos(vararg gastos: Gasto) =
        gastoDao.insertarGastos(*gastos)

    suspend fun insertarGastosLista(gastos: List<Gasto>) =
        gastoDao.insertarGastosLista(gastos)

    // -------------------------------
    // Actualización
    // -------------------------------
    suspend fun actualizarGasto(gasto: Gasto) =
        gastoDao.actualizarGasto(gasto)

    // -------------------------------
    // Eliminación física
    // -------------------------------
    suspend fun eliminarGastoFisico(gasto: Gasto) =
        gastoDao.eliminarGastoFisico(gasto)

    suspend fun eliminarGastosPorRangoYHojaFisico(hojaId: String, inicio: Long, fin: Long) =
        gastoDao.eliminarGastosPorRangoYHojaFisico(hojaId, inicio, fin)

    suspend fun eliminarGastosPorDescripcionFisico(descripcion: String) =
        gastoDao.eliminarGastosPorDescripcion(descripcion)

    // -------------------------------
    // Consultas
    // -------------------------------
    fun obtenerGastosPorHojaYPeriodo(
        hojaId: String,
        inicio: Long,
        fin: Long
    ): Flow<List<Gasto>> =
        gastoDao.getGastosByHojaYFecha(hojaId, inicio, fin)

    suspend fun obtenerTodasLasHojas(): List<String> =
        gastoDao.obtenerTodasLasHojas()

    suspend fun obtenerGastosPredeterminadosGlobales(): List<Gasto> =
        gastoDao.obtenerGastosPredeterminadosGlobales()

    suspend fun obtenerGastosPorHojaYPeriodoSuspend(
        hoja: String,
        inicio: Long,
        fin: Long
    ): List<Gasto> =
        gastoDao.obtenerGastosPorHojaYPeriodoSuspend(hoja, inicio, fin)

    suspend fun obtenerGastosPredeterminadosPorHojaYPeriodo(
        hojaId: String,
        inicio: Long,
        fin: Long
    ): List<Gasto> =
        gastoDao.obtenerGastosPredeterminadosPorHojaYPeriodo(hojaId, inicio, fin)
}