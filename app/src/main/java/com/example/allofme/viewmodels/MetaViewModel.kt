package com.example.allofme.viewmodels

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.allofme.data.models.Meta
import com.example.allofme.data.repository.MetaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

class MetaViewModel(private val repository: MetaRepository) : ViewModel() {

    private val _metas = MutableStateFlow<List<Meta>>(emptyList())

    private var ultimaFechaDiaria = mutableStateOf<Long>(0L)
    private var ultimaSemana = mutableStateOf<Pair<Int, Int>>(0 to 0) // semana, año
    private var ultimoMes = mutableStateOf<Pair<Int, Int>>(0 to 0) // mes, año



    private val _fechaHoy = MutableStateFlow(Date())
    val fechaHoy: StateFlow<Date> = _fechaHoy.asStateFlow()

    private val _metasHoy = MutableStateFlow<List<Meta>>(emptyList())
    val metasHoy: StateFlow<List<Meta>> = _metasHoy.asStateFlow()

    private val _metasSemana = MutableStateFlow<List<Meta>>(emptyList())
    val metasSemana: StateFlow<List<Meta>> = _metasSemana.asStateFlow()

    private val _metasMes = MutableStateFlow<List<Meta>>(emptyList())
    val metasMes: StateFlow<List<Meta>> = _metasMes.asStateFlow()

    init {
        Log.d("MetaViewModel", "Inicializando ViewModel...")
        actualizarMetasSegunFecha()
    }

    fun agregarMeta(meta: Meta) = viewModelScope.launch {
        Log.d("MetaViewModel", "Agregando meta: $meta")
        repository.agregarMeta(meta)
        actualizarMetasSegunFecha()
    }

    fun actualizarMeta(meta: Meta) = viewModelScope.launch {
        Log.d("MetaViewModel", "Actualizando meta: $meta")
        repository.actualizarMeta(meta)
        actualizarMetasSegunFecha()
    }

    fun eliminarMeta(meta: Meta) = viewModelScope.launch {
        Log.d("MetaViewModel", "Eliminando meta: $meta")
        repository.eliminarMeta(meta)
        actualizarMetasSegunFecha()
    }



    /** ==== FILTROS ==== */
    private fun filtrarMetasHoy(): List<Meta> {
        val hoy = Date()
        val diaSemana = diaSemanaNombre(hoy)
        val diaMes = diaMesNumero(hoy)

        // Filtrar tareas según el día actual
        val metasMensualesEspecificas = _metas.value.filter { meta ->
            meta.tipoFrecuencia == "Mes" && !meta.todoElMes && meta.diaMes == diaMes
        }
        val metasDiarias = _metas.value.filter { meta ->
            meta.tipoFrecuencia == "Hoy"
        }
        val metasSemanales = _metas.value.filter { meta ->
            meta.tipoFrecuencia == "Semana" && meta.diasSemana?.contains(diaSemana) == true
        }
        val metasMensualesGenerales = _metas.value.filter { meta ->
            meta.tipoFrecuencia == "Mes" && meta.todoElMes
        }

        // Ordenar: Mensuales específicas primero, luego diarias, semanales, y mensuales generales
        val filtradas = metasMensualesEspecificas + metasDiarias + metasSemanales + metasMensualesGenerales

        Log.d("MetaViewModel", "Filtrando metas para HOY ($diaSemana $diaMes): ${filtradas.size} encontradas")
        Log.d("MetaViewModel", "Desglose: Mensuales específicas=${metasMensualesEspecificas.size}, Diarias=${metasDiarias.size}, Semanales=${metasSemanales.size}, Mensuales generales=${metasMensualesGenerales.size}")
        return filtradas
    }

    private fun filtrarMetasSemana(): List<Meta> {
        val filtradas = _metas.value.filter { meta -> meta.tipoFrecuencia == "Semana" }
        Log.d("MetaViewModel", "Filtrando metas para SEMANA: ${filtradas.size} encontradas")
        return filtradas
    }

    private fun filtrarMetasMes(): List<Meta> {
        val filtradas = _metas.value.filter { meta -> meta.tipoFrecuencia == "Mes" }
        Log.d("MetaViewModel", "Filtrando metas para MES: ${filtradas.size} encontradas")
        return filtradas
    }

    /** ==== Actualización automática de metas según fecha ==== */
    fun actualizarMetasSegunFecha() = viewModelScope.launch {
        Log.d("MetaViewModel", "Actualizando metas según fecha...")
        repository.obtenerMetas().collect { todas ->
            Log.d("MetaViewModel", "Metas obtenidas del repositorio: ${todas.size}")

            val hoy = Calendar.getInstance()

            val metasActualizadas = todas.map { meta ->
                when (meta.tipoFrecuencia) {
                    "Hoy" -> if (meta.esPredeterminada) {
                        Log.d("MetaViewModel", "Meta predeterminada encontrada para HOY: $meta")
                        meta.copy(fechaInicio = hoy.timeInMillis)
                    } else meta
                    "Semana" -> meta
                    "Mes" -> meta
                    else -> meta
                }
            }

            Log.d("MetaViewModel", "Metas después de procesar frecuencia: ${metasActualizadas.size}")
            _metas.value = metasActualizadas

            _metasHoy.value = filtrarMetasHoy()
            _metasSemana.value = filtrarMetasSemana()
            _metasMes.value = filtrarMetasMes()

            Log.d("MetaViewModel", "Metas listas -> Hoy: ${_metasHoy.value.size}, Semana: ${_metasSemana.value.size}, Mes: ${_metasMes.value.size}")
        }
    }

    /** ==== Utilitarias ==== */
    private fun diaSemanaNombre(fecha: Date): String {
        val formato = java.text.SimpleDateFormat("EEEE", Locale("es", "ES"))
        val nombre = formato.format(fecha).replaceFirstChar { it.uppercase() }
        Log.d("MetaViewModel", "Día de la semana obtenido: $nombre")
        return nombre
    }

    private fun diaMesNumero(fecha: Date): Int {
        val cal = Calendar.getInstance().apply { time = fecha }
        val dia = cal.get(Calendar.DAY_OF_MONTH)
        Log.d("MetaViewModel", "Día del mes obtenido: $dia")
        return dia
    }

    fun rangoSemanaActual(): Pair<Date, Date> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        val inicio = cal.time
        cal.add(Calendar.DAY_OF_WEEK, 6)
        val fin = cal.time
        Log.d("MetaViewModel", "Rango de semana actual: $inicio a $fin")
        return inicio to fin
    }


}

class MetaViewModelFactory(private val repository: MetaRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MetaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MetaViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}