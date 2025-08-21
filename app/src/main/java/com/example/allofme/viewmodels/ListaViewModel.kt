package com.example.allofme.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.allofme.data.models.ItemLista
import com.example.allofme.data.models.ListaPersonal
import com.example.allofme.data.repository.ListaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ListasViewModel(private val repository: ListaRepository) : ViewModel() {

    private val _listas = MutableStateFlow<List<ListaPersonal>>(emptyList())
    val listas: StateFlow<List<ListaPersonal>> = _listas.asStateFlow()

    private val _items = MutableStateFlow<List<ItemLista>>(emptyList())
    val items: StateFlow<List<ItemLista>> = _items.asStateFlow()

    private val _listaSeleccionadaId = MutableStateFlow<String?>(null)
    val listaSeleccionadaId: StateFlow<String?> = _listaSeleccionadaId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var itemsJob: Job? = null // Para cancelar el flujo de ítems anterior

    init {
        cargarListas()
    }

    private fun cargarListas() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.getListas().collect { listas ->
                    _listas.value = listas
                    // Solo limpiar listaSeleccionadaId si no hay listas
                    if (listas.isEmpty()) {
                        _listaSeleccionadaId.value = null
                        _items.value = emptyList()
                    }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun seleccionarLista(id: String) {
        if (_listaSeleccionadaId.value != id) {
            _listaSeleccionadaId.value = id
            // Limpiar ítems inmediatamente para evitar mostrar ítems de la lista anterior
            _items.value = emptyList()
            // Cancelar el flujo de ítems anterior
            itemsJob?.cancel()
            cargarItems(id)
        }
    }

    private fun cargarItems(listaId: String) {
        itemsJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.getItems(listaId).collect { items ->
                    _items.value = items
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun agregarLista(nombre: String) {
        if (nombre.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val nuevaLista = ListaPersonal(id = UUID.randomUUID().toString(), nombre = nombre.trim())
                repository.insertLista(nuevaLista)
                // Actualización local
                _listas.value = _listas.value + nuevaLista
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun eliminarLista(id: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.deleteListaById(id)
                // Actualización local
                _listas.value = _listas.value.filter { it.id != id }
                if (_listaSeleccionadaId.value == id) {
                    // Seleccionar la primera lista disponible o ninguna
                    _listaSeleccionadaId.value = _listas.value.firstOrNull()?.id
                    _items.value = emptyList()
                    _listaSeleccionadaId.value?.let { cargarItems(it) }
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun agregarItem(nombre: String) {
        val listaId = _listaSeleccionadaId.value ?: return
        if (nombre.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val nuevoItem = ItemLista(id = UUID.randomUUID().toString(), listaId = listaId, nombre = nombre.trim(), completado = false)
                repository.insertItem(nuevoItem)
                // Actualización local
                _items.value = _items.value + nuevoItem
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleItem(id: String) {
        val listaId = _listaSeleccionadaId.value ?: return
        val item = _items.value.find { it.id == id } ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val updatedItem = item.copy(completado = !item.completado)
                repository.updateItem(updatedItem)
                // Actualización local
                _items.value = _items.value.map { if (it.id == id) updatedItem else it }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun eliminarItem(id: String) {
        val listaId = _listaSeleccionadaId.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.deleteItemById(id)
                // Actualización local
                _items.value = _items.value.filter { it.id != id }
            } finally {
                _isLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        itemsJob?.cancel()
    }
}

class ListasViewModelFactory(private val repository: ListaRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ListasViewModel::class.java)) {
            return ListasViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}