package com.example.allofme.data.repository

import com.example.allofme.data.dao.ListaDao
import com.example.allofme.data.models.ItemLista
import com.example.allofme.data.models.ListaPersonal
import kotlinx.coroutines.flow.Flow

class ListaRepository(private val dao: ListaDao) {

    fun getListas(): Flow<List<ListaPersonal>> = dao.getAllListas()

    fun getItems(listaId: String): Flow<List<ItemLista>> = dao.getItemsForLista(listaId)

    suspend fun insertLista(lista: ListaPersonal) {
        dao.insertLista(lista)
    }

    suspend fun deleteListaById(id: String) {
        dao.deleteListaById(id)
    }

    suspend fun insertItem(item: ItemLista) {
        dao.insertItem(item)
    }

    suspend fun updateItem(item: ItemLista) {
        dao.updateItem(item)
    }

    suspend fun deleteItemById(id: String) {
        dao.deleteItemById(id)
    }
}