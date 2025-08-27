package com.example.allofme.screens.navigation

import android.R.attr.type
import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.allofme.data.database.AppDatabase
import com.example.allofme.data.repository.GastoRepository
import com.example.allofme.data.repository.ListaRepository
import com.example.allofme.data.repository.MetaRepository
import com.example.allofme.data.repository.PeriodoRepository
import com.example.allofme.screens.finanzas.FinanzasScreen
import com.example.allofme.screens.metas.MetasScreen
import com.example.allofme.screens.metas.NotificationManager
import com.example.allofme.screens.settings.ListasScreen
import com.example.allofme.ui.theme.GrisSuave
import com.example.allofme.ui.theme.Lavanda
import com.example.allofme.ui.theme.RositaPastel
import com.example.allofme.viewmodels.GastoViewModel
import com.example.allofme.viewmodels.GastoViewModel.GastoViewModelFactory
import com.example.allofme.viewmodels.ListasViewModel
import com.example.allofme.viewmodels.ListasViewModelFactory
import com.example.allofme.viewmodels.MetaViewModel
import com.example.allofme.viewmodels.MetaViewModelFactory
import com.example.allofme.viewmodels.PeriodoViewModel
import com.example.allofme.viewmodels.PeriodoViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String) {
    object Metas : Screen("metas", "Metas")
    object Finanzas : Screen("finanzas/{hojaId}", "Finanzas")
    object Listas : Screen("listas", "Listas")
}

@Composable
fun AppNavigation(context: Context) {
    val navController = rememberNavController()


    val items = listOf(
        Screen.Metas,
        Screen.Finanzas,
        Screen.Listas
    )

    // Inicializar el canal de notificaciones
    LaunchedEffect(Unit) {
        NotificationManager.createNotificationChannel(context)
                //NotificationManager.scheduleNotifications(context) // 👈 AÑADIDO
    }


    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = RositaPastel,
                tonalElevation = 4.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(imageVector = getIconForScreen(screen), contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute?.startsWith(screen.route.substringBefore("{")) == true,
                        onClick = {
                            if (currentRoute?.startsWith(screen.route.substringBefore("{")) != true) {
                                when (screen) {
                                    is Screen.Finanzas -> {
                                        val hojaIdEjemplo = "hoja123"
                                        navController.navigate("finanzas/$hojaIdEjemplo") {
                                            launchSingleTop = true
                                            restoreState = true
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        }
                                    }
                                    else -> {
                                        navController.navigate(screen.route) {
                                            launchSingleTop = true
                                            restoreState = true
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        }
                                    }
                                }
                            }
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Lavanda,
                            selectedTextColor = Lavanda,
                            unselectedIconColor = GrisSuave,
                            unselectedTextColor = GrisSuave
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Listas.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable(Screen.Metas.route) {
                val db = remember { AppDatabase.getInstance(context) }
                val metaRepository = remember { MetaRepository(db.metaDao()) }
                val sharedPrefs = remember { context.getSharedPreferences("metas_prefs", Context.MODE_PRIVATE) }
                val metaViewModel: MetaViewModel = viewModel(
                    factory = MetaViewModelFactory(metaRepository, sharedPrefs)
                )
                MetasScreen(viewModel = metaViewModel)
            }

            composable(
                route = "finanzas/{hojaId}",
                arguments = listOf(navArgument("hojaId") { type = NavType.StringType })
            ) { backStackEntry ->
                val hojaId = backStackEntry.arguments?.getString("hojaId") ?: ""
                val db = remember { AppDatabase.getInstance(context) }
                val gastoRepository = remember { GastoRepository(db.gastoDao()) }
                val periodoRepository = remember { PeriodoRepository(db.periodoDao()) }
                val sharedPrefs = remember { context.getSharedPreferences("nombre_prefs", Context.MODE_PRIVATE) }
                val periodoViewModel: PeriodoViewModel = viewModel(
                    factory = PeriodoViewModelFactory(periodoRepository)
                )
                val coroutineScope = rememberCoroutineScope()
                val ingresoFlow = remember(periodoViewModel, coroutineScope) {
                    periodoViewModel.periodoSeleccionado
                        .map { it?.ingreso ?: 0.0 }
                        .stateIn(scope = coroutineScope, started = SharingStarted.Lazily, initialValue = 0.0)
                }
                val gastoViewModel: GastoViewModel = viewModel(
                    factory = GastoViewModelFactory(
                        gastoRepository = gastoRepository,
                        sharedPrefs = sharedPrefs,
                        hojaId = hojaId,
                        ingresoExterno = ingresoFlow,
                        periodoSeleccionadoFlow = periodoViewModel.periodoSeleccionado
                    )
                )
                FinanzasScreen(
                    gastoViewModel = gastoViewModel,
                    periodoViewModel = periodoViewModel
                )
            }

            composable(Screen.Listas.route) {
                val db = remember { AppDatabase.getInstance(context) }
                val listaRepository = remember { ListaRepository(db.ListaDao()) }
                val listasViewModel: ListasViewModel = viewModel(
                    factory = ListasViewModelFactory(listaRepository)
                )
                ListasScreen(viewModel = listasViewModel)
            }
        }
    }
}

@Composable
fun getIconForScreen(screen: Screen) = when (screen) {
    Screen.Listas -> Icons.AutoMirrored.Filled.List
    Screen.Finanzas -> Icons.Default.Edit
    Screen.Metas -> Icons.Default.DateRange
}