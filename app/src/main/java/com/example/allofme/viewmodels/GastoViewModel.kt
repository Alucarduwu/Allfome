package com.example.allofme.viewmodels

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.allofme.data.models.Gasto
import com.example.allofme.data.models.Periodo
import com.example.allofme.data.repository.GastoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

class GastoViewModel(
    private val gastoRepository: GastoRepository,
    private val sharedPrefs: SharedPreferences,
    initialHojaId: String,
    ingresoExterno: StateFlow<Double>,
    periodoSeleccionadoFlow: StateFlow<Periodo?>
) : ViewModel() {

    companion object {
        private const val GASTOS_ELIMINADOS_KEY = "gastos_eliminados"
    }

    private val _hojaId = MutableStateFlow(initialHojaId)
    val hojaId: StateFlow<String> = _hojaId.asStateFlow()

    private val _ingresoTotal = MutableStateFlow(0.0)
    val ingresoTotal: StateFlow<Double> = _ingresoTotal.asStateFlow()

    private val _fechaInicioPeriodo = MutableStateFlow(0L)
    private val _fechaFinPeriodo = MutableStateFlow(0L)
    private val _diasSeleccionados = MutableStateFlow<List<Int>>(emptyList())
    private val _refreshTrigger = MutableStateFlow(0)
    private val _gastosPredeterminados = MutableStateFlow<List<Gasto>>(emptyList())

    // Clase de estado combinando todo
    data class EstadoGastos(
        val hoja: String,
        val inicio: Long,
        val fin: Long,
        val dias: List<Int>,
        val refresh: Int,
        val predeterminados: List<Gasto>
    )



    // Luego combinamos con predeterminados
    @OptIn(ExperimentalCoroutinesApi::class)
    val gastos: StateFlow<List<Gasto>> = combine(
        hojaId,
        _fechaInicioPeriodo,
        _fechaFinPeriodo,
        _diasSeleccionados,
        _gastosPredeterminados
    ) { hoja, inicio, fin, dias, predeterminados ->
        Triple(Triple(hoja, inicio, fin), dias, predeterminados)
    }.flatMapLatest { (triple, dias, predeterminados) ->
        val (hoja, inicio, fin) = triple
        gastoRepository.obtenerGastosPorHojaYPeriodo(hoja, inicio, fin)
            .map { lista ->
                val listaConPredeterminados = lista.toMutableList()
                predeterminados.forEach { pred ->
                    val existe = listaConPredeterminados.any { it.esPredeterminado && it.descripcion == pred.descripcion }
                    if (!existe) {
                        listaConPredeterminados.add(
                            pred.copy(
                                id = UUID.randomUUID().toString(),
                                hojaId = hoja,
                                esPredeterminado = true,
                                fecha = inicio
                            )
                        )
                    }
                }

                if (dias.isEmpty()) listaConPredeterminados.sortedBy { it.fecha }
                else listaConPredeterminados.filter { gasto ->
                    val cal = Calendar.getInstance().apply { timeInMillis = gasto.fecha }
                    dias.contains(cal.get(Calendar.DAY_OF_MONTH))
                }.sortedBy { it.fecha }
            }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())



    val totalGastosMonto: StateFlow<Double> = gastos.map { lista ->
        val sinDuplicados = lista.distinctBy { it.id }
        sinDuplicados.sumOf { gasto ->
            if (gasto.porcentaje) ingresoTotal.value * gasto.monto / 100 else gasto.monto
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    val totalRestante: StateFlow<Double> = combine(
        ingresoTotal, totalGastosMonto
    ) { ingreso, gastos -> ingreso - gastos }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0.0)

    init {
        viewModelScope.launch {
            val lista = gastoRepository.obtenerGastosPredeterminadosGlobales()
                .filter { !gastoEliminadoGlobal(it.descripcion) }
            _gastosPredeterminados.value = lista.sortedBy { it.descripcion }
            _refreshTrigger.value++
        }

        viewModelScope.launch {
            ingresoExterno.collect { ingreso ->
                _ingresoTotal.value = ingreso
                _refreshTrigger.value++
            }
        }

        viewModelScope.launch {
            combine(hojaId, periodoSeleccionadoFlow) { hoja, periodo -> hoja to periodo }
                .collect { (hoja, periodo) ->
                    if (periodo != null && hoja.isNotBlank()) {
                        _fechaInicioPeriodo.value = periodo.fechaInicio
                        _fechaFinPeriodo.value = periodo.fechaFin
                        _diasSeleccionados.value = periodo.diasIncluidos
                        _ingresoTotal.value = periodo.ingreso
                        _refreshTrigger.value++
                    }
                }
        }
    }

    fun setCustomPeriodoRange(inicio: Long, fin: Long) {
        if (inicio > 0 && fin >= inicio) {
            _fechaInicioPeriodo.value = inicio
            _fechaFinPeriodo.value = fin
            viewModelScope.launch {
                _refreshTrigger.value++
            }
        }
    }

    fun setDiasSeleccionados(dias: List<Int>) {
        _diasSeleccionados.value = dias
        _refreshTrigger.value++
    }

    @SuppressLint("MutatingSharedPrefs")
    fun agregarGastoPredeterminado(gasto: Gasto) {
        viewModelScope.launch {
            if (gasto.descripcion.isBlank()) return@launch
            val existeGlobal = _gastosPredeterminados.value.any {
                it.descripcion == gasto.descripcion && it.monto == gasto.monto && it.porcentaje == gasto.porcentaje
            }
            if (existeGlobal) return@launch

            _gastosPredeterminados.value = _gastosPredeterminados.value + gasto
            _refreshTrigger.value++
        }
    }

    fun agregarGasto(gasto: Gasto) {
        viewModelScope.launch {
            if (_hojaId.value.isBlank()) return@launch
            gastoRepository.insertarGastos(gasto.copy(hojaId = _hojaId.value, esPredeterminado = false))
            _refreshTrigger.value++
        }
    }

    fun actualizarGasto(gasto: Gasto) {
        viewModelScope.launch {
            gastoRepository.actualizarGasto(gasto)
            _refreshTrigger.value++
        }
    }

    @SuppressLint("MutatingSharedPrefs")
    fun eliminarGasto(gasto: Gasto) {
        viewModelScope.launch {
            if (gasto.esPredeterminado) {
                _gastosPredeterminados.value = _gastosPredeterminados.value.filter { it.descripcion != gasto.descripcion }
                val set = sharedPrefs.getStringSet(GASTOS_ELIMINADOS_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                set.add(gasto.descripcion)
                sharedPrefs.edit { putStringSet(GASTOS_ELIMINADOS_KEY, set) }
            } else {
                gastoRepository.eliminarGastoFisico(gasto)
            }
            _refreshTrigger.value++
        }
    }

    private fun gastoEliminadoGlobal(descripcion: String): Boolean {
        val set = sharedPrefs.getStringSet(GASTOS_ELIMINADOS_KEY, emptySet()) ?: emptySet()
        return set.contains(descripcion)
    }

    fun obtenerTotalGastosEnDia(fecha: Long, onResult: (Double) -> Unit) {
        viewModelScope.launch {
            if (_hojaId.value.isBlank()) {
                onResult(0.0)
                return@launch
            }
            val inicioDia = obtenerInicioDeDia(fecha)
            val finDia = obtenerFinDeDia(fecha)
            val gastosDelDia = gastoRepository.obtenerGastosPorHojaYPeriodoSuspend(_hojaId.value, inicioDia, finDia)
                .filter { !it.esPredeterminado }

            val total = gastosDelDia.sumOf { gasto ->
                if (gasto.porcentaje) _ingresoTotal.value * gasto.monto / 100 else gasto.monto
            }
            onResult(total)
            _refreshTrigger.value++
        }
    }

    fun eliminarGastosDeDia(fecha: Long) {
        viewModelScope.launch {
            if (_hojaId.value.isBlank()) return@launch
            val inicioDia = obtenerInicioDeDia(fecha)
            val finDia = obtenerFinDeDia(fecha)
            val gastosDelDia = gastoRepository.obtenerGastosPorHojaYPeriodoSuspend(_hojaId.value, inicioDia, finDia)
                .filter { !it.esPredeterminado }

            gastosDelDia.forEach { gastoRepository.eliminarGastoFisico(it) }
            _refreshTrigger.value++
        }
    }

    private fun obtenerInicioDeDia(fecha: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = fecha
        cal.setToStartOfDay()
        return cal.timeInMillis
    }

    private fun obtenerFinDeDia(fecha: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = fecha
        cal.setToEndOfDay()
        return cal.timeInMillis
    }

    private fun Calendar.setToStartOfDay() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun Calendar.setToEndOfDay() {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }

    class GastoViewModelFactory(
        private val gastoRepository: GastoRepository,
        private val sharedPrefs: SharedPreferences,
        private val hojaId: String,
        private val ingresoExterno: StateFlow<Double>,
        private val periodoSeleccionadoFlow: StateFlow<Periodo?>
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GastoViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return GastoViewModel(gastoRepository, sharedPrefs, hojaId, ingresoExterno, periodoSeleccionadoFlow) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
