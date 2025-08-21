package com.example.allofme.data.repository

import com.example.allofme.data.dao.GastoDao
import com.example.allofme.data.models.Gasto
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class GastoRepository(private val gastoDao: GastoDao) {

    // -------------------------------
    // Inserción
    // -------------------------------
    suspend fun insertarGastos(vararg gastos: Gasto) =
        gastoDao.insertarGastos(*gastos)

    suspend fun insertarGastosLista(gastos: List<Gasto>) =
        gastoDao.insertarGastosLista(gastos)

    // Agregar un gasto predeterminado global y replicarlo en todas las hojas
    suspend fun agregarGastoPredeterminadoGlobal(
        gasto: Gasto,
        hojas: List<String>,
        inicio: Long = 0L,
        fin: Long = Long.MAX_VALUE
    ) {
        // Verificar si el gasto predeterminado global ya existe
        val gastosGlobales = gastoDao.obtenerGastosPredeterminadosGlobales()
        val existeGlobal = gastosGlobales.any {
            it.descripcion == gasto.descripcion &&
                    it.monto == gasto.monto &&
                    it.porcentaje == gasto.porcentaje
        }
        if (existeGlobal) {
            return
        }

        // Insertar gasto predeterminado global
        val global = gasto.copy(
            id = UUID.randomUUID().toString(),
            hojaId = "", // Global no tiene hoja
            esPredeterminado = true,
            fecha = 0L, // Fecha genérica, no asociada a un día
            eliminado = false
        )
        gastoDao.insertarGastos(global)

        // Insertar copias en cada hoja, verificando duplicados
        hojas.forEach { hoja ->
            val existentes = gastoDao.obtenerGastosPorHojaYPeriodoSuspend(hoja, 0L, Long.MAX_VALUE)
            val yaExiste = existentes.any {
                it.descripcion == gasto.descripcion &&
                        it.esPredeterminado &&
                        it.monto == gasto.monto &&
                        it.porcentaje == gasto.porcentaje
            }
            if (!yaExiste) {
                val copia = global.copy(
                    id = UUID.randomUUID().toString(),
                    hojaId = hoja,
                    fecha = 0L // Fecha genérica, no asociada a un día
                )
                gastoDao.insertarGastos(copia)
            }
        }
    }

    // -------------------------------
    // Actualización
    // -------------------------------
    suspend fun actualizarGasto(gasto: Gasto) =
        gastoDao.actualizarGasto(gasto)

    // -------------------------------
    // Eliminación física
    // -------------------------------
    suspend fun eliminarGasto(gasto: Gasto) {
        gastoDao.eliminarGastoFisico(gasto)
    }

    suspend fun eliminarGastoFisico(gasto: Gasto) {
        gastoDao.eliminarGastoFisico(gasto)
    }
    suspend fun eliminarGastosPorRangoYHojaFisico(hojaId: String, inicio: Long, fin: Long) {
        gastoDao.eliminarGastosPorRangoYHojaFisico(hojaId, inicio, fin)
    }

    suspend fun eliminarGastosPorDescripcionFisico(descripcion: String) {
        gastoDao.eliminarGastosPorDescripcion(descripcion)
    }

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