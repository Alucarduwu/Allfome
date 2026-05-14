package com.example.allofme.screens.metas

import android.app.DatePickerDialog
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.allofme.data.models.Meta
import com.example.allofme.viewmodels.MetaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

val diasSemanaLista = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

private val PinkTop = Color(0xFFD1C4E9)
private val PinkCard = Color(0xFFF5E6F0)
private val PinkAccent = Color(0xFFEC407A)
private val GraySoft = Color(0xFFB0BEC5)
private val TextDark = Color(0xFF37474F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetasScreen(viewModel: MetaViewModel) {
    val hoy by viewModel.fechaHoy.collectAsStateWithLifecycle(initialValue = Date())

    var mostrarDialog by remember { mutableStateOf(false) }
    var seccionActual by remember { mutableStateOf("Hoy") }
    var metaAEditar by remember { mutableStateOf<Meta?>(null) }
    var mostrarConfirmacion by remember { mutableStateOf<Meta?>(null) }

    val metasHoyOriginal by viewModel.metasHoy.collectAsStateWithLifecycle(initialValue = emptyList())
    val metasSemanaOriginal by viewModel.metasSemana.collectAsStateWithLifecycle(initialValue = emptyList())
    val metasMesOriginal by viewModel.metasMes.collectAsStateWithLifecycle(initialValue = emptyList())
    val (semanaInicio, semanaFin) = viewModel.rangoSemanaActual()

    val metasHoyOrdenadas by remember(metasHoyOriginal) { derivedStateOf { metasHoyOriginal.sortedBy { it.completado } } }
    val metasSemanaOrdenadas by remember(metasSemanaOriginal) { derivedStateOf { metasSemanaOriginal.sortedBy { it.completado } } }
    val metasMesOrdenadas by remember(metasMesOriginal) { derivedStateOf { metasMesOriginal.sortedBy { it.completado } } }

    LaunchedEffect(Unit) {
        viewModel.actualizarMetasSegunFecha()
    }

    // ✅ Snackbar rápido (se quita solo < 2s)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun showKawaii(msg: String, autoDismissMs: Long = 1800L) {
        scope.launch {
            // Cerramos el anterior si existe para que no se acumulen
            snackbarHostState.currentSnackbarData?.dismiss()

            // Lo mostramos INDEFINIDO y lo cerramos nosotros en ~1.8s
            snackbarHostState.showSnackbar(
                message = msg,
                withDismissAction = false,
                duration = SnackbarDuration.Indefinite
            )
        }
        scope.launch {
            delay(autoDismissMs)
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    if (mostrarConfirmacion != null) {
        ConfirmarEliminacionDialog(
            onConfirmar = {
                viewModel.eliminarMeta(mostrarConfirmacion!!)
                showKawaii("Listo ✨ Tarea eliminada", 1500)
                mostrarConfirmacion = null
            },
            onCancelar = { mostrarConfirmacion = null }
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = PinkAccent.copy(alpha = 0.12f),
                    contentColor = TextDark,
                    shape = RoundedCornerShape(14.dp)
                )
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Tareas",
                            color = TextDark,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp
                            )
                        )
                        Text(
                            when (seccionActual) {
                                "Hoy" -> formatoHoy(hoy)
                                "Semana" -> "Semana: ${formatoSemana(semanaInicio, semanaFin)}"
                                else -> "Mes: ${formatoMes(hoy)}"
                            },
                            color = TextDark.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PinkTop)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                modifier = Modifier.shadow(10.dp, RoundedCornerShape(18.dp)),
                containerColor = PinkAccent,
                contentColor = Color.White,
                onClick = {
                    metaAEditar = null
                    mostrarDialog = true
                    showKawaii("Nueva tarea 💗", 1200)
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar")
            }
        }
    ) { paddingValues ->

        // 🌸 Fondo con degradado para que no se vea plano
        val bg = Brush.verticalGradient(
            colors = listOf(
                PinkTop.copy(alpha = 0.35f),
                Color.White,
                PinkCard.copy(alpha = 0.55f)
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bg)
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            SeccionSelector(
                seccionActual = seccionActual,
                onChange = { seccionActual = it }
            )

            Spacer(Modifier.height(12.dp))

            val (titulo, metas) = when (seccionActual) {
                "Hoy" -> "Hoy" to metasHoyOrdenadas
                "Semana" -> "Semana" to metasSemanaOrdenadas
                else -> "Mes" to metasMesOrdenadas
            }

            // ❌ YA NO HAY MENSAJE AUTOMÁTICO DE “TODO COMPLETADO”
            // (lo quitamos como pediste)

            SeccionUnica(
                titulo = titulo,
                metas = metas,
                onEditarMeta = {
                    metaAEditar = it
                    mostrarDialog = true
                    showKawaii("Editando ✨", 1000)
                },
                onEliminarMeta = { mostrarConfirmacion = it },
                onToggle = { meta, checked ->
                    viewModel.actualizarMeta(
                        meta.copy(
                            completado = checked,
                            fechaInicio = if (checked && !meta.completado)
                                Calendar.getInstance().timeInMillis else meta.fechaInicio
                        )
                    )
                    if (checked) {
                        // ✅ mensaje cortito < 2s
                        showKawaii("Súper bien 💗", 1600)
                    } else {
                        showKawaii("Regresó a pendiente 🥺", 1400)
                    }
                }
            )
        }
    }

    if (mostrarDialog) {
        DialogMetaKawaiiPro(
            metaExistente = metaAEditar,
            tipoFrecuencia = seccionActual,
            onGuardar = { meta ->
                if (metaAEditar == null) {
                    viewModel.agregarMeta(meta)
                    showKawaii("Guardada ✨", 1400)
                } else {
                    viewModel.actualizarMeta(meta)
                    showKawaii("Actualizada 💕", 1400)
                }
                mostrarDialog = false
            },
            onCancelar = {
                mostrarDialog = false
                showKawaii("Okis 🫶", 900)
            }
        )
    }
}

