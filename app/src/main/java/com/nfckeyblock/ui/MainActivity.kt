package com.nfckeyblock.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nfckeyblock.NfcKeyBlockApp
import com.nfckeyblock.nfc.NdefWriter
import com.nfckeyblock.nfc.WriteResult
import com.nfckeyblock.nfc.NfcReaderController
import com.nfckeyblock.ui.apps.AppsScreen
import com.nfckeyblock.ui.apps.AppsViewModel
import com.nfckeyblock.ui.cards.CardsScreen
import com.nfckeyblock.ui.cards.CardsViewModel
import com.nfckeyblock.ui.cards.ScanPhase
import com.nfckeyblock.ui.home.HomeScreen
import com.nfckeyblock.ui.home.HomeViewModel
import com.nfckeyblock.ui.nav.Destination
import com.nfckeyblock.ui.profiles.ProfilesScreen
import com.nfckeyblock.ui.profiles.ProfilesViewModel
import com.nfckeyblock.ui.settings.SettingsScreen
import com.nfckeyblock.ui.settings.SettingsViewModel
import com.nfckeyblock.ui.stats.StatsScreen
import com.nfckeyblock.ui.stats.StatsViewModel
import com.nfckeyblock.ui.theme.NfcKeyBlockTheme
import com.nfckeyblock.util.Permissions

class MainActivity : ComponentActivity() {

    private lateinit var reader: NfcReaderController
    private var onTag: ((com.nfckeyblock.nfc.NfcTagIdentity) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        reader = NfcReaderController(this)
        val container = (application as NfcKeyBlockApp).container

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val settings by container.settingsRepository.settings.collectAsState(
                initial = com.nfckeyblock.data.prefs.AppSettings()
            )
            NfcKeyBlockTheme(dynamicColor = settings.useDynamicColor) {
                AppScaffold(
                    factory = appViewModelFactory(container),
                    startOnCards = intent?.getBooleanExtra(EXTRA_REGISTER_CARD, false) == true,
                    onReaderRequest = { callback ->
                        onTag = callback
                        if (callback != null) reader.enable { identity -> onTag?.invoke(identity) }
                        else reader.disable()
                    },
                    nfcSupported = reader.isSupported,
                    nfcEnabled = reader.isEnabled
                )
            }
        }
    }

    override fun onPause() {
        reader.disable()
        super.onPause()
    }

    companion object {
        private const val EXTRA_REGISTER_CARD = "register_card"

        fun registerCardIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_REGISTER_CARD, true)
    }
}

