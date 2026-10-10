package io.github.chinalwb.vocab

import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import io.github.chinalwb.vocab.ui.FilterIcon
import io.github.chinalwb.vocab.ui.SearchScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.chinalwb.vocab.ui.LocalHaze
import io.github.chinalwb.vocab.ui.glass
import io.github.chinalwb.vocab.ui.glassStyle
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
import io.github.chinalwb.vocab.ui.SelfTestScreen
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
                composable("search") {
                    SearchScreen(
                        lib,
                        onOpen = { nav.navigate("entry/$it") },
                        onBack = { nav.popBackStack() },
                        selfTests = selfTests,
                        stages = stages,
                    )
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
    val test by vm.test.collectAsStateWithLifecycle()
    // the browse page's 筛选与排序 sheet opens from the top bar
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    var activeFilters by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
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
    // The bars' own content fades a little ahead of the collapse (no scaling) instead of being pushed off.
    val barFade: GraphicsLayerScope.() -> Unit = { alpha = (1 - bars.state.collapsedFraction * 1.6f).coerceIn(0f, 1f) }

    // Background update checks notify; ask once on Android 13+.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // Liquid glass: the content is the haze source; the top bar and the floating tab bar blur it.
    val haze = rememberHazeState()
    CompositionLocalProvider(LocalHaze provides haze) {
    Scaffold(
        modifier = if (tab == 0) Modifier.nestedScroll(bars.nestedScrollConnection) else Modifier,
        topBar = {
            // The status-bar strip isn't part of the bar: it stays solid until the status bar
            // hides, then fades and the list scrolls up into that space.
            // Padding ignores visibility so hiding the status bar doesn't shift the layout.
            val hairline = MaterialTheme.colorScheme.outline
            TopAppBar(
                modifier = Modifier
                    .hazeEffect(haze, glassStyle()) { alpha = stripAlpha }
                    .drawBehind {
                        val y = size.height - 1f
                        drawLine(hairline.copy(alpha = stripAlpha * 0.6f), Offset(0f, y), Offset(size.width, y))
                    }
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                scrollBehavior = if (tab == 0) bars else null,
                title = {
                    val name = when (tab) { 0 -> stringResource(R.string.app_name); 1 -> "复习"; 2 -> "错题本"; else -> "自测" }
                    Text(name, fontFamily = FontFamily.Serif, modifier = Modifier.graphicsLayer(barFade))
                },
                actions = {
                    if (tab == 0) Row(Modifier.graphicsLayer(barFade)) {
                        IconButton(onClick = { insets.show(WindowInsetsCompat.Type.statusBars()); nav.navigate("search") }) {
                            Icon(Icons.Default.Search, "搜索")
                        }
                        IconButton(onClick = { filterOpen = true }) {
                            BadgedBox(badge = { if (activeFilters > 0) Badge(containerColor = MaterialTheme.colorScheme.primary) }) {
                                Icon(FilterIcon, "筛选与排序")
                            }
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                if (checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.MoreVert, "更多")
                            }
                            DropdownMenu(menuOpen, { menuOpen = false }) {
                                // shows the layout you'd switch to, like Keep does
                                DropdownMenuItem(
                                    text = { Text(if (tiles) "列表视图" else "卡片视图") },
                                    leadingIcon = { if (tiles) Icon(Icons.AutoMirrored.Filled.List, null) else Icon(GridViewIcon, null) },
                                    onClick = { menuOpen = false; vm.toggleTiles() },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (checking) "正在检查…" else "检查更新") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                    enabled = !checking,
                                    onClick = { menuOpen = false; vm.check() },
                                )
                                val changed = lib.newAnchors.size + lib.updatedAnchors.size
                                if (changed > 0) DropdownMenuItem(
                                    text = { Text("全部标为已读($changed)") },
                                    leadingIcon = { Icon(Icons.Default.Done, null) },
                                    onClick = { menuOpen = false; vm.markAllSeen() },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            // A floating glass capsule; the cards scroll on underneath it. It slides off the
            // bottom edge with the top bar, the slot shrinking as it goes.
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val bar = measurable.measure(constraints)
                        val shown = (bar.height * (1 - bars.state.collapsedFraction)).roundToInt()
                        layout(bar.width, shown) { bar.place(0, 0) }
                    }
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .glass(RoundedCornerShape(50))
                        .padding(6.dp)
                        .graphicsLayer { alpha = (1 - bars.state.collapsedFraction * 1.6f).coerceIn(0f, 1f) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // same order as the page's tabs; the indices aren't sequential (3 was 练习, now 自测)
                    GlassTab(tab == 0, { tab = 0 }, Icons.AutoMirrored.Filled.List, "浏览")
                    GlassTab(tab == 2, { tab = 2 }, Icons.Default.Warning, "错题")
                    GlassTab(tab == 1, { tab = 1 }, Icons.Default.Star, "复习")
                    GlassTab(tab == 3, { tab = 3 }, Icons.Default.Edit, "自测")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(Modifier.fillMaxSize().hazeSource(haze).background(MaterialTheme.colorScheme.background)) {
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
                sheetOpen = filterOpen,
                onSheetOpen = { filterOpen = it },
                onActiveCount = { activeFilters = it },
            )
        } else if (tab == 2) {
            MistakesScreen(lib, onOpen = open, modifier = Modifier.padding(pad))
        } else if (tab == 3) {
            SelfTestScreen(
                lib, test, stages, selfTests,
                onStart = vm::startTest,
                onReveal = vm::revealTest,
                onNext = vm::nextTest,
                onEnd = vm::endTest,
                onXref = open,
                modifier = Modifier.padding(pad).imePadding(),
            )
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
    }
}

/** One slot of the glass tab bar: the selected one sits in a soft pill. */
@Composable
private fun RowScope.GlassTab(selected: Boolean, onClick: () -> Unit, icon: ImageVector, label: String) {
    val ink = MaterialTheme.colorScheme.onSurface
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            .background(if (selected) ink.copy(alpha = 0.09f) else Color.Transparent)
            .selectable(selected, onClick = onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (selected) ink else ink.copy(alpha = 0.6f))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) ink else ink.copy(alpha = 0.6f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
