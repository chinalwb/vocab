package io.github.chinalwb.vocab

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
import io.github.chinalwb.vocab.ui.LocalNavAnimatedScope
import io.github.chinalwb.vocab.ui.LocalSharedTransitionScope
import io.github.chinalwb.vocab.ui.TRANSITION_MS
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.CompositionLocalProvider
import io.github.chinalwb.vocab.ui.GridViewIcon
import io.github.chinalwb.vocab.ui.MistakesScreen
import io.github.chinalwb.vocab.ui.PracticeScreen
import io.github.chinalwb.vocab.ui.ReviewScreen
import io.github.chinalwb.vocab.ui.VocabTheme
import io.github.chinalwb.vocab.ui.VocabViewModel
import io.github.chinalwb.vocab.review.Stage
import io.github.chinalwb.vocab.review.stageOf

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VocabTheme { VocabNav() }
        }
    }

    // 同步: pull what the page did when I come back, push what's waiting when I leave
    override fun onStart() {
        super.onStart()
        val p = (application as VocabApp).progress
        if (p.ready.isCompleted) p.now()
    }

    override fun onStop() {
        super.onStop()
        (application as VocabApp).progress.flush()
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun VocabNav() {
    val nav = rememberNavController()
    val vm: VocabViewModel = viewModel()
    val lib by vm.library.collectAsStateWithLifecycle()
    val selfTests by vm.selfTests.collectAsStateWithLifecycle()
    val stages by vm.stages.collectAsStateWithLifecycle()

    // Cards and the entry page share bounds across destinations (see SharedTransitions.kt);
    // the plain fades match the container transform's length so both sides finish together.
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                nav,
                startDestination = "home",
                enterTransition = { fadeIn(tween(TRANSITION_MS)) },
                exitTransition = { fadeOut(tween(TRANSITION_MS)) },
            ) {
                composable("home") {
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) { Home(vm, nav) }
                }
                composable("entry/{anchor}") { back ->
                    val anchor = back.arguments?.getString("anchor").orEmpty()
                    CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                        val entry = lib.data?.byAnchor?.get(anchor)
                        EntryScreen(
                            entry = entry,
                            onBack = { nav.popBackStack() },
                            onXref = { nav.navigate("entry/$it") },
                            onSeen = vm::markSeen,
                            attempts = selfTests[anchor].orEmpty(),
                            onAttempt = { text -> if (entry != null) vm.recordSelfTest(entry, text, "entry") },
                            stage = entry?.let { stages.stageOf(it) } ?: Stage.Learn,
                            onStage = { vm.setStage(anchor, it) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun Home(vm: VocabViewModel, nav: NavHostController) {
    val lib by vm.library.collectAsStateWithLifecycle()
    val checking by vm.checking.collectAsStateWithLifecycle()
    val review by vm.review.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val tiles by vm.tiles.collectAsStateWithLifecycle()
    val selfTests by vm.selfTests.collectAsStateWithLifecycle()
    val stages by vm.stages.collectAsStateWithLifecycle()
    val sync by vm.syncStatus.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    // Browsing is immersive: scrolling up slides both bars away, any scroll down
    // brings them straight back. The review tab keeps its bars fixed.
    val bars = TopAppBarDefaults.enterAlwaysScrollBehavior()
    LaunchedEffect(tab) { bars.state.heightOffset = 0f }

    // Fully collapsed → hide the system status bar too; the first scroll down brings it back.
    val view = LocalView.current
    val insets = remember(view) {
        WindowCompat.getInsetsController((view.context as Activity).window, view).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    val statusHidden by remember { derivedStateOf { bars.state.collapsedFraction >= 1f } }
    LaunchedEffect(statusHidden, tab) {
        if (statusHidden && tab == 0) insets.hide(WindowInsetsCompat.Type.statusBars())
        else insets.show(WindowInsetsCompat.Type.statusBars())
    }
    DisposableEffect(Unit) { onDispose { insets.show(WindowInsetsCompat.Type.statusBars()) } }
    // The strip behind the status bar stays solid while the clock is showing, so cards never
    // scroll up under it; it fades only once the status bar itself has gone.
    val stripAlpha by animateFloatAsState(if (statusHidden && tab == 0) 0f else 1f, tween(220), label = "strip")
    // The bars' own content fades a little ahead of the collapse instead of being pushed off.
    val barFade: GraphicsLayerScope.() -> Unit = {
        val f = bars.state.collapsedFraction
        alpha = (1 - f * 1.6f).coerceIn(0f, 1f)
        scaleX = 1 - 0.06f * f
        scaleY = 1 - 0.06f * f
    }

    // Background update checks notify; ask once on Android 13+.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold(
        modifier = if (tab == 0) Modifier.nestedScroll(bars.nestedScrollConnection) else Modifier,
        topBar = {
            // The status-bar strip isn't part of the bar: it stays solid until the status bar
            // hides, then fades and the list scrolls up into that space.
            // Padding ignores visibility so hiding the status bar doesn't shift the layout.
            val ground = MaterialTheme.colorScheme.background
            TopAppBar(
                modifier = Modifier
                    .drawBehind { drawRect(ground.copy(alpha = stripAlpha)) }
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ground, scrolledContainerColor = ground),
                scrollBehavior = if (tab == 0) bars else null,
                title = {
                    val name = when (tab) { 0 -> stringResource(R.string.app_name); 1 -> "复习"; 2 -> "错题本"; else -> "练习" }
                    Text(name, fontFamily = FontFamily.Serif, modifier = Modifier.graphicsLayer {
                        barFade()
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    })
                },
                actions = {
                    if (tab == 0) Row(Modifier.graphicsLayer { barFade(); transformOrigin = TransformOrigin(1f, 0.5f) }) {
                        // Shows the layout you'd switch to, like Keep does.
                        IconButton(onClick = vm::toggleTiles) {
                            if (tiles) Icon(Icons.AutoMirrored.Filled.List, "切换到列表视图")
                            else Icon(GridViewIcon, "切换到卡片视图")
                        }
                        if (checking) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                        else IconButton(onClick = { vm.check() }) { Icon(Icons.Default.Refresh, "检查更新") }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(
                Modifier.layout { measurable, constraints ->
                    val bar = measurable.measure(constraints)
                    val shown = (bar.height * (1 - bars.state.collapsedFraction)).roundToInt()
                    // Shrinking the slot while drawing the bar at its top pushes it off the bottom edge.
                    layout(bar.width, shown) { bar.place(0, 0) }
                }
            ) {
                // the bar's ground stays opaque and slides off; only its items fade, like the top bar's
                val fade = Modifier.graphicsLayer { alpha = (1 - bars.state.collapsedFraction * 1.6f).coerceIn(0f, 1f) }
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text("浏览") }, modifier = fade)
                // same order as the page's tabs; the indices predate 练习, so they aren't sequential
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Warning, null) }, label = { Text("错题") }, modifier = fade)
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.Star, null) }, label = { Text("复习") }, modifier = fade)
                NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Default.Edit, null) }, label = { Text("练习") }, modifier = fade)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        val open: (String) -> Unit = {
            insets.show(WindowInsetsCompat.Type.statusBars())
            nav.navigate("entry/$it")
        }
        if (tab == 0) {
            BrowseScreen(
                lib, checking, { vm.check() }, open, { vm.markAllSeen() }, tiles,
                contentPadding = pad,
                statusBarShown = { stripAlpha },
                selfTests = selfTests,
                stages = stages,
            )
        } else if (tab == 2) {
            MistakesScreen(lib, onOpen = open, modifier = Modifier.padding(pad))
        } else if (tab == 3) {
            PracticeScreen(lib, onOpen = open, modifier = Modifier.padding(pad).imePadding())
        } else {
            ReviewScreen(
                lib, review, session,
                onStart = vm::startReview,
                onReveal = vm::reveal,
                onGrade = vm::grade,
                onEnd = vm::endReview,
                onReset = { vm.resetReview() },
                onXref = open,
                modifier = Modifier.padding(pad).imePadding(),
                selfTests = selfTests,
                onSelfTest = { e, text -> vm.recordSelfTest(e, text, "review") },
                stages = stages,
                onMastered = vm::markMastered,
                sync = sync,
                onConnect = { vm.connectSync(it) },
                onDisconnect = vm::disconnectSync,
                onSyncNow = vm::syncNow,
            )
        }
    }
}