@Composable
private fun SeccionSelector(
    seccionActual: String,
    onChange: (String) -> Unit
) {
    val opciones = listOf("Hoy", "Semana", "Mes")
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(4.dp)
    ) {
        opciones.forEachIndexed { index, label ->
            SegmentedButton(
                selected = seccionActual == label,
                onClick = { onChange(label) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = opciones.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = PinkAccent.copy(alpha = 0.18f),
                    activeContentColor = PinkAccent,
                    inactiveContainerColor = Color.White,
                    inactiveContentColor = TextDark
                )
            ) {
                Text(label, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SeccionUnica(
    titulo: String,
    metas: List<Meta>,
    onEditarMeta: (Meta) -> Unit,
    onEliminarMeta: (Meta) -> Unit,
    onToggle: (Meta, Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, PinkAccent.copy(alpha = 0.10f), RoundedCornerShape(22.dp)),
        colors = CardDefaults.cardColors(containerColor = PinkCard),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            Modifier
                .padding(14.dp)
                .animateContentSize()
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        titulo,
                        fontWeight = FontWeight.Bold,
                        color = PinkAccent,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.width(8.dp))

                    val pendientes = metas.count { !it.completado }
                    AssistChip(
                        onClick = { },
                        label = { Text("$pendientes pendientes") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.White,
                            labelColor = TextDark
                        ),

                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (metas.isEmpty()) {
                EmptyStateCute()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(metas, key = { it.id }) { meta ->
                        MetaItemCute(
                            meta = meta,
                            onClick = { onEditarMeta(meta) },
                            onDelete = { onEliminarMeta(meta) },
                            onToggle = { checked -> onToggle(meta, checked) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCute() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No hay tareas aquí 🥺", color = GraySoft, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("Toca el botón + para agregar una", color = GraySoft, fontSize = 12.sp)
    }
}

@Composable
private fun MetaItemCute(
    meta: Meta,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(18.dp))
            .clickable { onClick() },
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = meta.completado,
                onCheckedChange = { onToggle(it) }
            )

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = meta.titulo,
                        fontWeight = if (meta.completado) FontWeight.Medium else FontWeight.Bold,
                        color = if (meta.completado) GraySoft else Color(0xFF212121),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            textDecoration = if (meta.completado)
                                TextDecoration.LineThrough else TextDecoration.None
                        )
                    )

                    if (meta.esPredeterminada == true) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Predeterminada",
                            tint = PinkAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (!meta.descripcion.isNullOrEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        meta.descripcion,
                        color = GraySoft,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            textDecoration = if (meta.completado)
                                TextDecoration.LineThrough else TextDecoration.None
                        )
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { },
                        label = { Text(meta.tipoFrecuencia) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = PinkAccent.copy(alpha = 0.10f),
                            labelColor = PinkAccent
                        )
                    )

                    if (meta.tipoFrecuencia == "Mes" && meta.todoElMes == false && meta.diaMes != null) {
                        AssistChip(
                            onClick = { },
                            label = { Text("Día ${meta.diaMes}") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color(0xFFFCE4EC),
                                labelColor = TextDark
                            )
                        )
                    }
                }
            }

            IconButton(onClick = onClick) {
                Icon(Icons.Default.Edit, contentDescription = "Editar", tint = GraySoft)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = GraySoft)
            }
        }
    }
}

