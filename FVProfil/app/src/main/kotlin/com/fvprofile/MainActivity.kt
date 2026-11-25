package com.fvprofile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModelProvider
import com.fvprofile.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = ViewModelProvider(this)[AppVM::class.java]
        setContent { FVTheme { App(vm) } }
    }
}

@Composable
fun App(vm: AppVM) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val top = vm.stack.last()
    val openMenu: () -> Unit = { scope.launch { drawer.open() } }
    BackHandler(enabled = drawer.isOpen || vm.stack.size > 1) {
        if (drawer.isOpen) scope.launch { drawer.close() } else vm.pop()
    }
    @Composable
    fun item(label: String, icon: ImageVector, go: () -> Unit) {
        NavigationDrawerItem(label = { Text(label) }, selected = false, icon = { Icon(icon, null) },
            onClick = { scope.launch { drawer.close() }; go() },
            colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = TextHi, unselectedIconColor = RedBright, unselectedContainerColor = Color.Transparent),
            modifier = Modifier.padding(horizontal = 8.dp))
    }
    ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = drawer.isOpen || vm.stack.size == 1, drawerContent = {
        ModalDrawerSheet(drawerContainerColor = Surface1) {
            Text("FV Profil", color = RedBright, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp))
            item("Athlètes & suivi", Icons.Filled.Person) { vm.goRoot(Screen.Athletes) }
            item("Protocole du test", Icons.Filled.Videocam) { vm.push(Screen.Protocol) }
            item("Glossaire des variables", Icons.Filled.Info) { vm.push(Screen.Glossary) }
            item("Références scientifiques", Icons.Filled.Settings) { vm.push(Screen.References) }
        }
    }) {
        when (top) {
            is Screen.Athletes -> AthletesScreen(vm, openMenu)
            is Screen.Protocol -> ProtocolScreen(vm, openMenu)
            is Screen.Glossary -> GlossaryScreen(vm, openMenu)
            is Screen.References -> ReferencesScreen(vm, openMenu)
            is Screen.AthleteEdit -> AthleteEditScreen(vm, top.id, openMenu)
            is Screen.AthleteDetail -> AthleteDetailScreen(vm, top.id, openMenu)
            is Screen.Setup -> SetupScreen(vm, top.athleteId, openMenu)
            is Screen.Hub -> HubScreen(vm, top.sessionId, openMenu)
            is Screen.Record -> RecordScreen(vm, top.sessionId, top.setId, top.videoIdx)
            is Screen.Analysis -> AnalysisScreen(vm, top.sessionId, top.setId, top.videoIdx, openMenu)
            is Screen.Results -> ResultsScreen(vm, top.sessionId, openMenu)
            is Screen.Reco -> RecoScreen(vm, top.sessionId, openMenu)
        }
    }
}

@Composable
fun PageV(vm: AppVM, title: String, onMenu: () -> Unit, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
          fab: @Composable () -> Unit = {}, header: (@Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit)? = null,
          content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) =
    Page(title, vm.stack.size, { vm.pop() }, onMenu, actions, fab, header, content)
