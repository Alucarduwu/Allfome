package com.example.allofme.screens.finanzas

import android.app.DatePickerDialog
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.allofme.data.models.Gasto
import com.example.allofme.data.models.Periodo
import com.example.allofme.ui.theme.FondoPremium
import com.example.allofme.ui.theme.GrisTexto
import com.example.allofme.ui.theme.LilaPremium
import com.example.allofme.ui.theme.LilaSuave
import com.example.allofme.viewmodels.GastoViewModel
import com.example.allofme.viewmodels.PeriodoViewModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

private val Blanco = Color.White
private val LilaPastel = Color(0xFFF3EEFF)
private val RosaPastel = Color(0xFFFFEEF4)
private val GrisBorde = Color(0xFFE6E6EA)
private val TextoNegro = Color(0xFF111111)
private val GrisCard = Color(0xFFF8F7FB)

private val LavandaTop = Color(0xFFB39DDB)
private val LilaTop = Color(0xFF7E57C2)
private val MoradoAccento = Color(0xFF6A5ACD)
private val MoradoAccentoSoft = Color(0xFFEDE7FF)

private enum class FinanzasPage { MAIN, HOJAS, PREDS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanzasScreen(
    gastoViewModel: GastoViewModel,
    periodoViewModel: PeriodoViewModel
) {
    // ---------- state ----------
    val gastosRaw by gastoViewModel.gastos.collectAsState()
    val predGlobales by gastoViewModel.predeterminadosGlobales.collectAsState()
    val hojaActual by gastoViewModel.hojaId.collectAsState()
    val periodoSeleccionado by periodoViewModel.periodoSeleccionado.collectAsState()

    val ingresoPeriodo = periodoSeleccionado?.ingreso ?: 0.0
    val fechaInicio = periodoSeleccionado?.fechaInicio ?: 0L
    val fechaFin = periodoSeleccionado?.fechaFin ?: 0L

    // ✅ Movimientos de la hoja actual
    val movimientos = remember(gastosRaw, hojaActual) {
        gastosRaw
            .filter { !it.eliminado }
            .filter { it.hojaId == hojaActual }
            .sortedBy { it.fecha }
    }

    // ✅ Ingresos extra (guardados como monto negativo)
    val ingresosExtra = remember(movimientos, ingresoPeriodo) {
        movimientos.sumOf { m ->
            val real = m.montoReal(ingresoPeriodo)
            if (real < 0) abs(real) else 0.0
        }
    }
    val gastosPositivos = remember(movimientos, ingresoPeriodo) {
        movimientos.sumOf { m ->
            val real = m.montoReal(ingresoPeriodo)
            if (real > 0) real else 0.0
        }
    }

    // ✅ Disponible = ingreso base + ingresos extra - gastos
    val ingresoTotalDisponible = ingresoPeriodo + ingresosExtra
    val restante = ingresoTotalDisponible - gastosPositivos
    val progreso = if (ingresoTotalDisponible > 0) (gastosPositivos / ingresoTotalDisponible).toFloat() else 0f

    // Día seleccionado y lista de días con movimientos
    var diaSeleccionado by rememberSaveable { mutableStateOf<Int?>(null) }
    val diasConMovimientos by remember(movimientos, fechaInicio, fechaFin) {
        derivedStateOf {
            if (fechaInicio <= 0L || fechaFin <= 0L) emptyList()
            else movimientos
                .filter { it.fecha in fechaInicio..fechaFin }
                .map { Calendar.getInstance().apply { timeInMillis = it.fecha }.get(Calendar.DAY_OF_MONTH) }
                .distinct()
                .sorted()
        }
    }

    LaunchedEffect(diasConMovimientos) {
        if (diaSeleccionado == null || !diasConMovimientos.contains(diaSeleccionado)) {
            diaSeleccionado = diasConMovimientos.firstOrNull()
        }
    }

    // Movimientos del día seleccionado
    val movimientosDelDia = remember(movimientos, diaSeleccionado, fechaInicio) {
        diaSeleccionado?.let { dia ->
            val inicioDia = Calendar.getInstance().apply {
                timeInMillis = fechaInicio
                set(Calendar.DAY_OF_MONTH, dia)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val finDia = Calendar.getInstance().apply {
                timeInMillis = inicioDia
                add(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MILLISECOND, -1)
            }.timeInMillis

            movimientos.filter { it.fecha in inicioDia..finDia }
        } ?: emptyList()
    }

    // --------- navegación interna ----------
    var page by rememberSaveable { mutableStateOf(FinanzasPage.MAIN) }

    // dialogs
    var agregarGastoVisible by rememberSaveable { mutableStateOf(false) }
    var agregarIngresoVisible by rememberSaveable { mutableStateOf(false) }
    var confirmarLimpiarVisible by rememberSaveable { mutableStateOf(false) }
    var gastoEditando by remember { mutableStateOf<Gasto?>(null) }

    // refresca globales al entrar a pantallas
    LaunchedEffect(page) {
        if (page == FinanzasPage.PREDS || page == FinanzasPage.HOJAS) {
            gastoViewModel.refrescarPredeterminadosGlobales()
        }
    }

    val topTitle = when (page) {
        FinanzasPage.MAIN -> "Finanzas"
        FinanzasPage.HOJAS -> "Hojas"
        FinanzasPage.PREDS -> "Predeterminados"
    }

    Scaffold(
        containerColor = FondoPremium,
        topBar = {
            val gradient = Brush.linearGradient(
                colors = listOf(LilaTop, LavandaTop),
                start = Offset(0f, 0f),
                end = Offset(1200f, 0f)
            )

            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Blanco,
                    navigationIconContentColor = Blanco,
                    actionIconContentColor = Blanco
                ),
                modifier = Modifier.drawBehind { drawRect(brush = gradient) },
                title = { Text(topTitle, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (page != FinanzasPage.MAIN) {
                        IconButton(onClick = { page = FinanzasPage.MAIN }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
                        }
                    }
                },
                actions = {
                    if (page == FinanzasPage.MAIN) {
                        IconButton(onClick = { page = FinanzasPage.HOJAS }) {
                            Icon(Icons.Default.List, contentDescription = "Hojas")
                        }
                        IconButton(onClick = { page = FinanzasPage.PREDS }) {
                            Icon(Icons.Default.FavoriteBorder, contentDescription = "Predeterminados")
                        }
                    }
                }
            )
        }
    ) { padding ->

        when (page) {
            FinanzasPage.MAIN -> FinanzasMainPage(
                modifier = Modifier.padding(padding),
                periodoSeleccionado = periodoSeleccionado,
                fechaInicio = fechaInicio,
                fechaFin = fechaFin,
                ingresoPeriodo = ingresoPeriodo,
                ingresoTotalDisponible = ingresoTotalDisponible,
                ingresosExtra = ingresosExtra,
                gastosPositivos = gastosPositivos,
                restante = restante,
                progreso = progreso,
                movimientos = movimientos,
                movimientosDelDia = movimientosDelDia,
                diaSeleccionado = diaSeleccionado,
                onDiaSeleccionado = { diaSeleccionado = it },
                onEditar = { gastoEditando = it },
                onEliminar = { gastoViewModel.eliminarGasto(it) },
                onAgregarGasto = { agregarGastoVisible = true },
                onAgregarIngreso = { agregarIngresoVisible = true },
                onLimpiarDia = { confirmarLimpiarVisible = true },
                onIngresoPeriodoChange = { nuevoIngreso ->
                    periodoSeleccionado?.id?.let { periodoId ->
                        periodoViewModel.setIngresoPeriodo(periodoId, nuevoIngreso)
                    }
                }
            )

            FinanzasPage.HOJAS -> HojasPage(
                modifier = Modifier.padding(padding),
                periodoViewModel = periodoViewModel,
                gastoViewModel = gastoViewModel,
                predGlobales = predGlobales,
                onBack = { page = FinanzasPage.MAIN }
            )

            FinanzasPage.PREDS -> PredeterminadosPage(
                modifier = Modifier.padding(padding),
                predGlobales = predGlobales,
                onAgregar = { desc, monto, porcentaje ->
                    gastoViewModel.agregarPredeterminadoGlobal(desc, monto, porcentaje)
                },
                onActualizar = { gastoViewModel.actualizarPredeterminadoGlobal(it) },
                onEliminar = { gastoViewModel.eliminarPredeterminadoGlobal(it) },
                onBack = { page = FinanzasPage.MAIN }
            )
        }
    }

    // ---------------- dialogs MAIN ----------------
    if (confirmarLimpiarVisible && diaSeleccionado != null && fechaInicio > 0L) {
        ConfirmarLimpiarDialog(
            dia = diaSeleccionado!!,
            onConfirmar = {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = fechaInicio
                    set(Calendar.DAY_OF_MONTH, diaSeleccionado!!)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                gastoViewModel.eliminarGastosDeDia(cal.timeInMillis)
                confirmarLimpiarVisible = false
            },
            onCancelar = { confirmarLimpiarVisible = false }
        )
    }

    gastoEditando?.let { gasto ->
        EditarMovimientoDialog(
            gasto = gasto,
            ingresoPeriodo = ingresoPeriodo,
            onConfirmar = {
                gastoViewModel.actualizarGasto(it)
                gastoEditando = null
            },
            onCancelar = { gastoEditando = null }
        )
    }

    if ((agregarGastoVisible || agregarIngresoVisible) && diaSeleccionado != null && fechaInicio > 0L) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = fechaInicio
            set(Calendar.DAY_OF_MONTH, diaSeleccionado!!)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (agregarGastoVisible) {
            AgregarMovimientoDialog(
                titulo = "Agregar gasto • día ${diaSeleccionado!!}",
                icon = Icons.Default.Add,
                colorPrimario = MoradoAccento,
                fechaInicioMillis = cal.timeInMillis,
                esIngreso = false,
                onAgregar = {
                    // ✅ VM setea hojaId real cuando viene null
                    gastoViewModel.agregarGasto(it.copy(hojaId = null))
                    agregarGastoVisible = false
                },
                onCancelar = { agregarGastoVisible = false }
            )
        }

        if (agregarIngresoVisible) {
            AgregarMovimientoDialog(
                titulo = "Agregar ingreso • día ${diaSeleccionado!!}",
                icon = Icons.Default.Add,
                colorPrimario = LilaPremium,
                fechaInicioMillis = cal.timeInMillis,
                esIngreso = true,
                onAgregar = {
                    // ✅ ingreso = monto negativo
                    gastoViewModel.agregarGasto(it.copy(hojaId = null))
                    agregarIngresoVisible = false
                },
                onCancelar = { agregarIngresoVisible = false }
            )
        }
    }
}

