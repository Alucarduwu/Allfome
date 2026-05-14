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

    // -------------------------
    // Fuente única de verdad
    // -------------------------
    val periodos: StateFlow<List<Periodo>> = repository.obtenerTodosLosPeriodos()
        .onEach { lista ->
            log("📊 periodos Flow -> tamaño=${lista.size}")
            lista.forEach { p ->
                log("📄 Periodo -> id=${p.id} | nombre=${p.nombre} | ${p.fechaInicio}..${p.fechaFin} | ingreso=${p.ingreso}")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // -------------------------
    // Selección (solo ID)
    // -------------------------
    private val _periodoSeleccionadoId = MutableStateFlow<String?>(null)
    val periodoSeleccionadoId: StateFlow<String?> = _periodoSeleccionadoId.asStateFlow()

    // Periodo seleccionado derivado de periodos + selectedId (sin loops)
    val periodoSeleccionado: StateFlow<Periodo?> = combine(periodos, periodoSeleccionadoId) { lista, selectedId ->
        when {
            lista.isEmpty() -> null
            selectedId == null -> lista.firstOrNull()
            else -> lista.firstOrNull { it.id == selectedId } ?: lista.firstOrNull()
        }
    }
        .distinctUntilChanged()
        .onEach { sel ->
            log("✅ periodoSeleccionado derivado -> ${sel?.id} | ${sel?.nombre}")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Ingreso reactivo (para tu circulito)
    val ingresoPeriodo: StateFlow<Double> = periodoSeleccionado
        .map { it?.ingreso ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    init {
        log("🚀 Init PeriodoViewModel")

        // Si no hay selección explícita, selecciona el primero cuando lleguen datos
        viewModelScope.launch {
            periodos
                .map { it.firstOrNull()?.id }
                .filterNotNull()
                .collect { firstId ->
                    if (_periodoSeleccionadoId.value == null) {
                        _periodoSeleccionadoId.value = firstId
                        log("🆕 Selección inicial automática -> $firstId")
                    }
                }
        }
    }

    // -------------------------
    // Acciones públicas
    // -------------------------
    fun seleccionarPeriodo(periodo: Periodo) {
        log("🔄 seleccionarPeriodo() -> ${periodo.id} | ${periodo.nombre}")
        _periodoSeleccionadoId.value = periodo.id
    }

    /**
     * Guardar periodo y obtener ID real.
     * Ideal para tu "Crear Hoja" porque al final necesitas el id para:
     * - setHojaId(periodo.id)
     * - copiarPredeterminadosAHoja(hojaId = periodo.id, ...)
     */
    fun guardarPeriodo(periodo: Periodo, onGuardado: ((Periodo) -> Unit)? = null) {
        val final = if (periodo.id.isBlank()) periodo.copy(id = UUID.randomUUID().toString()) else periodo

        log("💾 guardarPeriodo() -> id=${final.id} | nombre=${final.nombre}")
        _periodoSeleccionadoId.value = final.id

        viewModelScope.launch {
            repository.insertarPeriodo(final)
            log("✅ Periodo guardado en repo -> ${final.id}")
            onGuardado?.invoke(final) // 👈 ya te regresa el objeto final con id
        }
    }

    fun actualizarPeriodo(periodo: Periodo) {
        val final = if (periodo.id.isBlank()) periodo.copy(id = UUID.randomUUID().toString()) else periodo
        log("✏️ actualizarPeriodo() -> ${final.id}")

        _periodoSeleccionadoId.value = final.id
        viewModelScope.launch {
            repository.insertarPeriodo(final)
            log("✅ Periodo actualizado -> ${final.id}")
        }
    }

    fun eliminarPeriodo(periodo: Periodo) {
        log("🗑 eliminarPeriodo() -> ${periodo.id}")
        viewModelScope.launch {
            repository.eliminarPeriodo(periodo)
            log("✅ Periodo eliminado -> ${periodo.id}")

            // si borraste el seleccionado, selecciona otro o null
            if (_periodoSeleccionadoId.value == periodo.id) {
                val siguiente = periodos.value.firstOrNull { it.id != periodo.id }?.id
                _periodoSeleccionadoId.value = siguiente
                log("♻️ Selección movida a -> $siguiente")
            }
        }
    }

    fun setDiasIncluidos(periodoId: String, dias: List<Int>) {
        log("📆 setDiasIncluidos() -> periodoId=$periodoId | días=$dias")

        val actual = periodos.value.firstOrNull { it.id == periodoId } ?: return
        val actualizado = actual.copy(diasIncluidos = dias)

        actualizarPeriodo(actualizado)
    }

    fun setIngresoPeriodo(periodoId: String, ingreso: Double) {
        log("💰 setIngresoPeriodo() -> periodoId=$periodoId | ingreso=$ingreso")

        viewModelScope.launch {
            repository.actualizarIngreso(periodoId, ingreso)
            log("✅ Ingreso actualizado en repo -> periodo=$periodoId")
        }

        // opcional: optimista (si tu UI lo necesita inmediato)
        val actual = periodos.value.firstOrNull { it.id == periodoId }
        if (actual != null) {
            _periodoSeleccionadoId.value = periodoId
        }
    }

    fun setPredeterminadosPeriodo(periodoId: String, seleccion: List<String>) {
        log("🗂 setPredeterminadosPeriodo() -> periodoId=$periodoId | selección=$seleccion")

        viewModelScope.launch {
            repository.actualizarPredeterminados(periodoId, seleccion)
            log("✅ Predeterminados actualizados en repo -> periodo=$periodoId")
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