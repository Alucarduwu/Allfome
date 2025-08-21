package com.example.allofme.data.dao

import androidx.room.*
import com.example.allofme.data.models.ItemLista
import com.example.allofme.data.models.ListaPersonal
import kotlinx.coroutines.flow.Flow

@Dao
interface ListaDao {

    @Query("SELECT * FROM ListaPersonal")
    fun getAllListas(): Flow<List<ListaPersonal>>

    @Query("SELECT * FROM ItemLista WHERE listaId = :listaId")
    fun getItemsForLista(listaId: String): Flow<List<ItemLista>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLista(lista: ListaPersonal)

    @Query("DELETE FROM ListaPersonal WHERE id = :id")
    suspend fun deleteListaById(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemLista)

    @Update
    suspend fun updateItem(item: ItemLista)

    @Query("DELETE FROM ItemLista WHERE id = :id")
    suspend fun deleteItemById(id: String)
}