/* ====================== MAIN PAGE ====================== */

@Composable
private fun FinanzasMainPage(
    modifier: Modifier,
    periodoSeleccionado: Periodo?,
    fechaInicio: Long,
    fechaFin: Long,
    ingresoPeriodo: Double,
    ingresoTotalDisponible: Double,
    ingresosExtra: Double,
    gastosPositivos: Double,
    restante: Double,
    progreso: Float,
    movimientos: List<Gasto>,
    movimientosDelDia: List<Gasto>,
    diaSeleccionado: Int?,
    onDiaSeleccionado: (Int) -> Unit,
    onEditar: (Gasto) -> Unit,
    onEliminar: (Gasto) -> Unit,
    onAgregarGasto: () -> Unit,
    onAgregarIngreso: () -> Unit,
    onLimpiarDia: () -> Unit,
    onIngresoPeriodoChange: (Double) -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(FondoPremium)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { PeriodoHeader(periodoSeleccionado, fechaInicio, fechaFin) }

        item {
            ProgresoFinanciero(
                progreso = progreso,
                restante = restante
            )
            Spacer(Modifier.height(10.dp))
            ResumenFinancieroCards(
                ingresoBase = ingresoPeriodo,
                ingresosExtra = ingresosExtra,
                gastos = gastosPositivos,
                restante = restante
            )
        }

        item {
            IngresoPeriodoField(
                ingresoInicial = ingresoPeriodo,
                enabled = periodoSeleccionado != null,
                onIngresoChange = onIngresoPeriodoChange
            )
        }

        item {
            DisponibleExplainCard(
                ingresoBase = ingresoPeriodo,
                ingresosExtra = ingresosExtra,
                ingresoTotalDisponible = ingresoTotalDisponible
            )
        }

        item {
            Text(
                "Resumen por día",
                style = MaterialTheme.typography.titleMedium,
                color = TextoNegro
            )
            GastosPorDiaTabla(
                fechaInicio = fechaInicio,
                fechaFin = fechaFin,
                movimientos = movimientos,
                diaSeleccionado = diaSeleccionado,
                onDiaSeleccionado = onDiaSeleccionado,
                ingresoPeriodo = ingresoPeriodo
            )
        }

        item {
            MovimientosDelDiaCard(
                diaSeleccionado = diaSeleccionado,
                movimientosDelDia = movimientosDelDia,
                ingresoPeriodo = ingresoPeriodo,
                onEditar = onEditar,
                onEliminar = onEliminar
            )
        }

        item {
            AccionesDiaRow(
                enabled = diaSeleccionado != null,
                onAgregarGasto = onAgregarGasto,
                onAgregarIngreso = onAgregarIngreso,
                onLimpiar = onLimpiarDia
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/* ====================== HOJAS PAGE ====================== */

@Composable
private fun HojasPage(
    modifier: Modifier,
    periodoViewModel: PeriodoViewModel,
    gastoViewModel: GastoViewModel,
    predGlobales: List<Gasto>,
    onBack: () -> Unit,
    onSeleccionarPeriodo: (Periodo) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val periodos by periodoViewModel.periodos.collectAsState()

    var nombre by rememberSaveable { mutableStateOf("") }
    var ingresoTxt by rememberSaveable { mutableStateOf("") }
    var fechaInicioMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var fechaFinMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var showInicioPicker by rememberSaveable { mutableStateOf(false) }
    var showFinPicker by rememberSaveable { mutableStateOf(false) }

    val seleccion = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(predGlobales) {
        predGlobales
            .filter { !it.eliminado }
            .forEach { g ->
                if (!seleccion.containsKey(g.id)) seleccion[g.id] = false
            }
    }

    val listState = rememberLazyListState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(FondoPremium)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        state = listState
    ) {
        item {
            SectionHeader(
                title = "Tus hojas",
                subtitle = "Selecciona una o crea una nueva",
                badgeText = (periodos.size).toString()
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Blanco),
                border = BorderStroke(1.dp, GrisBorde),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (periodos.isEmpty()) {
                    EmptyState(
                        title = "Aún no tienes hojas",
                        subtitle = "Crea tu primera hoja abajo para empezar ✨"
                    )
                } else {
                    Column(Modifier.padding(12.dp)) {
                        periodos.sortedByDescending { it.fechaInicio }.forEach { p ->
                            PeriodoRowPro(
                                periodo = p,
                                onSeleccionar = {
                                    scope.launch {
                                        periodoViewModel.seleccionarPeriodo(p)
                                        gastoViewModel.setHojaId(p.id)
                                        gastoViewModel.setCustomPeriodoRange(p.fechaInicio, p.fechaFin)
                                        gastoViewModel.setDiasSeleccionados(p.diasIncluidos)
                                        onBack()
                                        onSeleccionarPeriodo(p)
                                    }
                                },
                                onEliminar = { scope.launch { periodoViewModel.eliminarPeriodo(p) } }
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Crear nueva hoja",
                subtitle = "Elige qué predeterminados se copian a esta hoja"
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MoradoAccentoSoft.copy(alpha = 0.55f)),
                border = BorderStroke(1.dp, GrisBorde),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {

                    ProOutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        label = "Nombre de la hoja",
                        placeholder = "Ej. Marzo 2026",
                        keyboardType = KeyboardType.Text
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { showInicioPicker = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, GrisBorde),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextoNegro)
                        ) { Text("Inicio: ${fechaInicioMillis.formatoFecha("dd/MM/yyyy")}") }

                        OutlinedButton(
                            onClick = { showFinPicker = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, GrisBorde),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextoNegro)
                        ) { Text("Fin: ${fechaFinMillis.formatoFecha("dd/MM/yyyy")}") }
                    }

                    Spacer(Modifier.height(10.dp))

                    ProOutlinedTextField(
                        value = ingresoTxt,
                        onValueChange = { ingresoTxt = it.filter { c -> c.isDigit() || c == '.' } },
                        label = "Ingreso del periodo",
                        placeholder = "Ej. 18000",
                        keyboardType = KeyboardType.Number
                    )

                    Spacer(Modifier.height(14.dp))

                    Text("Incluir predeterminados", style = MaterialTheme.typography.titleSmall, color = TextoNegro)
                    Text("Se copian como snapshot a la hoja.", color = GrisTexto)

                    Spacer(Modifier.height(10.dp))

                    val listaPred = predGlobales.filter { !it.eliminado }.sortedBy { it.descripcion.lowercase() }
                    if (listaPred.isEmpty()) {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = Blanco),
                            border = BorderStroke(1.dp, GrisBorde),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            EmptyState(
                                title = "No tienes predeterminados aún",
                                subtitle = "Ve a Predeterminados y crea tus gastos base."
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            listaPred.forEach { g ->
                                PredCheckboxRowPro(
                                    gasto = g,
                                    checked = seleccion[g.id] == true,
                                    onCheckedChange = { seleccion[g.id] = it }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    val ingreso = ingresoTxt.toDoubleOrNull() ?: 0.0
                    val canSave = nombre.isNotBlank() && ingreso > 0 && fechaInicioMillis > 0L

                    Button(
                        enabled = canSave,
                        onClick = {
                            val (inicioOk, finOk) = normalizarFechas(fechaInicioMillis, fechaFinMillis)
                            val idsSeleccionados = seleccion.filterValues { it }.keys.toList()

                            val periodoNuevo = Periodo(
                                id = "",
                                nombre = nombre.trim(),
                                fechaInicio = inicioOk,
                                fechaFin = finOk,
                                diasIncluidos = emptyList(),
                                ingreso = ingreso
                            )

                            periodoViewModel.guardarPeriodo(periodoNuevo) { periodoGuardado ->
                                scope.launch {
                                    gastoViewModel.crearHojaConPredeterminados(
                                        periodo = periodoGuardado,
                                        ingreso = periodoGuardado.ingreso,
                                        idsPredSeleccionados = idsSeleccionados
                                    )

                                    periodoViewModel.seleccionarPeriodo(periodoGuardado)
                                    gastoViewModel.setHojaId(periodoGuardado.id)
                                    gastoViewModel.setCustomPeriodoRange(periodoGuardado.fechaInicio, periodoGuardado.fechaFin)
                                    gastoViewModel.setDiasSeleccionados(periodoGuardado.diasIncluidos)

                                    nombre = ""
                                    ingresoTxt = ""
                                    seleccion.keys.forEach { seleccion[it] = false }

                                    onBack()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MoradoAccento, contentColor = Blanco)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Crear hoja")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showInicioPicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = fechaInicioMillis }
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val c = Calendar.getInstance().apply {
                    set(y, m, d, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                fechaInicioMillis = c.timeInMillis
                showInicioPicker = false
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    if (showFinPicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = fechaFinMillis }
        DatePickerDialog(
            context,
            { _, y, m, d ->
                val c = Calendar.getInstance().apply {
                    set(y, m, d, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                fechaFinMillis = c.timeInMillis
                showFinPicker = false
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}

/* ====================== PREDS PAGE ====================== */

@Composable
private fun PredeterminadosPage(
    modifier: Modifier,
    predGlobales: List<Gasto>,
    onAgregar: (String, Double, Boolean) -> Unit,
    onActualizar: (Gasto) -> Unit,
    onEliminar: (Gasto) -> Unit,
    onBack: () -> Unit
) {
    var descripcionNueva by rememberSaveable { mutableStateOf("") }
    var montoNuevo by rememberSaveable { mutableStateOf("") }
    var esPorcentajeNuevo by rememberSaveable { mutableStateOf(false) }
    var gastoAEliminar by remember { mutableStateOf<Gasto?>(null) }

    val lista = remember(predGlobales) {
        predGlobales.filter { !it.eliminado }.sortedBy { it.descripcion.lowercase() }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(FondoPremium)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SectionHeader(
                title = "Predeterminados",
                subtitle = "Tu plantilla base (se copia como snapshot a las hojas)",
                badgeText = lista.size.toString()
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = LilaPastel),
                border = BorderStroke(1.dp, GrisBorde),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    ProOutlinedTextField(
                        value = descripcionNueva,
                        onValueChange = { descripcionNueva = it },
                        label = "Descripción",
                        placeholder = "Ej. Renta, Gym, Spotify…",
                        keyboardType = KeyboardType.Text
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProOutlinedTextField(
                            value = montoNuevo,
                            onValueChange = { txt ->
                                if (txt.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) montoNuevo = txt
                            },
                            label = "Monto",
                            placeholder = "0.00",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )

                        AssistChip(
                            onClick = { esPorcentajeNuevo = !esPorcentajeNuevo },
                            label = { Text(if (esPorcentajeNuevo) "% Sí" else "% No") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (esPorcentajeNuevo) MoradoAccentoSoft else GrisCard,
                                labelColor = if (esPorcentajeNuevo) MoradoAccento else GrisTexto
                            ),
                            border = BorderStroke(1.dp, GrisBorde)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    val montoD = montoNuevo.toDoubleOrNull()
                    val habilitado = descripcionNueva.isNotBlank() && montoD != null && montoD > 0.0

                    Button(
                        enabled = habilitado,
                        onClick = {
                            onAgregar(descripcionNueva.trim(), montoD!!, esPorcentajeNuevo)
                            descripcionNueva = ""
                            montoNuevo = ""
                            esPorcentajeNuevo = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MoradoAccento, contentColor = Blanco)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Agregar predeterminado")
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Lista", subtitle = "Edita y guarda cambios cuando quieras")
        }

        if (lista.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Blanco),
                    border = BorderStroke(1.dp, GrisBorde),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EmptyState(
                        title = "Sin predeterminados",
                        subtitle = "Crea tu primera plantilla arriba ✨"
                    )
                }
            }
        } else {
            items(lista.size) { idx ->
                val g = lista[idx]
                PredItemEditablePro(
                    gasto = g,
                    onGuardar = onActualizar,
                    onEliminar = { gastoAEliminar = g }
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (gastoAEliminar != null) {
        AlertDialog(
            onDismissRequest = { gastoAEliminar = null },
            title = { Text("Eliminar predeterminado") },
            text = { Text("¿Seguro que quieres eliminar '${gastoAEliminar?.descripcion}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        gastoAEliminar?.let(onEliminar)
                        gastoAEliminar = null
                    }
                ) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { gastoAEliminar = null }) { Text("Cancelar") } }
        )
    }
}

/* ====================== UI COMPONENTS PRO ====================== */

@Composable
private fun SectionHeader(title: String, subtitle: String, badgeText: String? = null) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Blanco),
        border = BorderStroke(1.dp, GrisBorde),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = TextoNegro)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = GrisTexto)
            }

            if (badgeText != null) {
                AssistChip(
                    onClick = {},
                    label = { Text(badgeText) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MoradoAccentoSoft,
                        labelColor = MoradoAccento
                    ),
                    border = BorderStroke(1.dp, MoradoAccento.copy(alpha = 0.25f))
                )
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextoNegro)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = GrisTexto)
    }
}

@Composable
private fun ProOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = GrisTexto) },
        placeholder = { Text(placeholder, color = GrisTexto.copy(alpha = 0.75f)) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MoradoAccento,
            unfocusedBorderColor = GrisBorde,
            focusedLabelColor = MoradoAccento,
            unfocusedLabelColor = GrisTexto,
            cursorColor = MoradoAccento,
            focusedTextColor = TextoNegro,
            unfocusedTextColor = TextoNegro,
            disabledTextColor = GrisTexto,
            focusedContainerColor = Blanco,
            unfocusedContainerColor = Blanco
        )
    )
}

@Composable
private fun PeriodoRowPro(
    periodo: Periodo,
    onSeleccionar: () -> Unit,
    onEliminar: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = GrisCard),
        border = BorderStroke(1.dp, GrisBorde),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSeleccionar() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(periodo.nombre, style = MaterialTheme.typography.titleMedium, color = TextoNegro)
                Spacer(Modifier.height(6.dp))

                Text(
                    "${periodo.fechaInicio.formatoFecha("dd/MM/yyyy")} — ${periodo.fechaFin.formatoFecha("dd/MM/yyyy")}",
                    color = GrisTexto,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Ingreso base: ${periodo.ingreso.money()}",
                    color = MoradoAccento,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            FilledTonalIconButton(
                onClick = onEliminar,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar")
            }
        }
    }
}

@Composable
private fun PredCheckboxRowPro(
    gasto: Gasto,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Blanco),
        border = BorderStroke(1.dp, GrisBorde),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(gasto.descripcion, style = MaterialTheme.typography.bodyLarge, color = TextoNegro)
                Spacer(Modifier.height(4.dp))
                val txt = if (gasto.porcentaje) "${gasto.monto}% del ingreso" else gasto.monto.money()
                Text(txt, style = MaterialTheme.typography.bodySmall, color = GrisTexto)
            }

            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun PredItemEditablePro(
    gasto: Gasto,
    onGuardar: (Gasto) -> Unit,
    onEliminar: () -> Unit
) {
    var descripcion by rememberSaveable(gasto.id) { mutableStateOf(gasto.descripcion) }
    var monto by rememberSaveable(gasto.id) { mutableStateOf(gasto.monto.toString()) }
    var porcentaje by rememberSaveable(gasto.id) { mutableStateOf(gasto.porcentaje) }

    val montoD = monto.toDoubleOrNull()
    val hayCambios = descripcion.trim() != gasto.descripcion ||
            (montoD ?: -1.0) != gasto.monto ||
            porcentaje != gasto.porcentaje

    val puedeGuardar = hayCambios && descripcion.isNotBlank() && montoD != null && montoD > 0.0

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Blanco),
        border = BorderStroke(1.dp, GrisBorde),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {

            ProOutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = "Descripción",
                placeholder = "Ej. Renta",
                keyboardType = KeyboardType.Text
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProOutlinedTextField(
                    value = monto,
                    onValueChange = { txt ->
                        if (txt.matches(Regex("^\\d*(\\.\\d{0,2})?$"))) monto = txt
                    },
                    label = "Monto",
                    placeholder = "0.00",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )

                AssistChip(
                    onClick = { porcentaje = !porcentaje },
                    label = { Text(if (porcentaje) "% Sí" else "% No") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (porcentaje) MoradoAccentoSoft else GrisCard,
                        labelColor = if (porcentaje) MoradoAccento else GrisTexto
                    ),
                    border = BorderStroke(1.dp, GrisBorde)
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    enabled = puedeGuardar,
                    onClick = {
                        val md = montoD ?: return@Button
                        onGuardar(
                            gasto.copy(
                                descripcion = descripcion.trim(),
                                monto = md,
                                porcentaje = porcentaje,
                                esPredeterminado = true,
                                hojaId = "" // ✅ global consistente con tu VM
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (puedeGuardar) MoradoAccento else MoradoAccento.copy(alpha = 0.35f),
                        contentColor = Blanco
                    )
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Guardar cambios")
                }

                FilledTonalIconButton(
                    onClick = onEliminar,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                }
            }
        }
    }
}

/* ====================== MAIN UI PIECES ====================== */

@Composable
private fun PeriodoHeader(periodo: Periodo?, fechaInicio: Long, fechaFin: Long) {
    val texto = if (periodo == null || fechaInicio <= 0L || fechaFin <= 0L) {
        "Selecciona una hoja para comenzar ✨"
    } else {
        "Periodo: ${periodo.nombre} • ${fechaInicio.formatoFecha("dd/MM/yyyy")} - ${fechaFin.formatoFecha("dd/MM/yyyy")}"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Blanco),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, GrisBorde)
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(14.dp),
            color = TextoNegro
        )
    }
}

