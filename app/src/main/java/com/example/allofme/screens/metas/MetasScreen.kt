package com.example.allofme.screens.metas

import android.app.DatePickerDialog
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.allofme.data.models.Meta
import com.example.allofme.viewmodels.MetaViewModel
import java.text.SimpleDateFormat
import java.util.*

val diasSemanaLista = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetasScreen(viewModel: MetaViewModel) {
    val hoy by viewModel.fechaHoy.collectAsState(initial = Date())

    var mostrarDialog by remember { mutableStateOf(false) }
    var seccionActual by remember { mutableStateOf("Hoy") }
    var metaAEditar by remember { mutableStateOf<Meta?>(null) }
    var mostrarConfirmacion by remember { mutableStateOf<Meta?>(null) }

    val metasHoyOriginal by viewModel.metasHoy.collectAsState(initial = emptyList())
    val metasSemanaOriginal by viewModel.metasSemana.collectAsState(initial = emptyList())
    val metasMesOriginal by viewModel.metasMes.collectAsState(initial = emptyList())
    val (semanaInicio, semanaFin) = viewModel.rangoSemanaActual()

    // Ordenar metas: no completadas primero
    val metasHoyOrdenadas = remember(metasHoyOriginal) { metasHoyOriginal.sortedBy { it.completado } }
    val metasSemanaOrdenadas = remember(metasSemanaOriginal) { metasSemanaOriginal.sortedBy { it.completado } }
    val metasMesOrdenadas = remember(metasMesOriginal) { metasMesOriginal.sortedBy { it.completado } }

    // Control de último reset
    var ultimaFechaDiaria by remember { mutableIntStateOf(0) }
    var ultimaSemana by remember { mutableStateOf(0 to 0) }
    var ultimoMes by remember { mutableStateOf(0 to 0) }

    // Reseteo automático
    LaunchedEffect(hoy, metasHoyOriginal, metasSemanaOriginal, metasMesOriginal) {
        val calHoy = Calendar.getInstance().apply { time = hoy }

        // Reset diario
        val diaHoy = calHoy.get(Calendar.DAY_OF_YEAR)
        if (ultimaFechaDiaria != diaHoy) {
            metasHoyOriginal.forEach { meta ->
                if (meta.completado) viewModel.actualizarMeta(meta.copy(completado = false))
            }
            ultimaFechaDiaria = diaHoy
        }

        // Reset semanal
        val semanaActual = calHoy.get(Calendar.WEEK_OF_YEAR)
        val anioActual = calHoy.get(Calendar.YEAR)
        if (ultimaSemana != semanaActual to anioActual) {
            metasSemanaOriginal.forEach { meta ->
                if (meta.completado) viewModel.actualizarMeta(meta.copy(completado = false))
            }
            ultimaSemana = semanaActual to anioActual
        }

        // Reset mensual
        val mesActual = calHoy.get(Calendar.MONTH)
        if (ultimoMes != mesActual to anioActual) {
            metasMesOriginal.forEach { meta ->
                if (meta.completado) viewModel.actualizarMeta(meta.copy(completado = false))
            }
            ultimoMes = mesActual to anioActual
        }
    }

    if (mostrarConfirmacion != null) {
        ConfirmarEliminacionDialog(
            onConfirmar = {
                viewModel.eliminarMeta(mostrarConfirmacion!!)
                mostrarConfirmacion = null
            },
            onCancelar = { mostrarConfirmacion = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Tareas",
                        color = Color(0xFF37474F),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFD1C4E9))
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SeccionCalendario(
                    titulo = "Hoy - ${formatoHoy(hoy)}",
                    metas = metasHoyOrdenadas,
                    viewModel = viewModel,
                    onEditarMeta = { metaAEditar = it; seccionActual = "Hoy"; mostrarDialog = true },
                    onEliminarMeta = { mostrarConfirmacion = it },
                    onAgregarMeta = { seccionActual = "Hoy"; metaAEditar = null; mostrarDialog = true }
                )
            }
            item {
                SeccionCalendario(
                    titulo = "Semana - ${formatoSemana(semanaInicio, semanaFin)}",
                    metas = metasSemanaOrdenadas,
                    viewModel = viewModel,
                    onEditarMeta = { metaAEditar = it; seccionActual = "Semana"; mostrarDialog = true },
                    onEliminarMeta = { mostrarConfirmacion = it },
                    onAgregarMeta = { seccionActual = "Semana"; metaAEditar = null; mostrarDialog = true }
                )
            }
            item {
                SeccionCalendario(
                    titulo = "Mes - ${formatoMes(hoy)}",
                    metas = metasMesOrdenadas,
                    viewModel = viewModel,
                    onEditarMeta = { metaAEditar = it; seccionActual = "Mes"; mostrarDialog = true },
                    onEliminarMeta = { mostrarConfirmacion = it },
                    onAgregarMeta = { seccionActual = "Mes"; metaAEditar = null; mostrarDialog = true }
                )
            }
        }
    }

    if (mostrarDialog) {
        DialogMeta(
            metaExistente = metaAEditar,
            tipoFrecuencia = seccionActual,
            onGuardar = { meta ->
                if (metaAEditar == null) viewModel.agregarMeta(meta) else viewModel.actualizarMeta(meta)
                mostrarDialog = false
            },
            onCancelar = { mostrarDialog = false }
        )
    }
}

