package com.example.allofme.screens.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.allofme.data.models.ListaPersonal
import com.example.allofme.viewmodels.ListasViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// ====== Colores (tu paleta) ======
val SoftPink = Color(0xFFFFC1CC)
val PastelLilac = Color(0xFFE6E6FA)
val LightGray = Color(0xFFF5F5F5)
val DarkGray = Color(0xFF666666)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListasScreen(viewModel: ListasViewModel) {
    val listas by viewModel.listas.collectAsState()
    val items by viewModel.items.collectAsState()
    val listaSeleccionadaId by viewModel.listaSeleccionadaId.collectAsState()

    var nuevaLista by rememberSaveable { mutableStateOf("") }
    var nuevoItem by rememberSaveable { mutableStateOf("") }
    var showDialog by rememberSaveable { mutableStateOf(false) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Scroll state
    val listState = rememberLazyListState()

    // Guardar scroll por lista (id -> index/offset)
    val scrollByLista = remember { mutableStateMapOf<String, Pair<Int, Int>>() }

    // 1) Auto-selección de la primera lista
    LaunchedEffect(listas, listaSeleccionadaId) {
        if (listaSeleccionadaId == null && listas.isNotEmpty()) {
            viewModel.seleccionarLista(listas.first().id)
        }
    }

    // 2) Guardar scroll mientras scrollea (solo si hay lista seleccionada)
    LaunchedEffect(listaSeleccionadaId, listState) {
        val id = listaSeleccionadaId ?: return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { pos -> scrollByLista[id] = pos }
    }

    // 3) Restaurar scroll cuando cambias de lista (sin animación para que sea instantáneo)
    LaunchedEffect(listaSeleccionadaId) {
        val id = listaSeleccionadaId ?: return@LaunchedEffect
        val pos = scrollByLista[id]
        if (pos != null) {
            listState.scrollToItem(pos.first, pos.second)
        } else {
            listState.scrollToItem(0, 0)
        }
    }

    // Título calculado sin buscar en cada recomposición pesada
    val titulo by remember(listas, listaSeleccionadaId) {
        derivedStateOf {
            listaSeleccionadaId
                ?.let { id -> listas.firstOrNull { it.id == id }?.nombre }
                ?: "Mis Listas"
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = DarkGray.copy(alpha = 0.45f),
        drawerContent = {
            DrawerContent(
                listas = listas,
                nuevaLista = nuevaLista,
                onNuevaListaChange = { nuevaLista = it },
                onAgregarLista = {
                    if (nuevaLista.isNotBlank()) {
                        viewModel.agregarLista(nuevaLista.trim())
                        nuevaLista = ""
                    }
                },
                onSeleccionarLista = { listaId ->
                    viewModel.seleccionarLista(listaId)
                    scope.launch { drawerState.close() }
                },
                onEliminarLista = viewModel::eliminarLista,
                onCerrar = { scope.launch { drawerState.close() } },
                listaSeleccionadaId = listaSeleccionadaId
            )
        }
    ) {
        Scaffold(
            containerColor = LightGray,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            titulo,
                            fontWeight = FontWeight.Bold,
                            color = DarkGray
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menú", tint = SoftPink)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PastelLilac
                    )
                )
            },
            floatingActionButton = {
                if (listaSeleccionadaId != null) {
                    FloatingActionButton(
                        onClick = { showDialog = true },
                        containerColor = PastelLilac,
                        contentColor = Color.Black,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Agregar ítem")
                    }
                }
            }
        ) { padding ->
            NotebookBackground(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (listaSeleccionadaId == null || listas.isEmpty()) {
                    EmptyState(
                        title = "Aún no tienes listas",
                        subtitle = "Crea una lista desde el menú para empezar ✨"
                    )
                } else if (items.isEmpty()) {
                    EmptyState(
                        title = "Lista vacía",
                        subtitle = "Agrega tu primer ítem con el botón +"
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = items,
                            key = { it.id }
                        ) { item ->
                            ItemRow(
                                title = item.nombre,
                                checked = item.completado,
                                onCheckedChange = { viewModel.toggleItem(item.id) },
                                onDelete = { viewModel.eliminarItem(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDialog && listaSeleccionadaId != null) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                nuevoItem = ""
            },
            title = {
                Text("Nuevo ítem", fontWeight = FontWeight.Bold, color = DarkGray)
            },
            text = {
                OutlinedTextField(
                    value = nuevoItem,
                    onValueChange = { nuevoItem = it },
                    label = { Text("Nombre del ítem") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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
                        val text = nuevoItem.trim()
                        if (text.isNotBlank()) {
                            viewModel.agregarItem(text)
                            nuevoItem = ""
                            showDialog = false
                        }
                    },
                    enabled = nuevoItem.trim().isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PastelLilac,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Agregar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        nuevoItem = ""
                    }
                ) {
                    Text("Cancelar", color = DarkGray)
                }
            },
            containerColor = SoftPink.copy(alpha = 0.9f),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/** Fondo tipo libreta (más barato que drawWithContent) */
@Composable
private fun NotebookBackground(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val lineColor = remember { PastelLilac.copy(alpha = 0.22f) }
    val bg = remember { SoftPink.copy(alpha = 0.55f) }

    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .drawBehind {
                val lineSpacing = 30.dp.toPx()
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    y += lineSpacing
                }
            }
            .padding(14.dp),
        content = content
    )
}

@Composable
private fun ItemRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        color = Color.White.copy(alpha = 0.86f),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = Color.Black,
                    uncheckedColor = Color.DarkGray,
                    checkmarkColor = SoftPink
                )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                color = DarkGray,
                textDecoration = if (checked) TextDecoration.LineThrough else null
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar $title",
                    tint = SoftPink
                )
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DarkGray)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, fontSize = 14.sp, color = DarkGray.copy(alpha = 0.85f))
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

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(max = 340.dp),
        color = SoftPink,
        shape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp),
        shadowElevation = 6.dp
    ) {
        NotebookBackground(
            modifier = Modifier.fillMaxSize().padding(12.dp)
        ) {
            Spacer(Modifier.height(36.dp))

            Text(
                "Mis Listas",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = DarkGray
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = nuevaLista,
                onValueChange = onNuevaListaChange,
                label = { Text("Nueva lista") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PastelLilac,
                    unfocusedBorderColor = LightGray,
                    cursorColor = PastelLilac
                )
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onAgregarLista,
                enabled = nuevaLista.trim().isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PastelLilac,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Agregar lista")
            }

            Spacer(Modifier.height(14.dp))

            LazyColumn(
                state = drawerListState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 10.dp)
            ) {
                items(listas, key = { it.id }) { lista ->
                    ListaRow(
                        nombre = lista.nombre,
                        isSelected = lista.id == listaSeleccionadaId,
                        onClick = { onSeleccionarLista(lista.id) },
                        onDelete = { onEliminarLista(lista.id) }
                    )
                }
            }

            Button(
                onClick = onCerrar,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PastelLilac,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cerrar")
            }
        }
    }
}

@Composable
private fun ListaRow(
    nombre: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val bg = if (isSelected) PastelLilac.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.84f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = bg,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                nombre,
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                color = DarkGray,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar $nombre", tint = SoftPink)
            }
        }
    }
}