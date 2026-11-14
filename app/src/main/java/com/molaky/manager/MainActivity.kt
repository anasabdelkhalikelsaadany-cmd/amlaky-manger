package com.molaky.manager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { MolakyTheme { MolakyRoot() } }
    }
}

@Composable
fun MolakyRoot(vm: MainViewModel = viewModel()) {
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf("tabs") }
    var targetId by rememberSaveable { mutableStateOf(0L) }
    var tab by rememberSaveable { mutableStateOf(0) }

    BackHandler(enabled = route != "tabs") {
        route = if (route == "editor" && targetId != 0L) "detail" else "tabs"
    }

    val list = summaries
    if (list == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val current = list.firstOrNull { it.unit.id == targetId }

    // لو الوحدة اتحذفت أو مش موجودة نرجع للقائمة
    LaunchedEffect(route, current) {
        if (route != "tabs" && targetId != 0L && current == null) route = "tabs"
    }

    when (route) {
        "editor" -> UnitEditorScreen(
            unit = current?.unit,
            onBack = { route = if (targetId != 0L) "detail" else "tabs" },
            onSave = { unit, previousRent ->
                vm.saveUnit(unit, previousRent) {
                    if (unit.id != 0L) {
                        route = "detail"
                    } else {
                        tab = 1
                        route = "tabs"
                    }
                }
            }
        )

        "detail" -> if (current != null) {
            UnitDetailScreen(
                s = current,
                vm = vm,
                onBack = { route = "tabs" },
                onEdit = { route = "editor" },
                onDeleted = { route = "tabs" }
            )
        }

        else -> TabsScreen(
            list = list,
            vm = vm,
            tab = tab,
            onTab = { tab = it },
            onOpen = { id -> targetId = id; route = "detail" },
            onAdd = { targetId = 0L; route = "editor" }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsScreen(
    list: List<UnitSummary>,
    vm: MainViewModel,
    tab: Int,
    onTab: (Int) -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit
) {
    val tabs = listOf(
        "الرئيسية" to Icons.Default.Home,
        "الوحدات" to Icons.Default.Apartment,
        "الإعدادات" to Icons.Default.Settings
    )
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (tab == 0) "مدير أملاكي" else tabs[tab].first) }) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { onTab(i) },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab != 2) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("إضافة وحدة") }
                )
            }
        }
    ) { padding ->
        val m = Modifier.padding(padding).fillMaxSize()
        when (tab) {
            0 -> DashboardTab(list, onOpen, onAdd, m)
            1 -> UnitsTab(list, onOpen, m)
            else -> SettingsTab(vm, m)
        }
    }
}