@Composable
fun ConfirmarEliminacionDialog(onConfirmar: () -> Unit, onCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = { onCancelar() },
        title = {
            Text(
                "Eliminar tarea",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Medium,
                    color = PinkAccent
                )
            )
        },
        text = { Text("¿Estás segura que deseas eliminar esta tarea?") },
        confirmButton = {
            TextButton(onClick = { onConfirmar() }) {
                Text("Sí, eliminar", fontWeight = FontWeight.Bold, color = Color(0xFF9575CD))
            }
        },
        dismissButton = {
            TextButton(onClick = { onCancelar() }) {
                Text("Cancelar", color = GraySoft)
            }
        },
        containerColor = PinkCard,
        shape = RoundedCornerShape(18.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogMetaKawaiiPro(
    metaExistente: Meta? = null,
    tipoFrecuencia: String,
    onGuardar: (Meta) -> Unit,
    onCancelar: () -> Unit
) {
    var titulo by remember { mutableStateOf(metaExistente?.titulo ?: "") }
    var descripcion by remember { mutableStateOf(metaExistente?.descripcion ?: "") }
    var diasSemana by remember { mutableStateOf(metaExistente?.diasSemana ?: emptyList()) }
    var diaMes by remember { mutableIntStateOf(metaExistente?.diaMes ?: 1) }
    var todoElMes by remember { mutableStateOf(metaExistente?.todoElMes == true) }
    var abrirCalendario by remember { mutableStateOf(false) }
    var predeterminada by remember { mutableStateOf(metaExistente?.esPredeterminada == true) }
    var errorTitulo by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (abrirCalendario) {
        val calendario = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, _, _, dayOfMonth ->
                diaMes = dayOfMonth
                abrirCalendario = false
            },
            calendario.get(Calendar.YEAR),
            calendario.get(Calendar.MONTH),
            calendario.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    AlertDialog(
        onDismissRequest = { onCancelar() },
        containerColor = PinkCard,
        shape = RoundedCornerShape(22.dp),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (metaExistente == null) "Nueva tarea 💗" else "Editar tarea ✨",
                        color = PinkAccent,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                    Spacer(Modifier.width(10.dp))
                    AssistChip(
                        onClick = { },
                        label = { Text(tipoFrecuencia) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color.White,
                            labelColor = PinkAccent
                        )
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Hazla bonita, clara y fácil de cumplir 🌸",
                    color = TextDark.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                    elevation = CardDefaults.elevatedCardElevation(6.dp),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        OutlinedTextField(
                            value = titulo,
                            onValueChange = {
                                titulo = it
                                errorTitulo = it.isBlank()
                            },
                            label = { Text("Título") },
                            isError = errorTitulo,
                            supportingText = { if (errorTitulo) Text("El título no puede estar vacío") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(10.dp))

                        OutlinedTextField(
                            value = descripcion,
                            onValueChange = { descripcion = it },
                            label = { Text("Descripción (opcional)") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                    elevation = CardDefaults.elevatedCardElevation(6.dp),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = PinkAccent)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Predeterminada ⭐", fontWeight = FontWeight.Bold, color = TextDark)
                            Text(
                                "Aparece como tarea recurrente",
                                color = TextDark.copy(alpha = 0.65f),
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = predeterminada,
                            onCheckedChange = { predeterminada = it }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                when (tipoFrecuencia) {
                    "Semana" -> {
                        ElevatedCard(
                            colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                            elevation = CardDefaults.elevatedCardElevation(6.dp),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text("Días de la semana", fontWeight = FontWeight.Bold, color = TextDark)
                                Spacer(Modifier.height(8.dp))

                                // ✅ Mini scroll para que se vean todos los días
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp), // ajusta 180-260 si quieres
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(diasSemanaLista) { dia ->
                                        val selected = diasSemana.contains(dia)

                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(
                                                    if (selected) PinkAccent.copy(alpha = 0.10f)
                                                    else Color.Transparent
                                                )
                                                .toggleable(
                                                    value = selected,
                                                    onValueChange = {
                                                        diasSemana = if (selected) diasSemana - dia else diasSemana + dia
                                                    }
                                                )
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(checked = selected, onCheckedChange = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text(dia, color = TextDark)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Mes" -> {
                        ElevatedCard(
                            colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                            elevation = CardDefaults.elevatedCardElevation(6.dp),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text("Configuración mensual", fontWeight = FontWeight.Bold, color = TextDark)
                                Spacer(Modifier.height(10.dp))

                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(PinkAccent.copy(alpha = 0.08f))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = todoElMes, onCheckedChange = { todoElMes = it })
                                    Spacer(Modifier.width(6.dp))
                                    Text("Aplica todo el mes", fontWeight = FontWeight.SemiBold, color = TextDark)
                                }

                                if (!todoElMes) {
                                    Spacer(Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = { abrirCalendario = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PinkAccent)
                                    ) {
                                        Text("Elegir día del mes: $diaMes")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (titulo.isBlank()) {
                        errorTitulo = true
                        return@Button
                    }
                    onGuardar(
                        Meta(
                            id = metaExistente?.id ?: 0,
                            titulo = titulo.trim(),
                            descripcion = descripcion.trim(),
                            tipoFrecuencia = tipoFrecuencia,
                            diasSemana = if (tipoFrecuencia == "Semana") diasSemana else null,
                            diaMes = if (tipoFrecuencia == "Mes" && !todoElMes) diaMes else null,
                            todoElMes = if (tipoFrecuencia == "Mes") todoElMes else (metaExistente?.todoElMes ?: false),
                            esPredeterminada = predeterminada,
                            fechaInicio = metaExistente?.fechaInicio ?: Calendar.getInstance().timeInMillis,
                            completado = metaExistente?.completado == true,
                            repeticiones = metaExistente?.repeticiones ?: 1,
                            vecesPorPeriodo = metaExistente?.vecesPorPeriodo ?: 1,
                            fechaFin = metaExistente?.fechaFin,
                            recordatorio = metaExistente?.recordatorio == true,
                            horaRecordatorio = metaExistente?.horaRecordatorio ?: ""
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = PinkAccent, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Guardar ✨", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { onCancelar() }) {
                Text("Cancelar", color = TextDark)
            }
        }
    )
}

/** ======= Formatos de fecha ======= */
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