@Composable
fun ConfirmarEliminacionDialog(onConfirmar: () -> Unit, onCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = { onCancelar() },
        title = {
            Text("Eliminar tarea", style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Medium, color = Color(0xFFEC407A)))
        },
        text = { Text("¿Estás segura que deseas eliminar esta tarea?") },
        confirmButton = {
            TextButton(onClick = { onConfirmar() }) {
                Text("Sí", fontWeight = FontWeight.Bold, color = Color(0xFF9575CD))
            }
        },
        dismissButton = {
            TextButton(onClick = { onCancelar() }) {
                Text("Cancelar", color = Color(0xFFB0BEC5))
            }
        },
        containerColor = Color(0xFFF5E6F0),
        shape = RoundedCornerShape(16.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogMeta(
    metaExistente: Meta? = null,
    tipoFrecuencia: String,
    onGuardar: (Meta) -> Unit,
    onCancelar: () -> Unit
) {
    var titulo by remember { mutableStateOf(metaExistente?.titulo ?: "") }
    var descripcion by remember { mutableStateOf(metaExistente?.descripcion ?: "") }
    var diasSemana by remember { mutableStateOf(metaExistente?.diasSemana ?: emptyList()) }
    var diaMes by remember { mutableIntStateOf(metaExistente?.diaMes ?: 1) } // Int
    var todoElMes by remember { mutableStateOf(metaExistente?.todoElMes == true) }
    var abrirCalendario by remember { mutableStateOf(false) }
    var errorTitulo by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (abrirCalendario) {
        val calendario = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance()
                cal.set(year, month, dayOfMonth)
                diaMes = cal.get(Calendar.DAY_OF_MONTH)
                abrirCalendario = false
            },
            calendario.get(Calendar.YEAR),
            calendario.get(Calendar.MONTH),
            calendario.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = { onCancelar() },
        confirmButton = {
            TextButton(
                enabled = titulo.isNotBlank(),
                onClick = {
                    if (titulo.isNotBlank()) {
                        onGuardar(
                            Meta(
                                id = metaExistente?.id ?: 0,
                                titulo = titulo,
                                descripcion = descripcion,
                                tipoFrecuencia = tipoFrecuencia,
                                diasSemana = if (tipoFrecuencia == "Semana") diasSemana else null,
                                diaMes = if (tipoFrecuencia == "Mes" && !todoElMes) diaMes else null,
                                todoElMes = todoElMes,
                                esPredeterminada = todoElMes,
                                fechaInicio = Calendar.getInstance().timeInMillis,
                                completado = metaExistente?.completado == true,
                                repeticiones = metaExistente?.repeticiones ?: 1,
                                vecesPorPeriodo = metaExistente?.vecesPorPeriodo ?: 1,
                                fechaFin = metaExistente?.fechaFin,
                                recordatorio = metaExistente?.recordatorio == true,
                                horaRecordatorio = metaExistente?.horaRecordatorio ?: ""
                            )
                        )
                    } else errorTitulo = true
                }
            ) { Text("Guardar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { onCancelar() }) { Text("Cancelar") } },
        title = { Text(if (metaExistente == null) "Nueva Tarea" else "Editar Tarea") },
        text = {
            Column(Modifier.fillMaxWidth().animateContentSize().padding(8.dp)) {
                OutlinedTextField(
                    value = titulo,
                    onValueChange = {
                        titulo = it
                        errorTitulo = it.isBlank()
                    },
                    label = { Text("Título") },
                    isError = errorTitulo,
                    supportingText = { if (errorTitulo) Text("El título no puede estar vacío") }
                )
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Descripción") }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = todoElMes, onCheckedChange = { todoElMes = it })
                    Text("Predeterminada / Todo el mes")
                }
                when (tipoFrecuencia) {
                    "Semana" -> {
                        Text("Selecciona días de la semana:")
                        diasSemanaLista.forEach { dia ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = diasSemana.contains(dia),
                                        onValueChange = {
                                            diasSemana = if (diasSemana.contains(dia)) {
                                                diasSemana - dia
                                            } else {
                                                diasSemana + dia
                                            }
                                        }
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = diasSemana.contains(dia), onCheckedChange = null)
                                Text(dia)
                            }
                        }
                    }
                    "Mes" -> {
                        if (!todoElMes) {
                            Box(
                                Modifier.fillMaxWidth().height(56.dp).background(Color(0xFFF5E6F0))
                                    .clickable { abrirCalendario = true },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text("Día: $diaMes", Modifier.padding(start = 16.dp))
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFFF5E6F0),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun SeccionCalendario(
    titulo: String,
    metas: List<Meta>,
    viewModel: MetaViewModel,
    onEditarMeta: (Meta) -> Unit,
    onEliminarMeta: (Meta) -> Unit,
    onAgregarMeta: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5E6F0)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp).animateContentSize()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(titulo, fontWeight = FontWeight.SemiBold, color = Color(0xFFEC407A))
                IconButton(onClick = onAgregarMeta) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar")
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (metas.isEmpty()) {
                    Text("No hay tareas para esta sección", color = Color(0xFFB0BEC5))
                } else {
                    metas.forEach { meta ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onEditarMeta(meta) },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(1.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = meta.completado,
                                    onCheckedChange = { isChecked ->
                                        viewModel.actualizarMeta(
                                            meta.copy(
                                                completado = isChecked,
                                                fechaInicio = if (isChecked && !meta.completado)
                                                    Calendar.getInstance().timeInMillis else meta.fechaInicio
                                            )
                                        )
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        meta.titulo,
                                        fontWeight = if (meta.completado) FontWeight.Normal else FontWeight.Medium,
                                        color = if (meta.completado) Color(0xFFB0BEC5) else Color(0xFF212121)
                                    )
                                    if (!meta.descripcion.isNullOrEmpty()) {
                                        Text(meta.descripcion, color = Color(0xFFB0BEC5))
                                    }
                                }
                                IconButton(onClick = { onEliminarMeta(meta) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color(0xFFB0BEC5))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatoHoy(fecha: Date): String {
    val formato = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES"))
    return formato.format(fecha).replaceFirstChar { it.uppercase() }
}

fun formatoSemana(inicio: Date, fin: Date): String {
    val formato = SimpleDateFormat("d MMM", Locale("es", "ES"))
    return "${formato.format(inicio)} - ${formato.format(fin)}"
}

fun formatoMes(fecha: Date): String {
    val formato = SimpleDateFormat("MMMM yyyy", Locale("es", "ES"))
    return formato.format(fecha).replaceFirstChar { it.uppercase() }
}