@Composable
private fun DisponibleExplainCard(ingresoBase: Double, ingresosExtra: Double, ingresoTotalDisponible: Double) {
    if (ingresoBase <= 0.0 && ingresosExtra <= 0.0) return

    Card(
        colors = CardDefaults.cardColors(containerColor = Blanco),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, GrisBorde)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Disponible del periodo", style = MaterialTheme.typography.titleSmall, color = TextoNegro)
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Ingreso base", color = GrisTexto)
                Text(ingresoBase.money(), color = TextoNegro)
            }
            if (ingresosExtra > 0) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ingresos extra", color = GrisTexto)
                    Text("+ ${ingresosExtra.money()}", color = TextoNegro)
                }
            }
            Spacer(Modifier.height(10.dp))
            Divider(color = GrisBorde)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total disponible", color = GrisTexto)
                Text(ingresoTotalDisponible.money(), color = MoradoAccento, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun IngresoPeriodoField(
    ingresoInicial: Double,
    enabled: Boolean,
    onIngresoChange: (Double) -> Unit
) {
    var ingresoTexto by rememberSaveable(enabled) {
        mutableStateOf(if (enabled) ingresoInicial.toString() else "")
    }

    LaunchedEffect(ingresoInicial, enabled) {
        if (enabled) ingresoTexto = ingresoInicial.toString()
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Blanco),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, GrisBorde)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Ingreso base del periodo", style = MaterialTheme.typography.titleSmall, color = TextoNegro)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = ingresoTexto,
                onValueChange = { txt ->
                    ingresoTexto = txt.filter { it.isDigit() || it == '.' }
                    ingresoTexto.toDoubleOrNull()?.let(onIngresoChange)
                },
                enabled = enabled,
                label = { Text("Ej. 18000", color = GrisTexto) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MoradoAccento,
                    unfocusedBorderColor = GrisBorde,
                    focusedLabelColor = MoradoAccento,
                    unfocusedLabelColor = GrisTexto,
                    cursorColor = MoradoAccento,
                    focusedTextColor = TextoNegro,
                    unfocusedTextColor = TextoNegro,
                    focusedContainerColor = Blanco,
                    unfocusedContainerColor = Blanco
                )
            )
        }
    }
}

