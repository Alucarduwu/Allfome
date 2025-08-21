package com.example.allofme.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.allofme.data.models.Periodo
import com.example.allofme.data.repository.PeriodoRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class PeriodoViewModel(
    private val repository: PeriodoRepository
) : ViewModel() {

    companion object {
        private const val TAG = "📅 PeriodoViewModelLogs"
        private fun log(msg: String) {
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n$msg\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        }
    }



    val periodos: StateFlow<List<Periodo>> = repository.obtenerTodosLosPeriodos()
        .onEach { lista ->
            log("📊 periodos Flow -> tamaño=${lista.size}")
            lista.forEach { log("📄 Periodo -> id=${it.id} | días=${it.diasIncluidos} | ingreso=${it.ingreso}") }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _periodoSeleccionado = MutableStateFlow<Periodo?>(null)
    val periodoSeleccionado: StateFlow<Periodo?> = _periodoSeleccionado.asStateFlow()

    init {
        log("🚀 Init PeriodoViewModel")
        viewModelScope.launch {
            periodos.collect { lista ->
                log("📥 periodos.collect -> recibidos ${lista.size} elementos")
                if (_periodoSeleccionado.value?.id !in lista.map { it.id }) {
                    _periodoSeleccionado.value = lista.firstOrNull()
                    log("🆕 Periodo seleccionado cambiado -> ${_periodoSeleccionado.value}")
                } else {
                    val actualizado = lista.find { it.id == _periodoSeleccionado.value?.id }
                    if (actualizado != null) {
                        _periodoSeleccionado.value = actualizado
                        log("♻️ Periodo seleccionado actualizado -> $actualizado")
                    }
                }
            }
        }
    }

    // ---------- NUEVO: flujo ingreso actualizado --------
    val ingresoPeriodo: StateFlow<Double> = periodoSeleccionado
        .map { it?.ingreso ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)
    // ----------------------------------------------------

    fun guardarPeriodo(periodo: Periodo, onGuardado: ((String) -> Unit)? = null) {
        log("💾 guardarPeriodo() -> $periodo")

        val periodoFinal = if (periodo.id.isBlank()) {
            periodo.copy(id = UUID.randomUUID().toString())
        } else periodo

        _periodoSeleccionado.value = periodoFinal

        viewModelScope.launch {
            repository.insertarPeriodo(periodoFinal)
            log("✅ Periodo guardado en repo -> $periodoFinal")
            onGuardado?.invoke(periodoFinal.id)
        }
    }

    fun actualizarPeriodo(periodo: Periodo) {
        log("✏️ actualizarPeriodo() -> $periodo")
        viewModelScope.launch {
            repository.insertarPeriodo(periodo)
            log("✅ Periodo actualizado en repo -> $periodo")
            _periodoSeleccionado.value = periodo
        }
    }

    fun eliminarPeriodo(periodo: Periodo) {
        log("🗑 eliminarPeriodo() -> $periodo")
        viewModelScope.launch {
            repository.eliminarPeriodo(periodo)
            log("✅ Periodo eliminado en repo -> $periodo")
            if (_periodoSeleccionado.value?.id == periodo.id) {
                _periodoSeleccionado.value = null
                log("⚠️ Periodo seleccionado reseteado a null")
            }
        }
    }

    fun seleccionarPeriodo(periodo: Periodo) {
        log("🔄 seleccionarPeriodo() -> $periodo")
        _periodoSeleccionado.value = periodo
    }

    fun setDiasIncluidos(periodoId: String, dias: List<Int>) {
        log("📆 setDiasIncluidos() -> periodoId=$periodoId | días=$dias")
        _periodoSeleccionado.value?.let { actual ->
            if (actual.id == periodoId) {
                val actualizado = actual.copy(diasIncluidos = dias)
                actualizarPeriodo(actualizado)
            }
        }
    }

    fun setIngresoPeriodo(periodoId: String, ingreso: Double) {
        log("💰 setIngresoPeriodo() -> periodoId=$periodoId | ingreso=$ingreso")
        viewModelScope.launch {
            repository.actualizarIngreso(periodoId, ingreso)
            log("✅ Ingreso actualizado en repo para periodo=$periodoId")
        }
        _periodoSeleccionado.value?.let { actual ->
            if (actual.id == periodoId) {
                _periodoSeleccionado.value = actual.copy(ingreso = ingreso)
                log("♻️ Ingreso actualizado en periodo seleccionado")
            }
        }
    }

    fun setPredeterminadosPeriodo(periodoId: String, seleccion: List<String>) {
        log("🗂 setPredeterminadosPeriodo() -> periodoId=$periodoId | selección=$seleccion")
        viewModelScope.launch {
            repository.actualizarPredeterminados(periodoId, seleccion)
            log("✅ Predeterminados actualizados en repo para periodo=$periodoId")
        }
        _periodoSeleccionado.value?.let { actual ->
            if (actual.id == periodoId) {
                _periodoSeleccionado.value = actual.copy(predeterminadosUsados = seleccion)
                log("♻️ Predeterminados actualizados en periodo seleccionado")
            }
        }
    }
}

class PeriodoViewModelFactory(
    private val repository: PeriodoRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PeriodoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PeriodoViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
