package com.example.allofme.data.repository


import com.example.allofme.data.dao.PeriodoDao
import com.example.allofme.data.models.Periodo
import kotlinx.coroutines.flow.Flow

class PeriodoRepository(private val periodoDao: PeriodoDao) {
    fun obtenerTodosLosPeriodos(): Flow<List<Periodo>> = periodoDao.obtenerTodosLosPeriodos()

    suspend fun insertarPeriodo(periodo: Periodo) = periodoDao.insertarPeriodo(periodo)

    suspend fun eliminarPeriodo(periodo: Periodo) = periodoDao.eliminarPeriodo(periodo)

    suspend fun obtenerPeriodoPorId(id: String): Periodo? {
        return periodoDao.obtenerPeriodoPorId(id)
    }

    suspend fun actualizarIngreso(periodoId: String, nuevoIngreso: Double) {
        periodoDao.actualizarIngreso(periodoId, nuevoIngreso)
    }

    suspend fun actualizarPredeterminados(periodoId: String, predeterminados: List<String>) {
        periodoDao.actualizarPredeterminados(periodoId, predeterminados)
    }
}
