package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.example.core.configureAppCheck
import com.example.ui.ClipMasterViewModel
import com.example.ui.components.ServerSettingsDialog
import com.example.ui.screens.ClipFullscreenScreen
import com.example.ui.screens.ClipHistoryScreen
import com.example.ui.screens.ClipingScreen
import com.example.ui.theme.ClipMasterTheme
import com.example.ui.theme.KineticYellow
import com.example.ui.theme.KineticYellowContainer
import com.example.ui.theme.StudioCanvas
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TextSecondary
import kotlinx.serialization.Serializable

@Serializable
data object ClipingRoute

@Serializable
data object HistoryRoute

@Serializable
data class ClipFullscreenRoute(
    val clipId: String,
    val title: String,
    val previewUrl: String,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent BEFORE Compose UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        val sharedUrl = extractSharedUrl(intent)

        setContent {
            ClipMasterTheme {
                ClipMasterApp(initialSharedUrl = sharedUrl)
            }
        }
    }

    private fun extractSharedUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return com.example.core.YouTubeUrlValidator.extractFirstUrl(text).takeIf { it.isNotBlank() }
    }
}

@Composable
fun ClipMasterApp(
    initialSharedUrl: String? = null,
    viewModel: ClipMasterViewModel = viewModel(),
) {
    LaunchedEffect(initialSharedUrl) {
        if (!initialSharedUrl.isNullOrBlank()) {
            viewModel.onUrlInputChanged(initialSharedUrl)
        }
    }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val context = LocalContext.current

    val urlInput by viewModel.urlInput.collectAsStateWithLifecycle()
    val targetLanguage by viewModel.targetLanguage.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val backendUrl by viewModel.backendUrl.collectAsStateWithLifecycle()
    val savedJobs by viewModel.savedJobs.collectAsStateWithLifecycle()
    val activePlayingClipId by viewModel.activePlayingClipId.collectAsStateWithLifecycle()
    val serverTestState by viewModel.serverTestState.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showServerSettings by remember { mutableStateOf(false) }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    val isFullscreen = currentDestination?.hasRoute<ClipFullscreenRoute>() == true
    val isClipingSelected = currentDestination?.hasRoute<ClipingRoute>() == true
    val isHistorySelected = currentDestination?.hasRoute<HistoryRoute>() == true

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = StudioCanvas,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail && !isFullscreen) {
                    NavigationBar(
                        containerColor = StudioSurface,
                        contentColor = Color.White,
                    ) {
                        NavigationBarItem(
                            selected = isClipingSelected,
                            onClick = {
                                navController.navigate(ClipingRoute) {
                                    popUpTo(ClipingRoute) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isClipingSelected) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                                    contentDescription = stringResource(R.string.nav_studio),
                                )
                            },
                            label = { Text(stringResource(R.string.nav_studio)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = KineticYellow,
                                selectedTextColor = KineticYellow,
                                indicatorColor = KineticYellowContainer,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                            ),
                            modifier = Modifier.testTag("nav_studio_tab"),
                        )
                        NavigationBarItem(
                            selected = isHistorySelected,
                            onClick = {
                                navController.navigate(HistoryRoute) {
                                    popUpTo(ClipingRoute)
                                    launchSingleTop = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isHistorySelected) Icons.Filled.History else Icons.Outlined.History,
                                    contentDescription = stringResource(R.string.nav_saved),
                                )
                            },
                            label = { Text(stringResource(R.string.nav_saved)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = KineticYellow,
                                selectedTextColor = KineticYellow,
                                indicatorColor = KineticYellowContainer,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                            ),
                            modifier = Modifier.testTag("nav_saved_tab"),
                        )
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && !isFullscreen) {
                    NavigationRail(
                        containerColor = StudioSurface,
                        contentColor = Color.White,
                    ) {
                        NavigationRailItem(
                            selected = isClipingSelected,
                            onClick = {
                                navController.navigate(ClipingRoute) {
                                    popUpTo(ClipingRoute) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isClipingSelected) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                                    contentDescription = stringResource(R.string.nav_studio),
                                )
                            },
                            label = { Text(stringResource(R.string.nav_studio)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = KineticYellow,
                                selectedTextColor = KineticYellow,
                                indicatorColor = KineticYellowContainer,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                            ),
                            modifier = Modifier.testTag("rail_studio_tab"),
                        )
                        NavigationRailItem(
                            selected = isHistorySelected,
                            onClick = {
                                navController.navigate(HistoryRoute) {
                                    popUpTo(ClipingRoute)
                                    launchSingleTop = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isHistorySelected) Icons.Filled.History else Icons.Outlined.History,
                                    contentDescription = stringResource(R.string.nav_saved),
                                )
                            },
                            label = { Text(stringResource(R.string.nav_saved)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = KineticYellow,
                                selectedTextColor = KineticYellow,
                                indicatorColor = KineticYellowContainer,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                            ),
                            modifier = Modifier.testTag("rail_saved_tab"),
                        )
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ClipingRoute,
                    modifier = Modifier.weight(1f),
                ) {
                    composable<ClipingRoute>(
                        deepLinks = listOf(
                            navDeepLink<ClipingRoute>(basePath = "clipmaster://cliping"),
                            navDeepLink<ClipingRoute>(basePath = "https://clipmaster.app/cliping"),
                        )
                    ) {
                        ClipingScreen(
                            urlInput = urlInput,
                            selectedLanguage = targetLanguage,
                            uiState = uiState,
                            processingState = processingState,
                            activePlayingClipId = activePlayingClipId,
                            onUrlChanged = viewModel::onUrlInputChanged,
                            onLanguageSelected = viewModel::onTargetLanguageSelected,
                            onClearUrl = viewModel::clearUrlInput,
                            onGenerateClick = viewModel::generateClips,
                            onResetToIdle = viewModel::resetToIdle,
                            onOpenServerSettings = { showServerSettings = true },
                            onTogglePlayClip = { clipId ->
                                viewModel.setActivePlayingClip(
                                    if (activePlayingClipId == clipId) null else clipId
                                )
                            },
                            onDownloadClip = viewModel::downloadClip,
                            onDownloadAllZip = viewModel::downloadAllZip,
                            onShareClip = { clip ->
                                viewModel.prepareShareClip(clip) { uri ->
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "video/mp4"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_TEXT, clip.formattedCopyText())
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Share Clip")
                                    )
                                }
                            },
                            onShowToast = viewModel::showToastMessage,
                            onOpenFullscreen = { clip ->
                                viewModel.setActivePlayingClip(null)
                                navController.navigate(
                                    ClipFullscreenRoute(
                                        clipId = clip.id,
                                        title = clip.title,
                                        previewUrl = clip.previewUrl,
                                    )
                                )
                            },
                        )
                    }

                    composable<HistoryRoute> {
                        ClipHistoryScreen(
                            savedJobs = savedJobs,
                            onOpenJob = { record ->
                                viewModel.openSavedJob(record)
                                navController.navigate(ClipingRoute) {
                                    popUpTo(ClipingRoute) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                        )
                    }

                    composable<ClipFullscreenRoute> { entry ->
                        val route = entry.toRoute<ClipFullscreenRoute>()
                        ClipFullscreenScreen(
                            title = route.title,
                            previewUrl = route.previewUrl,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    if (showServerSettings) {
        ServerSettingsDialog(
            currentUrl = backendUrl,
            testState = serverTestState,
            onTestConnection = viewModel::testServerConnection,
            onSaveUrl = viewModel::saveBackendUrl,
            onDismiss = { showServerSettings = false },
        )
    }
}
