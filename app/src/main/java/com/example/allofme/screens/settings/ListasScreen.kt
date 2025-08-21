package com.example.allofme.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.allofme.data.models.ListaPersonal
import com.example.allofme.viewmodels.ListasViewModel
import kotlinx.coroutines.launch

// Colores personalizados
val SoftPink = Color(0xFFFFC1CC) // Rosita suave
val PastelLilac = Color(0xFFE6E6FA) // Lila pastel
val LightGray = Color(0xFFF5F5F5) // Gris claro
val DarkGray = Color(0xFF666666) // Gris oscuro para texto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListasScreen(viewModel: ListasViewModel) {
    val listas by viewModel.listas.collectAsState()
    val items by viewModel.items.collectAsState()
    val listaSeleccionadaId by viewModel.listaSeleccionadaId.collectAsState()
    var nuevaLista by remember { mutableStateOf("") }
    var nuevoItem by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var scrollPosition by remember { mutableStateOf(Pair(0, 0)) } // Guarda índice y offset

    // Seleccionar automáticamente la primera lista si no hay ninguna seleccionada
    LaunchedEffect(listas) {
        if (listaSeleccionadaId == null && listas.isNotEmpty()) {
            viewModel.seleccionarLista(listas.first().id)
        }
    }

    // Restaurar scroll después de cambios en ítems
    LaunchedEffect(items, listaSeleccionadaId) {
        if (items.isNotEmpty()) {
            listState.animateScrollToItem(scrollPosition.first, scrollPosition.second)
        }
    }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = SoftPink,
            secondary = PastelLilac,
            surface = LightGray,
            onSurface = DarkGray,
            primaryContainer = SoftPink,
            secondaryContainer = PastelLilac,
            onPrimaryContainer = Color.White,
            onSecondaryContainer = Color.White
        )
    ) {
        ModalNavigationDrawer(
            drawerContent = {
                DrawerContent(
                    listas = listas,
                    nuevaLista = nuevaLista,
                    onNuevaListaChange = { nuevaLista = it },
                    onAgregarLista = {
                        if (nuevaLista.isNotBlank()) {
                            viewModel.agregarLista(nuevaLista)
                            nuevaLista = ""
                        }
                    },
                    onSeleccionarLista = { listaId ->
                        viewModel.seleccionarLista(listaId)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onEliminarLista = viewModel::eliminarLista,
                    onCerrar = { coroutineScope.launch { drawerState.close() } },
                    listaSeleccionadaId = listaSeleccionadaId
                )
            },
            drawerState = drawerState,
            gesturesEnabled = true,
            scrimColor = DarkGray // Fondo opaco para bloquear contenido debajo
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                listaSeleccionadaId?.let { id ->
                                    listas.find { it.id == id }?.nombre ?: "Mis Listas"
                                } ?: "Mis Listas",
                                fontWeight = FontWeight.Bold,
                                color = DarkGray
                            )
                        },
                        actions = {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Abrir menú de listas",
                                    tint = SoftPink
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = PastelLilac,
                            titleContentColor = DarkGray
                        )
                    )
                },
                floatingActionButton = {
                    if (listaSeleccionadaId != null) {
                        FloatingActionButton(
                            onClick = { showDialog = true },
                            containerColor = PastelLilac,
                            contentColor = Color.White,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Agregar nuevo ítem"
                            )
                        }
                    }
                },
                containerColor = LightGray
            ) { padding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(SoftPink.copy(alpha = 0.6f))
                        .drawWithContent {
                            drawContent()
                            // Líneas de libreta tenues
                            val lineSpacing = 32.dp.toPx()
                            val lineColor = PastelLilac.copy(alpha = 0.2f)
                            var y = 0f
                            while (y < size.height) {
                                drawLine(
                                    color = lineColor,
                                    start = androidx.compose.ui.geometry.Offset(0f, y),
                                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                                    strokeWidth = 1.dp.toPx()
                                )
                                y += lineSpacing
                            }
                        }
                        .padding(16.dp)
                ) {
                    Spacer(Modifier.height(16.dp))

                    LazyColumn(state = listState) {
                        items(items, key = { it.id }) { item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = item.completado,
                                    onCheckedChange = {
                                        scrollPosition = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                                        viewModel.toggleItem(item.id)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color.Black, // Fondo negro cuando está marcado
                                        uncheckedColor = Color.DarkGray, // Borde más oscuro cuando no está marcado
                                        checkmarkColor = SoftPink
                                    )
                                )
                                Text(
                                    item.nombre,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 16.sp,
                                    color = DarkGray,
                                    textDecoration = if (item.completado) TextDecoration.LineThrough else null
                                )
                                IconButton(onClick = {
                                    scrollPosition = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                                    viewModel.eliminarItem(item.id)
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Eliminar ítem ${item.nombre}",
                                        tint = SoftPink
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Diálogo para agregar nuevo ítem
            if (showDialog && listaSeleccionadaId != null) {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { Text("Nuevo ítem", fontWeight = FontWeight.Bold, color = DarkGray) },
                    text = {
                        OutlinedTextField(
                            value = nuevoItem,
                            onValueChange = { nuevoItem = it },
                            label = { Text("Nombre del ítem") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(8.dp)),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PastelLilac,
                                unfocusedBorderColor = LightGray,
                                cursorColor = PastelLilac
                            )
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (nuevoItem.isNotBlank()) {
                                    scrollPosition = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                                    viewModel.agregarItem(nuevoItem)
                                    nuevoItem = ""
                                    showDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PastelLilac,
                                contentColor = Color.White
                            ),
                            enabled = nuevoItem.isNotBlank()
                        ) {
                            Text("Agregar")
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = {
                                nuevoItem = ""
                                showDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LightGray,
                                contentColor = DarkGray
                            )
                        ) {
                            Text("Cancelar")
                        }
                    },
                    containerColor = SoftPink,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

@Composable
private fun DrawerContent(
    listas: List<ListaPersonal>,
    nuevaLista: String,
    onNuevaListaChange: (String) -> Unit,
    onAgregarLista: () -> Unit,
    onSeleccionarLista: (String) -> Unit,
    onEliminarLista: (String) -> Unit,
    onCerrar: () -> Unit,
    listaSeleccionadaId: String?
) {
    val drawerListState = rememberLazyListState()

    Column(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            .background(SoftPink)
            .drawWithContent {
                drawContent()
                // Líneas de libreta tenues
                val lineSpacing = 32.dp.toPx()
                val lineColor = PastelLilac.copy(alpha = 0.2f)
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    y += lineSpacing
                }
            }
            .padding(16.dp)
    ) {
        Spacer(Modifier.height(48.dp)) // Espacio para bajar el contenido

        Text(
            "Mis Listas",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = DarkGray
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = nuevaLista,
            onValueChange = onNuevaListaChange,
            label = { Text("Nueva lista") },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(8.dp)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PastelLilac,
                unfocusedBorderColor = LightGray,
                cursorColor = PastelLilac
            )
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onAgregarLista,
            colors = ButtonDefaults.buttonColors(
                containerColor = PastelLilac,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            enabled = nuevaLista.isNotBlank()
        ) {
            Text("Agregar lista")
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(state = drawerListState) {
            items(listas, key = { it.id }) { lista ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .background(
                            if (lista.id == listaSeleccionadaId) PastelLilac.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.8f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                        .clickable {
                            onSeleccionarLista(lista.id)
                            onCerrar()
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        lista.nombre,
                        modifier = Modifier.weight(1f),
                        fontSize = 16.sp,
                        color = DarkGray
                    )
                    IconButton(onClick = { onEliminarLista(lista.id) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Eliminar lista ${lista.nombre}",
                            tint = SoftPink
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onCerrar,
            colors = ButtonDefaults.buttonColors(
                containerColor = PastelLilac,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
        ) {
            Text("Cerrar")
        }
    }
}