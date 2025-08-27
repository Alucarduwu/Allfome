    package com.example.allofme.viewmodels

    import android.content.SharedPreferences
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

    class MetaViewModel(
        private val repository: MetaRepository,
        private val sharedPrefs: SharedPreferences
    ) : ViewModel() {

        private val _metas = MutableStateFlow<List<Meta>>(emptyList())


        private var ultimaFechaDiaria = mutableStateOf(
            sharedPrefs.getLong("ultimaFechaDiaria", 0L)
        )
        private var ultimaSemana = mutableStateOf(
            Pair(
                sharedPrefs.getInt("ultimaSemana", 0),
                sharedPrefs.getInt("ultimoAnioSemana", 0)
            )
        )
        private var ultimoMes = mutableStateOf(
            Pair(
                sharedPrefs.getInt("ultimoMes", 0),
                sharedPrefs.getInt("ultimoAnioMes", 0)
            )
        )

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
            viewModelScope.launch {
                repository.obtenerMetas().collect { todas ->
                    Log.d("MetaViewModel", "Recolectando metas del repositorio: ${todas.size}")
                    _metas.value = todas
                    actualizarMetasSegunFecha()
                }
            }
        }

        fun agregarMeta(meta: Meta) = viewModelScope.launch {
            Log.d("MetaViewModel", "Agregando meta: ${meta.descripcion}")
            repository.agregarMeta(meta)
            _metas.value = _metas.value + meta
            actualizarEstadosFiltrados()
        }

        fun actualizarMeta(meta: Meta) = viewModelScope.launch {
            Log.d("MetaViewModel", "Actualizando meta: ${meta.descripcion}, completado=${meta.completado}")
            repository.actualizarMeta(meta)
            _metas.value = _metas.value.map { if (it.id == meta.id) meta else it }
            actualizarEstadosFiltrados()
        }

        fun eliminarMeta(meta: Meta) = viewModelScope.launch {
            Log.d("MetaViewModel", "Eliminando meta: ${meta.descripcion}")
            repository.eliminarMeta(meta)
            _metas.value = _metas.value.filter { it.id != meta.id }
            actualizarEstadosFiltrados()
        }

        /** ==== Verificación de cambio de período ==== */
        private fun haCambiadoPeriodo(tipoFrecuencia: String, hoy: Calendar): Boolean {
            return when (tipoFrecuencia) {
                "Hoy" -> {
                    val ultima = Calendar.getInstance().apply { timeInMillis = ultimaFechaDiaria.value }
                    val diaActual = hoy.get(Calendar.DAY_OF_YEAR)
                    val anioActual = hoy.get(Calendar.YEAR)
                    val diaUltimo = ultima.get(Calendar.DAY_OF_YEAR)
                    val anioUltimo = ultima.get(Calendar.YEAR)
                    val cambiado = diaActual != diaUltimo || anioActual != anioUltimo
                    Log.d("MetaViewModel", "Cambio de día detectado: $cambiado (Hoy: $diaActual/$anioActual, Último: $diaUltimo/$anioUltimo)")
                    cambiado
                }
                "Semana" -> {
                    val semanaActual = hoy.get(Calendar.WEEK_OF_YEAR)
                    val anioActual = hoy.get(Calendar.YEAR)
                    val (semanaUltima, anioUltimo) = ultimaSemana.value
                    val cambiado = semanaActual != semanaUltima || anioActual != anioUltimo
                    Log.d("MetaViewModel", "Cambio de semana detectado: $cambiado (Semana: $semanaActual/$anioActual, Última: $semanaUltima/$anioUltimo)")
                    cambiado
                }
                "Mes" -> {
                    val mesActual = hoy.get(Calendar.MONTH)
                    val anioActual = hoy.get(Calendar.YEAR)
                    val (mesUltimo, anioUltimo) = ultimoMes.value
                    val cambiado = mesActual != mesUltimo || anioActual != anioUltimo
                    Log.d("MetaViewModel", "Cambio de mes detectado: $cambiado (Mes: $mesActual/$anioActual, Último: $mesUltimo/$anioUltimo)")
                    cambiado
                }
                else -> {
                    Log.d("MetaViewModel", "Frecuencia desconocida: $tipoFrecuencia")
                    false
                }
            }
        }

        /** ==== FILTROS ==== */
        private fun filtrarMetasHoy(): List<Meta> {
            val hoy = Date()
            val diaSemana = diaSemanaNombre(hoy)
            val diaMes = diaMesNumero(hoy)

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
            val hoy = Calendar.getInstance()
            val metasActualizadas = _metas.value.map { meta ->
                val haCambiado = haCambiadoPeriodo(meta.tipoFrecuencia, hoy)
                when (meta.tipoFrecuencia) {
                    "Hoy" -> {
                        if (haCambiado) {
                            Log.d("MetaViewModel", "Reseteando meta diaria: ${meta.descripcion} (ID: ${meta.id})")
                            meta.copy(completado = false)
                        } else {
                            meta
                        }
                    }
                    "Semana" -> {
                        if (haCambiado) {
                            Log.d("MetaViewModel", "Reseteando meta semanal: ${meta.descripcion} (ID: ${meta.id})")
                            meta.copy(completado = false)
                        } else {
                            meta
                        }
                    }
                    "Mes" -> {
                        if (haCambiado) {
                            Log.d("MetaViewModel", "Reseteando meta mensual: ${meta.descripcion} (ID: ${meta.id})")
                            meta.copy(completado = false)
                        } else {
                            meta
                        }
                    }
                    else -> meta
                }
            }

            // Actualizar en el repositorio si hay cambios
            metasActualizadas.forEach { meta ->
                val original = _metas.value.find { it.id == meta.id }
                if (original != null && meta.completado != original.completado) {
                    Log.d("MetaViewModel", "Persistiendo meta actualizada: ${meta.descripcion} (ID: ${meta.id}, Completado: ${meta.completado})")
                    repository.actualizarMeta(meta)
                }
            }

            _metas.value = metasActualizadas
            actualizarEstadosFiltrados()

            // Guardar los nuevos valores en SharedPreferences
            with(sharedPrefs.edit()) {
                putLong("ultimaFechaDiaria", hoy.timeInMillis)
                putInt("ultimaSemana", hoy.get(Calendar.WEEK_OF_YEAR))
                putInt("ultimoAnioSemana", hoy.get(Calendar.YEAR))
                putInt("ultimoMes", hoy.get(Calendar.MONTH))
                putInt("ultimoAnioMes", hoy.get(Calendar.YEAR))
                apply()
            }
            Log.d("MetaViewModel", "Fechas guardadas en SharedPreferences: Día=${hoy.time}, Semana=${hoy.get(Calendar.WEEK_OF_YEAR)}/${hoy.get(Calendar.YEAR)}, Mes=${hoy.get(Calendar.MONTH)}/${hoy.get(Calendar.YEAR)}")
        }

        private fun actualizarEstadosFiltrados() {
            _metasHoy.value = filtrarMetasHoy()
            _metasSemana.value = filtrarMetasSemana()
            _metasMes.value = filtrarMetasMes()
            Log.d("MetaViewModel", "Metas listas -> Hoy: ${_metasHoy.value.size}, Semana: ${_metasSemana.value.size}, Mes: ${_metasMes.value.size}")
            Log.d("MetaViewModel", "Metas diarias: ${_metasHoy.value.filter { it.tipoFrecuencia == "Hoy" }.joinToString { "${it.descripcion} (Completado: ${it.completado})" }}")
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

        /** ==== Funciones para notificaciones ==== */
        fun getPendingDailyTasks(): List<Meta> {
            val pendientes = _metasHoy.value.filter { !it.completado }
            Log.d("MetaViewModel", "Tareas diarias pendientes: ${pendientes.size} (${pendientes.joinToString { it.descripcion.toString() }})")
            return pendientes
        }

        fun getPendingWeeklyTasks(): List<Meta> {
            val pendientes = _metasSemana.value.filter { !it.completado }
            Log.d("MetaViewModel", "Tareas semanales pendientes: ${pendientes.size} (${pendientes.joinToString { it.descripcion.toString() }})")
            return pendientes
        }

        fun getPendingMonthlyTasks(): List<Meta> {
            val pendientes = _metasMes.value.filter { !it.completado }
            Log.d("MetaViewModel", "Tareas mensuales pendientes: ${pendientes.size} (${pendientes.joinToString { it.descripcion.toString() }})")
            return pendientes
        }




    }

    class MetaViewModelFactory(
        private val repository: MetaRepository,
        private val sharedPrefs: SharedPreferences
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MetaViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MetaViewModel(repository, sharedPrefs) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }