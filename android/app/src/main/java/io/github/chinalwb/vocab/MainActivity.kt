package io.github.chinalwb.vocab

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.chinalwb.vocab.ui.BrowseScreen
import io.github.chinalwb.vocab.ui.EntryScreen
import io.github.chinalwb.vocab.ui.ReviewScreen
import io.github.chinalwb.vocab.ui.VocabTheme
import io.github.chinalwb.vocab.ui.VocabViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VocabTheme { VocabNav() }
        }
    }
}

@Composable
private fun VocabNav() {
    val nav = rememberNavController()
    val vm: VocabViewModel = viewModel()
    val lib by vm.library.collectAsStateWithLifecycle()

    NavHost(nav, startDestination = "home") {
        composable("home") { Home(vm, nav) }
        composable("entry/{anchor}") { back ->
            val anchor = back.arguments?.getString("anchor").orEmpty()
            EntryScreen(
                entry = lib.data?.byAnchor?.get(anchor),
                onBack = { nav.popBackStack() },
                onXref = { nav.navigate("entry/$it") },
                onSeen = vm::markSeen,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Home(vm: VocabViewModel, nav: NavHostController) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val checking by vm.checking.collectAsStateWithLifecycle()
    val review by vm.review.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    // Background update checks notify; ask once on Android 13+.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tab == 0) "词汇便签墙" else "复习", fontFamily = FontFamily.Serif) },
                actions = {
                    if (tab == 0) {
                        if (checking) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                        else IconButton(onClick = { vm.check() }) { Icon(Icons.Default.Refresh, "检查更新") }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text("浏览") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.Star, null) }, label = { Text("复习") })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        val open: (String) -> Unit = { nav.navigate("entry/$it") }
        if (tab == 0) {
            BrowseScreen(lib, checking, { vm.check() }, open, { vm.markAllSeen() }, Modifier.padding(pad))
        } else {
            ReviewScreen(
                lib, review, session,
                onStart = vm::startReview,
                onReveal = vm::reveal,
                onGrade = vm::grade,
                onEnd = vm::endReview,
                onReset = { vm.resetReview() },
                onXref = open,
                modifier = Modifier.padding(pad),
            )
        }
    }
}
