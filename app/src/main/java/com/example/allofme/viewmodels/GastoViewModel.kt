package com.example.allofme.viewmodels

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
import java.util.Calendar
import java.util.UUID
import kotlin.math.abs

class GastoViewModel(
    private val gastoRepository: GastoRepository,
    private val sharedPrefs: SharedPreferences,
    initialHojaId: String,
    ingresoExterno: StateFlow<Double>,
    periodoSeleccionadoFlow: StateFlow<Periodo?>
) : ViewModel() {

    companion object {
        private const val PREDS_ELIMINADOS_IDS_KEY = "preds_eliminados_ids"
    }

    private val started = SharingStarted.WhileSubscribed(5_000)

    // ---------------------------
    // Estado base
    // ---------------------------
    private val _hojaId = MutableStateFlow(initialHojaId)
    val hojaId: StateFlow<String> = _hojaId.asStateFlow()

    private val _ingresoTotal = MutableStateFlow(0.0) // ingreso base del periodo
    val ingresoTotal: StateFlow<Double> = _ingresoTotal.asStateFlow()

    private val _fechaInicioPeriodo = MutableStateFlow(0L)
    private val _fechaFinPeriodo = MutableStateFlow(0L)
    private val _diasSeleccionados = MutableStateFlow<List<Int>>(emptyList())

    // ---------------------------
    // Predeterminados globales (plantilla)
    // ---------------------------
    private val _predeterminadosGlobales = MutableStateFlow<List<Gasto>>(emptyList())
    val predeterminadosGlobales: StateFlow<List<Gasto>> = _predeterminadosGlobales.asStateFlow()

    // ---------------------------
    // Gastos por hoja/periodo (incluye snapshot predeterminados de ESA hoja en DB)
    // ---------------------------
    @OptIn(ExperimentalCoroutinesApi::class)
    val gastos: StateFlow<List<Gasto>> = combine(
        hojaId, _fechaInicioPeriodo, _fechaFinPeriodo, _diasSeleccionados
    ) { hoja, inicio, fin, dias ->
        Params(hoja = hoja, inicio = inicio, fin = fin, dias = dias)
    }
        .distinctUntilChanged()
        .flatMapLatest { p ->
            if (p.hoja.isBlank() || p.inicio <= 0L || p.fin <= 0L || p.fin < p.inicio) {
                flowOf(emptyList())
            } else {
                gastoRepository.obtenerGastosPorHojaYPeriodo(p.hoja, p.inicio, p.fin)
                    .map { lista ->
                        val base = lista.filter { !it.eliminado }
                        val filtrada = if (p.dias.isEmpty()) base else base.filter { g ->
                            val cal = Calendar.getInstance().apply { timeInMillis = g.fecha }
                            p.dias.contains(cal.get(Calendar.DAY_OF_MONTH))
                        }
                        filtrada.sortedBy { it.fecha }
                    }
            }
        }
        .stateIn(viewModelScope, started, emptyList())

    // ---------------------------
    // ✅ Totales (soportan ingresos por día con monto NEGATIVO)
    // Regla:
    //  - Gasto normal: montoReal > 0
    //  - Ingreso del día: montoReal < 0 (se guarda como negativo)
    // ---------------------------

    // Solo suma GASTOS (positivos)
    val totalGastosMonto: StateFlow<Double> =
        combine(gastos, ingresoTotal) { lista, ingreso ->
            lista.sumOf { it.montoReal(ingreso) }
                .let { total -> // esto incluye negativos; lo separamos:
                    lista
                        .map { it.montoReal(ingreso) }
                        .filter { it > 0.0 }
                        .sum()
                }
        }.stateIn(viewModelScope, started, 0.0)

    // Suma de INGRESOS EXTRA (negativos convertidos a positivos)
    val totalIngresosExtra: StateFlow<Double> =
        combine(gastos, ingresoTotal) { lista, ingreso ->
            lista
                .map { it.montoReal(ingreso) }
                .filter { it < 0.0 }
                .sumOf { abs(it) }
        }.stateIn(viewModelScope, started, 0.0)

    // Ingreso disponible = ingreso base + ingresos extra
    val ingresoDisponible: StateFlow<Double> =
        combine(ingresoTotal, totalIngresosExtra) { base, extras ->
            base + extras
        }.stateIn(viewModelScope, started, 0.0)

    // Restante = disponible - gastos
    val totalRestante: StateFlow<Double> =
        combine(ingresoDisponible, totalGastosMonto) { disponible, gastosPos ->
            disponible - gastosPos
        }.stateIn(viewModelScope, started, 0.0)

    init {
        viewModelScope.launch { refrescarPredeterminadosGlobales() }

        // periodo seleccionado => rango/dias/ingreso base
        viewModelScope.launch {
            combine(hojaId, periodoSeleccionadoFlow) { hoja, periodo -> hoja to periodo }
                .distinctUntilChanged()
                .collect { (hoja, periodo) ->
                    if (periodo != null && hoja.isNotBlank()) {
                        _fechaInicioPeriodo.value = periodo.fechaInicio
                        _fechaFinPeriodo.value = periodo.fechaFin
                        _diasSeleccionados.value = periodo.diasIncluidos
                        _ingresoTotal.value = periodo.ingreso
                    }
                }
        }

        // ingreso externo (si aplica): no pisa con 0
        viewModelScope.launch {
            ingresoExterno
                .filter { it > 0.0 }
                .collect { _ingresoTotal.value = it }
        }
    }

    // ---------------------------
    // setters
    // ---------------------------
    fun setHojaId(nuevaHojaId: String) {
        if (nuevaHojaId.isBlank()) return
        _hojaId.value = nuevaHojaId
    }

    fun setCustomPeriodoRange(inicio: Long, fin: Long) {
        if (inicio > 0L && fin >= inicio) {
            _fechaInicioPeriodo.value = inicio
            _fechaFinPeriodo.value = fin
        }
    }

    fun setDiasSeleccionados(dias: List<Int>) { _diasSeleccionados.value = dias }

    // ---------------------------
    // ✅ CREAR HOJA + COPIAR PREDETERMINADOS SELECCIONADOS (snapshot)
    // Periodo debe venir YA guardado con ID real
    // ---------------------------
    fun crearHojaConPredeterminados(
        periodo: Periodo,
        ingreso: Double,
        idsPredSeleccionados: List<String>,
        onFinish: (Periodo) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (periodo.id.isBlank()) return@launch

            val periodoFinal = periodo.copy(ingreso = ingreso)
            val baseFecha = periodoFinal.fechaInicio

            val globalesElegidos = _predeterminadosGlobales.value
                .filter { it.id in idsPredSeleccionados }
                .filter { it.esPredeterminado && it.esGlobal() && !it.eliminado }

            val copias = globalesElegidos.map { g ->
                g.copy(
                    id = UUID.randomUUID().toString(),
                    hojaId = periodoFinal.id,
                    fecha = baseFecha, // dentro del rango
                    eliminado = false,
                    esPredeterminado = true
                )
            }

            if (copias.isNotEmpty()) gastoRepository.insertarGastosLista(copias)

            _hojaId.value = periodoFinal.id
            _fechaInicioPeriodo.value = periodoFinal.fechaInicio
            _fechaFinPeriodo.value = periodoFinal.fechaFin
            _diasSeleccionados.value = periodoFinal.diasIncluidos
            _ingresoTotal.value = ingreso

            onFinish(periodoFinal)
        }
    }

    // ---------------------------
    // Predeterminados globales CRUD
    // ---------------------------
    fun refrescarPredeterminadosGlobales() {
        viewModelScope.launch {
            val eliminadosIds = getPredsEliminadosIds()

            _predeterminadosGlobales.value = gastoRepository.obtenerGastosPredeterminadosGlobales()
                .filter { !it.eliminado }
                .filter { it.esPredeterminado && it.esGlobal() }
                .filter { it.id !in eliminadosIds }
                .sortedBy { it.descripcion.lowercase() }
        }
    }

    fun agregarPredeterminadoGlobal(descripcion: String, monto: Double, porcentaje: Boolean) {
        viewModelScope.launch {
            val desc = descripcion.trim()
            if (desc.isBlank() || monto <= 0.0) return@launch

            val existe = _predeterminadosGlobales.value.any {
                it.descripcion.equals(desc, ignoreCase = true) && it.porcentaje == porcentaje && !it.eliminado
            }
            if (existe) return@launch

            // GLOBAL: hojaId = "" (o null). Tu DAO acepta ambos.
            val g = Gasto(
                id = UUID.randomUUID().toString(),
                descripcion = desc,
                monto = monto,
                porcentaje = porcentaje,
                fecha = 0L,
                esPredeterminado = true,
                hojaId = "",
                eliminado = false
            )

            gastoRepository.insertarGastos(g)
            refrescarPredeterminadosGlobales()
        }
    }

    fun actualizarPredeterminadoGlobal(gasto: Gasto) {
        viewModelScope.launch {
            val desc = gasto.descripcion.trim()
            if (desc.isBlank() || gasto.monto <= 0.0) return@launch

            gastoRepository.actualizarGasto(
                gasto.copy(descripcion = desc, esPredeterminado = true, hojaId = "")
            )
            refrescarPredeterminadosGlobales()
        }
    }

    fun eliminarPredeterminadoGlobal(gasto: Gasto) {
        viewModelScope.launch {
            addPredEliminadoId(gasto.id)
            gastoRepository.eliminarGastoFisico(gasto)
            refrescarPredeterminadosGlobales()
        }
    }

    // ---------------------------
    // ✅ Gastos (hoja) + INGRESOS del día
    // Regla: ingreso = monto NEGATIVO
    // ---------------------------
    fun agregarGasto(gasto: Gasto) {
        viewModelScope.launch {
            val desc = gasto.descripcion.trim()
            if (desc.isBlank()) return@launch
            if (gasto.monto == 0.0) return@launch

            // ✅ primero intento usar el hojaId que venga en el objeto (si no viene vacío)
            val hojaFinal = gasto.hojaId?.takeIf { it.isNotBlank() }
                ?: _hojaId.value.takeIf { it.isNotBlank() }

            // ❗ Si sigue null -> NO hay hoja seleccionada realmente
            if (hojaFinal == null) {
                // pon Log.e si quieres: "No hoja seleccionada"
                return@launch
            }

            gastoRepository.insertarGastos(
                gasto.copy(
                    id = gasto.id.ifBlank { UUID.randomUUID().toString() },
                    descripcion = desc,
                    hojaId = hojaFinal,
                    eliminado = false
                )
            )
        }
    }

    fun actualizarGasto(gasto: Gasto) {
        viewModelScope.launch {
            gastoRepository.actualizarGasto(gasto.copy(descripcion = gasto.descripcion.trim()))
        }
    }

    fun eliminarGasto(gasto: Gasto) {
        viewModelScope.launch {
            if (gasto.esPredeterminado && gasto.esGlobal()) {
                eliminarPredeterminadoGlobal(gasto)
            } else {
                gastoRepository.eliminarGastoFisico(gasto)
            }
        }
    }

    // ---------------------------
    // Totales por día
    // ---------------------------
    fun eliminarGastosDeDia(fecha: Long) {
        viewModelScope.launch {
            val hoja = _hojaId.value
            if (hoja.isBlank()) return@launch

            val inicioDia = obtenerInicioDeDia(fecha)
            val finDia = obtenerFinDeDia(fecha)

            val gastosDelDia = gastoRepository
                .obtenerGastosPorHojaYPeriodoSuspend(hoja, inicioDia, finDia)
                .filter { !it.eliminado && !it.esPredeterminado }

            gastosDelDia.forEach { gastoRepository.eliminarGastoFisico(it) }
        }
    }

    // ---------------------------
    // prefs helpers
    // ---------------------------
    private fun getPredsEliminadosIds(): Set<String> =
        sharedPrefs.getStringSet(PREDS_ELIMINADOS_IDS_KEY, emptySet()) ?: emptySet()

    private fun addPredEliminadoId(id: String) {
        val set = getPredsEliminadosIds().toMutableSet()
        set.add(id)
        sharedPrefs.edit { putStringSet(PREDS_ELIMINADOS_IDS_KEY, set) }
    }

    private fun obtenerInicioDeDia(fecha: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = fecha
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun obtenerFinDeDia(fecha: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = fecha
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

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
                return GastoViewModel(
                    gastoRepository, sharedPrefs, hojaId, ingresoExterno, periodoSeleccionadoFlow
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    private data class Params(val hoja: String, val inicio: Long, val fin: Long, val dias: List<Int>)

    private fun Gasto.esGlobal(): Boolean = hojaId.isNullOrBlank() || hojaId == ""

    private fun Gasto.montoReal(ingreso: Double): Double =
        if (porcentaje) ingreso * (monto / 100.0) else monto
}