@Composable
private fun AppScaffold(
    factory: androidx.lifecycle.ViewModelProvider.Factory,
    startOnCards: Boolean,
    onReaderRequest: (((com.nfckeyblock.nfc.NfcTagIdentity) -> Unit)?) -> Unit,
    nfcSupported: Boolean,
    nfcEnabled: Boolean
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: Destination.Home.route
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var permissions by remember { mutableStateOf(Permissions.status(context)) }

    LaunchedEffect(backStack) { permissions = Permissions.status(context) }
    LaunchedEffect(startOnCards) { if (startOnCards) navController.navigate(Destination.Cards.route) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Destination.entries.firstOrNull { it.route == currentRoute }?.label ?: "Ajustes") },
                actions = {
                    IconButton(onClick = { navController.navigate(Destination.SETTINGS_ROUTE) }) {
                        Icon(Icons.Filled.Settings, "Ajustes")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(Destination.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, null) },
                        label = { Text(destination.label) }
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        NavHost(navController, startDestination = Destination.Home.route) {
            composable(Destination.Home.route) {
                val vm: HomeViewModel = viewModel(factory = factory)
                val ui by vm.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
                HomeScreen(
                    ui = ui,
                    accessibilityReady = permissions.accessibilityEnabled,
                    nfcReady = !nfcSupported || nfcEnabled,
                    onFixPermissions = { context.startActivity(Permissions.accessibilitySettingsIntent()) },
                    onStart = vm::startManual,
                    onStop = vm::stopManual,
                    onRequestEmergency = vm::requestEmergency,
                    onCancelEmergency = vm::cancelEmergency,
                    onConfirmEmergency = vm::confirmEmergency,
                    contentPadding = padding
                )
            }
            composable(Destination.Apps.route) {
                val vm: AppsViewModel = viewModel(factory = factory)
                val ui by vm.state.collectAsStateWithLifecycle()
                AppsScreen(
                    state = ui,
                    onQuery = vm::setQuery,
                    onSelectProfile = vm::selectProfile,
                    onToggle = vm::toggle,
                    onToggleSystem = vm::toggleSystemApps,
                    onSelectSuggested = vm::selectSuggested,
                    contentPadding = padding
                )
            }
            composable(Destination.Cards.route) {
                val vm: CardsViewModel = viewModel(factory = factory)
                val ui by vm.state.collectAsStateWithLifecycle()
                CardsRoute(vm, ui, nfcSupported && nfcEnabled, onReaderRequest, snackbar, padding)
            }
            composable(Destination.Profiles.route) {
                val vm: ProfilesViewModel = viewModel(factory = factory)
                val ui by vm.state.collectAsStateWithLifecycle()
                ProfilesScreen(
                    state = ui,
                    onNew = vm::newProfile,
                    onEdit = vm::edit,
                    onDelete = vm::delete,
                    onDraftChange = vm::updateDraft,
                    onSave = vm::save,
                    onCancel = vm::cancel,
                    contentPadding = padding
                )
            }
            composable(Destination.Stats.route) {
                val vm: StatsViewModel = viewModel(factory = factory)
                val ui by vm.state.collectAsStateWithLifecycle()
                StatsScreen(ui, padding)
            }
            composable(Destination.SETTINGS_ROUTE) {
                val vm: SettingsViewModel = viewModel(factory = factory)
                val ui by vm.state.collectAsStateWithLifecycle()
                SettingsScreen(
                    state = ui,
                    permissions = permissions,
                    onOpenAccessibility = { context.startActivity(Permissions.accessibilitySettingsIntent()) },
                    onOpenUsageAccess = { context.startActivity(Permissions.usageAccessIntent()) },
                    onOpenNfcSettings = { context.startActivity(Permissions.nfcSettingsIntent()) },
                    onOpenBattery = { context.startActivity(Permissions.batteryOptimizationIntent()) },
                    onOpenPrivacy = { },
                    onEmergencyDelay = vm::setEmergencyDelay,
                    onResumeReboot = vm::setResumeAfterReboot,
                    onHaptics = vm::setHaptics,
                    onNotification = vm::setNotification,
                    onDynamicColor = vm::setDynamicColor,
                    contentPadding = padding
                )
            }
        }
    }
}

@Composable
private fun CardsRoute(
    vm: CardsViewModel,
    ui: com.nfckeyblock.ui.cards.CardsUiState,
    nfcReady: Boolean,
    onReaderRequest: (((com.nfckeyblock.nfc.NfcTagIdentity) -> Unit)?) -> Unit,
    snackbar: SnackbarHostState,
    padding: androidx.compose.foundation.layout.PaddingValues
) {
    val context = LocalContext.current
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    // El Reader Mode se enciende solo mientras hay un escaneo en curso; dejarlo
    // encendido siempre consume radio y roba tags a otras apps.
    LaunchedEffect(ui.phase) {
        when (ui.phase) {
            ScanPhase.WAITING -> onReaderRequest(vm::onTagDetected)
            ScanPhase.WAITING_WRITE -> onReaderRequest { identity ->
                val tag = identity.tag
                val token = vm.pendingToken
                if (tag == null || token == null) {
                    vm.onWriteFailed("Se ha perdido el contacto con la tarjeta")
                    return@onReaderRequest
                }
                when (val result = NdefWriter().write(tag, token, context.packageName)) {
                    is WriteResult.Success -> vm.save(writtenToken = token)
                    is WriteResult.ReadOnly -> vm.onWriteFailed("La tarjeta es de solo lectura; se registrará por UID")
                    is WriteResult.TooSmall -> vm.onWriteFailed(
                        "La tarjeta necesita ${result.required} bytes y solo tiene ${result.available}"
                    )
                    is WriteResult.Failed -> vm.onWriteFailed("No se pudo escribir: ${result.cause.message}")
                }
            }
            else -> onReaderRequest(null)
        }
    }

    CardsScreen(
        state = ui,
        nfcAvailable = nfcReady,
        onStartScan = vm::startScan,
        onCancelScan = vm::cancelScan,
        onLabel = vm::setLabel,
        onAction = vm::setAction,
        onProfile = vm::setProfile,
        onWriteToggle = vm::setWriteToCard,
        onSave = { vm.confirm() },
        onDelete = vm::delete,
        contentPadding = padding
    )
}