/* ---------------------- Movimientos del día ---------------------- */

@Composable
private fun MovimientosDelDiaCard(
    diaSeleccionado: Int?,
    movimientosDelDia: List<Gasto>,
    ingresoPeriodo: Double,
    onEditar: (Gasto) -> Unit,
    onEliminar: (Gasto) -> Unit
) {
    if (diaSeleccionado == null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Blanco),
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(0.dp),
            border = BorderStroke(1.dp, GrisBorde)
        ) {
            Text(
                "No hay días con movimientos todavía. Agrega uno 💖",
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = GrisTexto
            )
        }
        return
    }

    val ingresos = remember(movimientosDelDia, ingresoPeriodo) {
        movimientosDelDia.filter { it.montoReal(ingresoPeriodo) < 0 }
    }
    val gastos = remember(movimientosDelDia, ingresoPeriodo) {
        movimientosDelDia.filter { it.montoReal(ingresoPeriodo) >= 0 }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Blanco),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, GrisBorde)
    ) {
        Column(Modifier.padding(14.dp)) {

            Text(
                "Movimientos del día $diaSeleccionado",
                style = MaterialTheme.typography.titleMedium,
                color = TextoNegro
            )
            Spacer(Modifier.height(10.dp))

            if (movimientosDelDia.isEmpty()) {
                Text("No hay movimientos registrados para este día.", style = MaterialTheme.typography.bodyMedium, color = GrisTexto)
                return@Column
            }

            if (ingresos.isNotEmpty()) {
                Text("Ingresos", color = LilaPremium, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                ingresos.forEach { mov ->
                    MovimientoRowPro(
                        gasto = mov,
                        ingresoPeriodo = ingresoPeriodo,
                        accent = LilaPremium,
                        showPositive = true,
                        onEditar = { onEditar(mov) },
                        onEliminar = { onEliminar(mov) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            if (gastos.isNotEmpty()) {
                if (ingresos.isNotEmpty()) {
                    Divider(color = GrisBorde)
                    Spacer(Modifier.height(10.dp))
                }
                Text("Gastos", color = MoradoAccento, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                gastos.forEach { mov ->
                    MovimientoRowPro(
                        gasto = mov,
                        ingresoPeriodo = ingresoPeriodo,
                        accent = MoradoAccento,
                        showPositive = false,
                        onEditar = { onEditar(mov) },
                        onEliminar = { onEliminar(mov) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun MovimientoRowPro(
    gasto: Gasto,
    ingresoPeriodo: Double,
    accent: Color,
    showPositive: Boolean,
    onEditar: () -> Unit,
    onEliminar: () -> Unit
) {
    val montoReal = gasto.montoReal(ingresoPeriodo)
    val display = if (showPositive) abs(montoReal) else montoReal

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GrisCard),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.20f)),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditar() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(gasto.descripcion, style = MaterialTheme.typography.bodyLarge, color = TextoNegro)
                Text(
                    DateFormat.getDateInstance().format(Date(gasto.fecha)),
                    style = MaterialTheme.typography.bodySmall,
                    color = GrisTexto
                )
            }

            Text(
                (if (showPositive) "+ " else "") + display.money(),
                style = MaterialTheme.typography.bodyLarge,
                color = accent
            )

            IconButton(onClick = onEditar) {
                Icon(Icons.Default.Edit, contentDescription = "Editar", tint = TextoNegro)
            }
            IconButton(onClick = onEliminar) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/* ---------------------- Acciones del día ---------------------- */

@Composable
private fun AccionesDiaRow(
    enabled: Boolean,
    onAgregarGasto: () -> Unit,
    onAgregarIngreso: () -> Unit,
    onLimpiar: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onAgregarGasto,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (enabled) MoradoAccento else MoradoAccento.copy(alpha = 0.35f),
                contentColor = Blanco
            )
        ) { Text("Agregar gasto") }

        OutlinedButton(
            onClick = onAgregarIngreso,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, LilaPremium.copy(alpha = 0.8f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = LilaPremium)
        ) { Text("Agregar ingreso") }

        OutlinedButton(
            onClick = onLimpiar,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) { Text("Limpiar") }
    }
}

@Composable
private fun ConfirmarLimpiarDialog(
    dia: Int,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Confirmar eliminación") },
        text = { Text("¿Seguro que deseas eliminar todos los gastos del día $dia?") },
        confirmButton = {
            Button(
                onClick = onConfirmar,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Sí, eliminar", color = Blanco) }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

/* ---------------------- Tabla por día (neto) ---------------------- */

@Composable
fun GastosPorDiaTabla(
    fechaInicio: Long,
    fechaFin: Long,
    movimientos: List<Gasto>,
    diaSeleccionado: Int?,
    onDiaSeleccionado: (Int) -> Unit,
    ingresoPeriodo: Double
) {
    val dias = remember(fechaInicio, fechaFin) {
        if (fechaInicio <= 0L || fechaFin <= 0L || fechaFin < fechaInicio) emptyList()
        else buildList {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = fechaInicio
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            while (calendar.timeInMillis <= fechaFin) {
                add(calendar.timeInMillis)
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
    }

    val totalesPorDia = remember(movimientos, ingresoPeriodo, dias) {
        dias.associateWith { diaMillis ->
            movimientos
                .filter { isSameDay(it.fecha, diaMillis) }
                .sumOf { it.montoReal(ingresoPeriodo) }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Blanco),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, GrisBorde)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Día", style = MaterialTheme.typography.labelLarge, color = GrisTexto)
                Text("Neto", style = MaterialTheme.typography.labelLarge, color = GrisTexto)
            }
            Spacer(Modifier.height(10.dp))

            if (dias.isEmpty()) {
                Text("Selecciona un periodo para ver el resumen por día.", color = GrisTexto)
                return@Column
            }

            dias.forEachIndexed { index, diaMillis ->
                val total = totalesPorDia[diaMillis] ?: 0.0
                val diaMes = Calendar.getInstance().apply { timeInMillis = diaMillis }.get(Calendar.DAY_OF_MONTH)
                val selected = diaSeleccionado == diaMes

                val bg = when {
                    selected -> MoradoAccentoSoft.copy(alpha = 0.55f)
                    index % 2 == 0 -> LilaPastel.copy(alpha = 0.45f)
                    else -> GrisCard
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bg, RoundedCornerShape(16.dp))
                        .clickable { onDiaSeleccionado(diaMes) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        DateFormat.getDateInstance().format(Date(diaMillis)),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextoNegro
                    )

                    val color = if (total < 0) LilaPremium else MoradoAccento
                    Text(
                        (if (total < 0) "- " else "") + abs(total).money(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                }

                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/* ---------------------- Dialogs (Agregar/Editar Movimiento) ---------------------- */

@Composable
private fun EditarMovimientoDialog(
    gasto: Gasto,
    ingresoPeriodo: Double,
    onConfirmar: (Gasto) -> Unit,
    onCancelar: () -> Unit
) {
    val esIngreso = remember(gasto, ingresoPeriodo) { gasto.montoReal(ingresoPeriodo) < 0 }

    var descripcion by rememberSaveable(gasto.id) { mutableStateOf(gasto.descripcion) }
    var montoTxt by rememberSaveable(gasto.id) { mutableStateOf(abs(gasto.monto).toString()) }
    var esPorcentaje by rememberSaveable(gasto.id) { mutableStateOf(gasto.porcentaje) }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (esIngreso) "Editar ingreso" else "Editar gasto") },
        text = {
            Column {
                ProOutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = "Descripción",
                    placeholder = "Ej. Nómina, Uber, Comida…",
                    keyboardType = KeyboardType.Text
                )
                Spacer(Modifier.height(10.dp))

                ProOutlinedTextField(
                    value = montoTxt,
                    onValueChange = { montoTxt = it.filter { c -> c.isDigit() || c == '.' } },
                    label = "Monto",
                    placeholder = "0.00",
                    keyboardType = KeyboardType.Number
                )

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = esPorcentaje && !esIngreso,
                        onCheckedChange = { if (!esIngreso) esPorcentaje = it },
                        enabled = !esIngreso
                    )
                    Text(
                        "Es porcentaje",
                        color = if (!esIngreso) GrisTexto else GrisTexto.copy(alpha = 0.5f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val montoD = montoTxt.toDoubleOrNull() ?: abs(gasto.monto)
                    val montoFinal = if (esIngreso) montoD else -montoD

                    onConfirmar(
                        gasto.copy(
                            descripcion = descripcion.trim(),
                            monto = montoFinal,
                            porcentaje = if (esIngreso) false else esPorcentaje
                        )
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MoradoAccento, contentColor = Blanco)
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

@Composable
private fun AgregarMovimientoDialog(
    titulo: String,
    icon: ImageVector,
    colorPrimario: Color,
    fechaInicioMillis: Long,
    esIngreso: Boolean,
    onAgregar: (Gasto) -> Unit,
    onCancelar: () -> Unit
) {
    var descripcion by rememberSaveable { mutableStateOf("") }
    var monto by rememberSaveable { mutableStateOf("") }

    val montoDouble = monto.toDoubleOrNull() ?: 0.0
    val isValid = descripcion.isNotBlank() && montoDouble > 0.0

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo) },
        text = {
            Column {
                ProOutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = "Descripción",
                    placeholder = if (esIngreso) "Ej. Venta, Nómina, Reembolso…" else "Ej. Comida, Uber, Renta…",
                    keyboardType = KeyboardType.Text
                )
                Spacer(Modifier.height(10.dp))
                ProOutlinedTextField(
                    value = monto,
                    onValueChange = { monto = it.filter { c -> c.isDigit() || c == '.' } },
                    label = "Monto",
                    placeholder = "0.00",
                    keyboardType = KeyboardType.Number
                )
                Spacer(Modifier.height(8.dp))
                if (esIngreso) {
                    Text("Tip: los ingresos del día aumentan tu disponible del periodo.", color = GrisTexto)
                } else {
                    Text("Tip: si un gasto es porcentaje, actívalo al editar.", color = GrisTexto)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid) {
                        val montoFinal = if (esIngreso) -montoDouble else montoDouble
                        onAgregar(
                            Gasto(
                                id = UUID.randomUUID().toString(),
                                descripcion = descripcion.trim(),
                                monto = montoFinal,
                                fecha = fechaInicioMillis,
                                esPredeterminado = false,
                                porcentaje = false,
                                hojaId = null,
                                eliminado = false
                            )
                        )
                    }
                },
                enabled = isValid,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorPrimario, contentColor = Blanco)
            ) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Agregar")
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

/* ---------------------- Progress + resumen ---------------------- */

@Composable
fun ProgresoFinanciero(progreso: Float, restante: Double) {
    val animated by animateFloatAsState(
        targetValue = progreso.coerceIn(0f, 1f),
        animationSpec = tween(900),
        label = "progress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Blanco),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, GrisBorde)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(10.dp)
        ) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(170.dp),
                color = MoradoAccentoSoft,
                strokeWidth = 16.dp
            )
            CircularProgressIndicator(
                progress = { animated },
                modifier = Modifier.size(170.dp),
                color = MoradoAccento,
                strokeWidth = 16.dp
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Disponible", style = MaterialTheme.typography.labelMedium, color = GrisTexto)
                Text(restante.money(), style = MaterialTheme.typography.headlineMedium, color = TextoNegro)
            }
        }
    }
}

@Composable
fun ResumenFinancieroCards(
    ingresoBase: Double,
    ingresosExtra: Double,
    gastos: Double,
    restante: Double
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ResumenCard("Ingreso base", ingresoBase, LilaPastel, MoradoAccento, Modifier.weight(1f))
            ResumenCard("Ingresos extra", ingresosExtra, MoradoAccentoSoft, LilaPremium, Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ResumenCard("Gastos", gastos, RosaPastel, MoradoAccento, Modifier.weight(1f))
            ResumenCard("Restante", restante, Blanco, TextoNegro, Modifier.weight(1f), outlined = true)
        }
    }
}

@Composable
fun ResumenCard(
    titulo: String,
    valor: Double,
    fondo: Color,
    colorPrincipal: Color,
    modifier: Modifier = Modifier,
    outlined: Boolean = false
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = fondo),
        border = if (outlined) BorderStroke(1.dp, GrisBorde) else BorderStroke(1.dp, GrisBorde),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(titulo, style = MaterialTheme.typography.labelMedium, color = GrisTexto)
            Spacer(Modifier.height(6.dp))
            Text(valor.money(), style = MaterialTheme.typography.titleLarge, color = colorPrincipal)
        }
    }
}

/* ====================== Helpers ====================== */

private fun normalizarFechas(inicio: Long, fin: Long): Pair<Long, Long> =
    if (fin < inicio) fin to inicio else inicio to fin

private fun Gasto.montoReal(ingreso: Double): Double =
    if (porcentaje) ingreso * (monto / 100.0) else monto

private fun Double.money(): String =
    "$" + String.format(Locale.getDefault(), "%,.2f", this)

fun Long.formatoFecha(formato: String = "dd/MM/yyyy HH:mm"): String {
    val sdf = SimpleDateFormat(formato, Locale.getDefault())
    return sdf.format(Date(this))
}

fun isSameDay(time1: Long, time2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = time1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = time2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}