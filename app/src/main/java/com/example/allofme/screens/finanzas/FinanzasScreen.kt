
package com.example.allofme.screens.finanzas


import android.app.DatePickerDialog
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.allofme.data.models.Gasto
import com.example.allofme.data.models.Periodo
import com.example.allofme.viewmodels.GastoViewModel

import com.example.allofme.viewmodels.PeriodoViewModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

private val VerdePastel = Color.Black
private val LilaPastel = Color(0xFFE1BEE7)
private val RosaPastel = Color(0xFFF8BBD0)
private val Blanco = Color.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanzasScreen(
    gastoViewModel: GastoViewModel,
    periodoViewModel: PeriodoViewModel
) {
    val gastos by gastoViewModel.gastos.collectAsState()
    val hojaActual by gastoViewModel.hojaId.collectAsState()

    LaunchedEffect(gastos) {
        gastos.forEach { gasto ->
            Log.d(
                "FinanzasScreen",
                "Gasto cargado: desc='${gasto.descripcion}', monto=${gasto.monto}, fecha=${Date(gasto.fecha)}, predeterminado=${gasto.esPredeterminado}"
            )
        }
    }

    val totalGastos by gastoViewModel.totalGastosMonto.collectAsState()
    val restante by gastoViewModel.totalRestante.collectAsState()
    val periodoSeleccionado by periodoViewModel.periodoSeleccionado.collectAsState()

    val ingresoPeriodo = periodoSeleccionado?.ingreso ?: 0.0
    val fechaInicioLocal = periodoSeleccionado?.fechaInicio ?: 0L
    val fechaFinLocal = periodoSeleccionado?.fechaFin ?: 0L

    // Mapa reactivo para guardar totales por día
    val totalesPorDia = remember { mutableStateMapOf<Long, Double>() }

    var diasSeleccionados by remember { mutableStateOf<List<Int>>(emptyList()) }
    var diaSeleccionado by remember { mutableStateOf<Int?>(null) }

    var gastoEditando by remember { mutableStateOf<Gasto?>(null) }
    var agregarGastoVisible by remember { mutableStateOf(false) }
    var confirmarLimpiarVisible by remember { mutableStateOf(false) }
    var editarPredeterminadosVisible by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var historialVisible by remember { mutableStateOf(false) }


    // Actualiza la lista de días con gastos y selecciona el primero
    LaunchedEffect(gastos, fechaInicioLocal, fechaFinLocal) {
        val diasConGastos = gastos
            .filter { gasto ->
                gasto.hojaId == hojaActual && gasto.fecha in fechaInicioLocal..fechaFinLocal
            }
            .map { gasto ->
                Calendar.getInstance().apply { timeInMillis = gasto.fecha }
                    .get(Calendar.DAY_OF_MONTH)
            }
            .distinct()
            .sorted()

        if (diasConGastos != diasSeleccionados) {
            diasSeleccionados = diasConGastos
            diaSeleccionado = diasConGastos.firstOrNull()
        }
    }

    // Para el día seleccionado, obtener y actualizar total gastado por día usando la función asíncrona del ViewModel
    LaunchedEffect(diasSeleccionados, hojaActual) {
        diasSeleccionados.forEach { dia ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = fechaInicioLocal
                set(Calendar.DAY_OF_MONTH, dia)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            gastoViewModel.obtenerTotalGastosEnDia(cal.timeInMillis) { total ->
                totalesPorDia[cal.timeInMillis] = total
            }
        }
    }

    val gastosDelDiaSeleccionado = remember(gastos, diaSeleccionado, fechaInicioLocal) {
        diaSeleccionado?.let { dia ->
            val calInicio = Calendar.getInstance().apply {
                timeInMillis = fechaInicioLocal
                set(Calendar.DAY_OF_MONTH, dia)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val calFin = Calendar.getInstance().apply {
                timeInMillis = calInicio.timeInMillis
                add(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MILLISECOND, -1)
            }
            gastos.filter { gasto ->
                gasto.hojaId == hojaActual && gasto.fecha in calInicio.timeInMillis..calFin.timeInMillis
            }
        } ?: emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Finanzas") },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menú")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {

                        DropdownMenuItem(
                            text = { Text("Editar gastos predeterminados") },
                            onClick = {
                                editarPredeterminadosVisible = true
                                menuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Historial") },
                            onClick = {
                                historialVisible = true
                                menuExpanded = false
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Blanco)
                .padding(14.dp,16.dp,16.dp,0.dp)
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Periodo: ${periodoSeleccionado?.nombre ?: "Ninguno"} (${fechaInicioLocal.formatoFecha()} - ${fechaFinLocal.formatoFecha()})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            item {
                ProgresoFinanciero(
                    progreso = if (ingresoPeriodo > 0) (totalGastos / ingresoPeriodo).toFloat() else 0f,
                    restante = restante
                )
                ResumenFinancieroCards(ingresoPeriodo, totalGastos, restante)
            }
            item {
                val gastosPredeterminados =
                    gastos.filter { it.esPredeterminado && it.hojaId == hojaActual }
                if (gastosPredeterminados.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .background(Color(0xFFEDE7F6), shape = RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Gastos predeterminados en este periodo:",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(gastosPredeterminados) { gastoPred ->
                                val montoReal =
                                    if (gastoPred.porcentaje) ingresoPeriodo * gastoPred.monto / 100 else gastoPred.monto
                                val textoMonto = if (gastoPred.porcentaje) {
                                    "${gastoPred.monto}% ($${"%.2f".format(montoReal)})"
                                } else {
                                    "$${"%.2f".format(montoReal)}"
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = gastoPred.descripcion,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = textoMonto,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }

                            }
                        }
                    }
                }
            }

                item {
                var ingresoTexto by remember { mutableStateOf(ingresoPeriodo.toString()) }
                OutlinedTextField(
                    value = ingresoTexto,
                    onValueChange = {
                        ingresoTexto = it
                        it.toDoubleOrNull()?.let { nuevoIngreso ->
                            periodoSeleccionado?.id?.let { periodoId ->
                                periodoViewModel.setIngresoPeriodo(periodoId, nuevoIngreso)
                            }
                        }
                    },
                    label = { Text("Ingreso total del periodo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            item {
                Text("Resumen de días con gastos:", style = MaterialTheme.typography.titleMedium)
                GastosPorDiaTabla(
                    fechaInicio = fechaInicioLocal,
                    fechaFin = fechaFinLocal,
                    gastos = gastos,
                    diasSeleccionados = diasSeleccionados,
                    onDiaSeleccionado = { diaSeleccionado = it },
                    colorFondo = Color(0xFFEDE7F6),
                    ingresoPeriodo = ingresoPeriodo
                )
            }
            item {
                if (diaSeleccionado != null) {
                    Text(
                        "Gastos del día $diaSeleccionado",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = LilaPastel),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        if (gastosDelDiaSeleccionado.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No hay gastos registrados para este día.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(gastosDelDiaSeleccionado) { gasto ->
                                    // Calculamos el monto real: si es porcentaje de un gasto predeterminado, usamos ingresoPeriodo
                                    val montoReal = if (gasto.porcentaje && gasto.esPredeterminado) ingresoPeriodo * gasto.monto / 100 else gasto.monto

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { gastoEditando = gasto },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
                                        elevation = CardDefaults.cardElevation(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    gasto.descripcion,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                                Text(
                                                    text = DateFormat.getDateInstance().format(Date(gasto.fecha)),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.Gray
                                                )
                                            }
                                            Text(
                                                text = "$${"%.2f".format(montoReal)}", // <-- mostramos el monto real
                                                style = MaterialTheme.typography.bodyLarge,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                            IconButton(onClick = { gastoEditando = gasto }) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Editar gasto"
                                                )
                                            }
                                            IconButton(onClick = {
                                                gastoViewModel.eliminarGasto(gasto)
                                            }) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Eliminar gasto",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { if (diaSeleccionado != null) agregarGastoVisible = true },
                        enabled = diaSeleccionado != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (diaSeleccionado != null) Color(0xFF4A148C) else Color(0xFFD1C4E9)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Agregar gasto", color = Color.White)
                    }
                    Button(
                        onClick = { if (diaSeleccionado != null) confirmarLimpiarVisible = true },
                        enabled = diaSeleccionado != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (diaSeleccionado != null) Color(0xFF4A148C) else Color(0xFFD1C4E9)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Limpiar gastos", color = Color.White)
                    }
                }
            }
        }
    }

    // Diálogos y modales

    if (confirmarLimpiarVisible && diaSeleccionado != null) {
        AlertDialog(
            onDismissRequest = { confirmarLimpiarVisible = false },
            title = { Text("Confirmar eliminación") },
            text = { Text("¿Seguro que deseas eliminar todos los gastos del día $diaSeleccionado?") },
            confirmButton = {
                Button(onClick = {
                    val dia = diaSeleccionado!!
                    val cal = Calendar.getInstance().apply { timeInMillis = fechaInicioLocal }
                    cal.set(Calendar.DAY_OF_MONTH, dia)
                    val fechaDelDia = cal.timeInMillis
                    gastoViewModel.eliminarGastosDeDia(fechaDelDia)
                    confirmarLimpiarVisible = false
                }) {
                    Text("Sí, eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmarLimpiarVisible = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    gastoEditando?.let { gasto ->
        EditarGastoDialog(
            gasto = gasto,
            onConfirmar = { nuevoGasto ->
                gastoViewModel.actualizarGasto(nuevoGasto)
                gastoEditando = null
            },
            onCancelar = { gastoEditando = null }
        )
    }

    if (agregarGastoVisible && diaSeleccionado != null) {
        val dia = diaSeleccionado!!
        val cal = Calendar.getInstance().apply { timeInMillis = fechaInicioLocal }
        cal.set(Calendar.DAY_OF_MONTH, dia)
        AgregarGastoDialog(
            fechaInicioMillis = cal.timeInMillis,
            diaSeleccionado = dia,
            onAgregar = {
                gastoViewModel.agregarGasto(it)
                agregarGastoVisible = false
            },
            onCancelar = { agregarGastoVisible = false }
        )
    }

    if (editarPredeterminadosVisible) {
        EditarGastosPredeterminadosDialog(
            gastosPredeterminados = gastos.filter { it.esPredeterminado },
            onAgregar = { gastoNuevo -> gastoViewModel.agregarGastoPredeterminado(gastoNuevo) },
            onActualizar = { gastoEditado -> gastoViewModel.actualizarGasto(gastoEditado) },
            onEliminar = { gastoEliminar -> gastoViewModel.eliminarGasto(gastoEliminar) },
            onCerrar = { editarPredeterminadosVisible = false },
            fechaPrimerDiaHoja = periodoSeleccionado?.fechaInicio ?: System.currentTimeMillis()
        )
    }

    if (historialVisible) {
        HistorialDialog(
            periodoViewModel = periodoViewModel,
            gastoViewModel = gastoViewModel,
            onCerrar = { historialVisible = false }
        )
    }


}






@Composable
fun GastosPorDiaTabla(
    fechaInicio: Long,
    fechaFin: Long,
    gastos: List<Gasto>,
    diasSeleccionados: List<Int>,
    onDiaSeleccionado: (Int) -> Unit,
    colorFondo: Color,
    ingresoPeriodo: Double // <-- ingreso total del periodo para calcular %
) {
    val dias = remember(fechaInicio, fechaFin) {
        val calendar = Calendar.getInstance()
        val listaDias = mutableListOf<Long>()
        calendar.timeInMillis = fechaInicio
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        while (calendar.timeInMillis <= fechaFin) {
            listaDias.add(calendar.timeInMillis)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        listaDias
    }

    // Calculamos totales por día usando ingresoPeriodo
    val totalesPorDia = remember(gastos, ingresoPeriodo) {
        dias.associateWith { dia ->
            gastos.filter { isSameDay(it.fecha, dia) }.sumOf { gasto ->
                if (gasto.esPredeterminado && gasto.porcentaje) ingresoPeriodo * gasto.monto / 100
                else gasto.monto
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorFondo),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Día", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text("Total gastos", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            }

            dias.forEachIndexed { index, dia ->
                val total = totalesPorDia[dia] ?: 0.0
                val diaMes = Calendar.getInstance().apply { timeInMillis = dia }.get(Calendar.DAY_OF_MONTH)
                val estaSeleccionado = diasSeleccionados.contains(diaMes)

                val colorFila = when {
                    estaSeleccionado -> Color(0xFFBB86FC) // Morado seleccionado
                    (index % 2) == 0 -> RosaPastel.copy(alpha = 0.3f)
                    else -> LilaPastel.copy(alpha = 0.3f)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorFila)
                        .clickable { onDiaSeleccionado(diaMes) }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = DateFormat.getDateInstance().format(Date(dia)),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "$${"%.2f".format(total)}",
                        modifier = Modifier.weight(1f),
                        color = VerdePastel,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}






@Composable
fun HistorialDialog(
    periodoViewModel: PeriodoViewModel,
    gastoViewModel: GastoViewModel,
    onCerrar: () -> Unit
) {
    val context = LocalContext.current
    val periodos by periodoViewModel.periodos.collectAsState()
    val scope = rememberCoroutineScope() // CoroutineScope para llamadas suspend

    var nombreNuevoPeriodo by remember { mutableStateOf("") }
    var fechaInicioMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var fechaFinMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val diasSeleccionados = remember { mutableStateListOf<Int>() }
    var mostrandoCalendarioInicio by remember { mutableStateOf(false) }
    var mostrandoCalendarioFin by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    Dialog(onDismissRequest = onCerrar) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Hojas y periodos", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))

                // Lista scrollable con periodos
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(periodos.sortedBy { it.fechaInicio }) { p ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        periodoViewModel.seleccionarPeriodo(p)
                                        gastoViewModel.setCustomPeriodoRange(p.fechaInicio, p.fechaFin)
                                        gastoViewModel.setDiasSeleccionados(p.diasIncluidos)
                                        onCerrar()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(15.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(p.nombre, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "${formatearFechaMillis(p.fechaInicio)} — ${formatearFechaMillis(p.fechaFin)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                IconButton(
                                    onClick = { periodoViewModel.eliminarPeriodo(p) }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                                }
                            }
                        }
                    }
                }

                // Scroll automático al final
                LaunchedEffect(periodos) {
                    if (periodos.isNotEmpty()) listState.animateScrollToItem(periodos.size - 1)
                }

                Spacer(Modifier.height(8.dp))
                Text("Crear nueva hoja", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = nombreNuevoPeriodo,
                    onValueChange = { nombreNuevoPeriodo = it },
                    label = { Text("Nombre de la hoja") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { mostrandoCalendarioInicio = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFE0E0E0),
                        contentColor = Color.Black
                    )
                ) {
                    Text("Fecha inicio: ${formatearFechaMillis(fechaInicioMillis)}")
                }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { mostrandoCalendarioFin = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFE0E0E0),
                        contentColor = Color.Black
                    )
                ) {
                    Text("Fecha fin: ${formatearFechaMillis(fechaFinMillis)}")
                }

                Spacer(Modifier.height(8.dp))
                Text("Días incluidos:", style = MaterialTheme.typography.bodyLarge)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val totalDias = diasEntreMillis(fechaInicioMillis, fechaFinMillis)
                    for (i in 1..totalDias) {
                        val seleccionado = diasSeleccionados.contains(i)
                        AssistChip(
                            onClick = {
                                if (seleccionado) diasSeleccionados.remove(i) else diasSeleccionados.add(i)
                            },
                            label = { Text(i.toString()) },
                            colors = if (seleccionado) {
                                AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primary)
                            } else {
                                AssistChipDefaults.assistChipColors()
                            }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                val guardarHabilitado = nombreNuevoPeriodo.isNotBlank()
                Button(
                    onClick = {
                        scope.launch {
                            if (fechaFinMillis < fechaInicioMillis) {
                                val tmp = fechaInicioMillis
                                fechaInicioMillis = fechaFinMillis
                                fechaFinMillis = tmp
                            }
                            val nuevo = Periodo(
                                id = "",
                                nombre = nombreNuevoPeriodo.trim(),
                                fechaInicio = fechaInicioMillis,
                                fechaFin = fechaFinMillis,
                                diasIncluidos = diasSeleccionados.sorted()
                            )
                            periodoViewModel.guardarPeriodo(nuevo)
                            gastoViewModel.setCustomPeriodoRange(nuevo.fechaInicio, nuevo.fechaFin)
                            gastoViewModel.setDiasSeleccionados(nuevo.diasIncluidos)
                            periodoViewModel.seleccionarPeriodo(nuevo)
                            onCerrar()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (guardarHabilitado) Color(0xFF4A148C) else Color(0xFFD6D6D6),
                        contentColor = if (guardarHabilitado) Color.White else Color.Black
                    ),
                    enabled = guardarHabilitado
                ) {
                    Text("💾 Guardar hoja")
                }
            }
        }
    }

    // DatePickers
    if (mostrandoCalendarioInicio) {
        val cal = Calendar.getInstance().apply { timeInMillis = fechaInicioMillis }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val c = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                fechaInicioMillis = c.timeInMillis
                mostrandoCalendarioInicio = false
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    if (mostrandoCalendarioFin) {
        val cal = Calendar.getInstance().apply { timeInMillis = fechaFinMillis }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val c = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                fechaFinMillis = c.timeInMillis
                mostrandoCalendarioFin = false
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
}


/** Helpers (usar millis) **/
private fun formatearFechaMillis(millis: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return sdf.format(Date(millis))
}

private fun diasEntreMillis(inicioMillis: Long, finMillis: Long): Int {
    val start = Calendar.getInstance().apply {
        timeInMillis = inicioMillis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val end = Calendar.getInstance().apply {
        timeInMillis = finMillis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val diff = end - start
    val days = (diff / (1000L * 60 * 60 * 24)).toInt() + 1
    return days.coerceAtLeast(1)
}




fun Long.formatoFecha(formato: String = "dd/MM/yyyy HH:mm"): String {
    val sdf = SimpleDateFormat(formato, Locale.getDefault())
    val fecha = Date(this)
    return sdf.format(fecha)
}




data class GastoEdicionState(
    val descripcion: String,
    val monto: String,
    val porcentaje: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun EditarGastosPredeterminadosDialog(
    gastosPredeterminados: List<Gasto>,
    onAgregar: (Gasto) -> Unit,
    onActualizar: (Gasto) -> Unit,
    onEliminar: (Gasto) -> Unit,
    onCerrar: () -> Unit,
    fechaPrimerDiaHoja: Long
) {
    var descripcionNueva by remember { mutableStateOf("") }
    var montoNuevo by remember { mutableStateOf("") }
    var esPorcentajeNuevo by remember { mutableStateOf(false) }
    var gastoAEliminar by remember { mutableStateOf<Gasto?>(null) }

    val edicionMap = remember {
        mutableStateMapOf<String, MutableState<GastoEdicionState>>().apply {
            gastosPredeterminados.forEach { gasto ->
                put(
                    gasto.id,
                    mutableStateOf(
                        GastoEdicionState(
                            descripcion = gasto.descripcion,
                            monto = gasto.monto.toString(),
                            porcentaje = gasto.porcentaje
                        )
                    )
                )
            }
        }
    }

    Dialog(onDismissRequest = onCerrar) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.99f)
                .fillMaxHeight(0.99f)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Encabezado + botón cerrar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Gastos Predeterminados", style = MaterialTheme.typography.headlineLarge)
                    IconButton(onClick = onCerrar) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // FORMULARIO AGREGAR GASTO - fijo arriba
                OutlinedTextField(
                    value = descripcionNueva,
                    onValueChange = { descripcionNueva = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = montoNuevo,
                        onValueChange = { nuevoMonto ->
                            if (nuevoMonto.matches(Regex("^\\d*(\\.\\d{0,2})?\$"))) {
                                montoNuevo = nuevoMonto
                            }
                        },
                        label = { Text("Monto") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Checkbox(
                        checked = esPorcentajeNuevo,
                        onCheckedChange = { esPorcentajeNuevo = it }
                    )
                    Text("%", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(16.dp))

                // BOTÓN AGREGAR
                val agregarHabilitado = descripcionNueva.isNotBlank() && montoNuevo.toDoubleOrNull() != null
                Button(
                    enabled = agregarHabilitado,
                    onClick = {
                        onAgregar(
                            Gasto(
                                id = UUID.randomUUID().toString(),
                                descripcion = descripcionNueva.trim(),
                                monto = montoNuevo.toDouble(),
                                porcentaje = esPorcentajeNuevo,
                                esPredeterminado = true,
                                fecha = fechaPrimerDiaHoja,
                                hojaId = ""
                            )
                        )
                        descripcionNueva = ""
                        montoNuevo = ""
                        esPorcentajeNuevo = false
                    },
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (agregarHabilitado) Color(0xFF4A148C) else Color(0xFFD1C4E9)
                    )
                ) {
                    Text("Agregar", color = Color.White)
                }

                Spacer(Modifier.height(16.dp))

                // LISTA DE GASTOS PRED. - scrollable
                Text("Lista de gastos predeterminados", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(gastosPredeterminados) { gasto ->
                        val estadoEdicion = edicionMap[gasto.id] ?: return@items
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            elevation = CardDefaults.cardElevation(6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = estadoEdicion.value.descripcion,
                                        onValueChange = {
                                            estadoEdicion.value = estadoEdicion.value.copy(descripcion = it)
                                        },
                                        label = { Text("Descripción") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = estadoEdicion.value.monto,
                                            onValueChange = { nuevoMonto ->
                                                if (nuevoMonto.matches(Regex("^\\d*(\\.\\d{0,2})?\$"))) {
                                                    estadoEdicion.value = estadoEdicion.value.copy(monto = nuevoMonto)
                                                }
                                            },
                                            label = { Text("Monto") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier.width(120.dp),
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodyLarge,
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Checkbox(
                                            checked = estadoEdicion.value.porcentaje,
                                            onCheckedChange = {
                                                estadoEdicion.value = estadoEdicion.value.copy(porcentaje = it)
                                            }
                                        )
                                        Text("%", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }

                                Spacer(Modifier.width(8.dp))
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.height(72.dp)
                                ) {
                                    val hayCambios =
                                        gasto.descripcion != estadoEdicion.value.descripcion ||
                                                gasto.monto.toString() != estadoEdicion.value.monto ||
                                                gasto.porcentaje != estadoEdicion.value.porcentaje

                                    // BOTÓN GUARDAR
                                    val guardarHabilitado =
                                        hayCambios &&
                                                estadoEdicion.value.descripcion.isNotBlank() &&
                                                estadoEdicion.value.monto.toDoubleOrNull() != null

                                    Button(
                                        enabled = guardarHabilitado,
                                        onClick = {
                                            val montoDouble = estadoEdicion.value.monto.toDoubleOrNull() ?: return@Button
                                            onActualizar(
                                                gasto.copy(
                                                    descripcion = estadoEdicion.value.descripcion,
                                                    monto = montoDouble,
                                                    porcentaje = estadoEdicion.value.porcentaje
                                                )
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (guardarHabilitado) Color(0xFF4A148C) else Color(0xFFD1C4E9)
                                        )
                                    ) {
                                        Text("Guardar", color = Color.White)
                                    }

                                    // BOTÓN ELIMINAR
                                    IconButton(
                                        onClick = { gastoAEliminar = gasto },
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Eliminar",
                                            tint = Color.Red
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val scope = rememberCoroutineScope() // dentro del Composable

    if (gastoAEliminar != null) {
        AlertDialog(
            onDismissRequest = { gastoAEliminar = null },
            title = { Text("Confirmar eliminación") },
            text = { Text("¿Seguro que quieres eliminar '${gastoAEliminar?.descripcion}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        gastoAEliminar?.let { gasto ->
                            scope.launch {
                                onEliminar(gasto) // ahora sí puede llamar al suspend del ViewModel
                            }
                        }
                        gastoAEliminar = null
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { gastoAEliminar = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

}







// Función auxiliar para comparar fechas ignorando horas
fun isSameDay(time1: Long, time2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = time1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = time2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}




@Composable
fun ProgresoFinanciero(progreso: Float, restante: Double) {
    val animatedProgress by animateFloatAsState(
        targetValue = progreso,
        animationSpec = tween(durationMillis = 1200),
        label = "progresoAnimado"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(16.dp)
        ) {
            CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.size(180.dp),
            color = RosaPastel,
            strokeWidth = 20.dp,

            )

            CircularProgressIndicator(
            progress = { animatedProgress.coerceIn(0f, 1f) },
            modifier = Modifier.size(180.dp),
            color = LilaPastel,
            strokeWidth = 20.dp,

            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Restante",
                    style = MaterialTheme.typography.labelMedium.copy(color = Color.Gray)
                )
                Text(
                    text = "$${"%.2f".format(restante)}",
                    style = MaterialTheme.typography.headlineSmall.copy(color = Color.Black)
                )
            }
        }
    }
}

@Composable
fun ResumenFinancieroCards(ingreso: Double, totalGastos: Double, restante: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ResumenCard("Ingreso", ingreso, RosaPastel, modifier = Modifier.weight(1f))
        ResumenCard("Gastos", totalGastos, LilaPastel, modifier = Modifier.weight(1f))
        ResumenCard("Restante", restante, Color.White, outlined = true, modifier = Modifier.weight(1f))
    }
}

@Composable
fun ResumenCard(
    titulo: String,
    valor: Double,
    color: Color,
    outlined: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val elevation = if (outlined) 0.dp else 6.dp
    val border = if (outlined) BorderStroke(1.dp, Color.LightGray) else null

    Card(
        modifier = modifier
            .graphicsLayer {
                shadowElevation = 12.dp.toPx()
                clip = true
                this.shape = shape
            },
        shape = shape,
        border = border,
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.labelLarge.copy(color = Color.DarkGray)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$${"%.2f".format(valor)}",
                style = MaterialTheme.typography.titleLarge.copy(color = Color.Black)
            )
        }
    }
}


@Composable
fun EditarGastoDialog(
    gasto: Gasto,
    onConfirmar: (Gasto) -> Unit,
    onCancelar: () -> Unit
) {
    var descripcion by remember { mutableStateOf(gasto.descripcion) }
    var monto by remember { mutableStateOf(gasto.monto.toString()) }
    var esPorcentaje by remember { mutableStateOf(gasto.porcentaje) }

    AlertDialog(
        onDismissRequest = onCancelar,
        confirmButton = {
            Button(onClick = {
                val nuevoGasto = gasto.copy(
                    descripcion = descripcion,
                    monto = monto.toDoubleOrNull() ?: gasto.monto,
                    porcentaje = esPorcentaje
                )
                onConfirmar(nuevoGasto)
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        },
        title = { Text("Editar Gasto") },
        text = {
            Column {
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = monto,
                    onValueChange = { monto = it },
                    label = { Text("Monto") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = esPorcentaje, onCheckedChange = { esPorcentaje = it })
                    Text("Es porcentaje")
                }
            }
        }
    )
}

@Composable
fun AgregarGastoDialog(
    fechaInicioMillis: Long,
    diaSeleccionado: Int,
    onAgregar: (Gasto) -> Unit,
    onCancelar: () -> Unit
) {
    var descripcion by remember { mutableStateOf("") }
    var monto by remember { mutableStateOf("") }

    val montoDouble = monto.toDoubleOrNull() ?: 0.0
    val isValid = descripcion.isNotBlank() && montoDouble > 0.0

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Agregar gasto día $diaSeleccionado") },
        text = {
            Column {
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Descripción") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = monto,
                    onValueChange = {
                        monto = it.filter { c -> c.isDigit() || c == '.' }
                    },
                    label = { Text("Monto") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid) {
                        val gastoNuevo = Gasto(
                            id = UUID.randomUUID().toString(),
                            descripcion = descripcion.trim(),
                            monto = montoDouble,
                            fecha = fechaInicioMillis,
                            esPredeterminado = false,
                            porcentaje = false,
                            hojaId = String()
                        )
                        onAgregar(gastoNuevo)
                    }
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF512DA8),  // Morado oscuro
                    contentColor = Color.White
                )
            ) {
                Text("Agregar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancelar,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF512DA8)  // Morado oscuro para texto cancelar
                )
            ) {
                Text("Cancelar")
            }
        }
    )
}
