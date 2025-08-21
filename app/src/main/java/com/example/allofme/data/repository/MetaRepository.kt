package com.example.allofme.data.repository

import com.example.allofme.data.models.Meta
import com.example.allofme.data.dao.MetaDao
import kotlinx.coroutines.flow.Flow

class MetaRepository(private val metaDao: MetaDao) {

    // Obtener todas las metas como flujo
    fun obtenerMetas(): Flow<List<Meta>> = metaDao.getAllMetas()

    // Insertar nueva meta
    suspend fun agregarMeta(meta: Meta) {
        metaDao.insert(meta)
    }

    // Actualizar meta existente
    suspend fun actualizarMeta(meta: Meta) {
        metaDao.update(meta)
    }

    // Eliminar meta específica
    suspend fun eliminarMeta(meta: Meta) {
        metaDao.delete(meta)
    }

    // Desactivar todas las metas (por ejemplo para activar solo una)
    suspend fun desactivarTodasLasMetas() {
        metaDao.desactivarTodas()
    }

    // Obtener meta por ID
    fun obtenerMetaPorId(id: Int): Flow<Meta?> = metaDao.getMetaById(id)

    // Obtener metas por tipo de frecuencia
    fun obtenerPorTipo(tipo: String): Flow<List<Meta>> = metaDao.getMetasByTipo(tipo)

    // Obtener metas por día de la semana
    fun obtenerPorDiaSemana(dia: String): Flow<List<Meta>> = metaDao.getMetasByDiaSemana(dia)

    // Obtener metas por día del mes
    fun obtenerPorDiaMes(dia: Int): Flow<List<Meta>> = metaDao.getMetasByDiaMes(dia)

    // Eliminar todas las metas
    suspend fun eliminarTodas() {
        metaDao.eliminarTodas()
    }

}
