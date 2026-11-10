package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTimeFilled
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.repository.PayrollRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOut
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.AttendanceScreen
import com.example.ui.screens.PayrollScreen
import com.example.ui.screens.TeamAdminScreen
import com.example.ui.util.FormatUtils
import com.example.ui.viewmodel.AssistantViewModel
import com.example.ui.viewmodel.DataState
import com.example.ui.viewmodel.PayrollUiViewModel
import com.example.ui.viewmodel.RoleMode
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_ATTENDANCE = "attendance"
const val ROUTE_PAYROLL = "payroll"
const val ROUTE_ASSISTANT = "assistant"
const val ROUTE_TEAM = "team"

private data class NavDestinationItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

private val navItems = listOf(
    NavDestinationItem(
        route = ROUTE_ATTENDANCE,
        labelRes = R.string.nav_attendance,
        selectedIcon = Icons.Filled.AccessTimeFilled,
        unselectedIcon = Icons.Outlined.AccessTime,
        testTag = "nav_attendance",
    ),
    NavDestinationItem(
        route = ROUTE_PAYROLL,
        labelRes = R.string.nav_payroll,
        selectedIcon = Icons.Filled.AccountBalanceWallet,
        unselectedIcon = Icons.Outlined.AccountBalanceWallet,
        testTag = "nav_payroll",
    ),
    NavDestinationItem(
        route = ROUTE_ASSISTANT,
        labelRes = R.string.nav_assistant,
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
        testTag = "nav_assistant",
    ),
    NavDestinationItem(
        route = ROUTE_TEAM,
        labelRes = R.string.nav_team,
        selectedIcon = Icons.Filled.Groups,
        unselectedIcon = Icons.Outlined.Groups,
        testTag = "nav_team",
    ),
)

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            MainFeatureShell(
                currentUserId = user.uid,
                userEmail = user.email,
                userDisplayName = user.displayName,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainFeatureShell(
    currentUserId: String,
    userEmail: String?,
    userDisplayName: String?,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val payrollViewModel: PayrollUiViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                PayrollUiViewModel(
                    repository = PayrollRepository(db),
                    currentUserId = currentUserId,
                    userDisplayName = userDisplayName,
                )
            }
        },
    )
    val assistantViewModel: AssistantViewModel = viewModel(key = "assistant_$currentUserId")

    val employeesState by payrollViewModel.employeesState.collectAsStateWithLifecycle()
    val attendanceState by payrollViewModel.attendanceState.collectAsStateWithLifecycle()
    val payrollsState by payrollViewModel.payrollsState.collectAsStateWithLifecycle()
    val selectedEmployee by payrollViewModel.selectedEmployee.collectAsStateWithLifecycle()
    val attendanceOvertime by payrollViewModel.selectedEmployeeOvertimeHours.collectAsStateWithLifecycle()
    val roleMode by payrollViewModel.roleMode.collectAsStateWithLifecycle()
    val nowMillis by payrollViewModel.nowMillis.collectAsStateWithLifecycle()
    val calcInputs by payrollViewModel.calcInputs.collectAsStateWithLifecycle()
    val liveBreakdown by payrollViewModel.liveBreakdown.collectAsStateWithLifecycle()
    val statusBanner by payrollViewModel.statusBanner.collectAsStateWithLifecycle()
    val isBusy by payrollViewModel.isBusy.collectAsStateWithLifecycle()

    val assistantMessages by assistantViewModel.messages.collectAsStateWithLifecycle()
    val assistantStatus by assistantViewModel.status.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_ATTENDANCE
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            payrollViewModel.clearStatusBanner()
        }
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val allEmployees = (employeesState as? DataState.Success)?.data.orEmpty()
    val allLogs = (attendanceState as? DataState.Success)?.data.orEmpty()
    val allPayrolls = (payrollsState as? DataState.Success)?.data.orEmpty()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 700.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    actions = {
                        Surface(
                            onClick = {
                                val next = if (roleMode == RoleMode.EMPLOYEE) RoleMode.ADMIN else RoleMode.EMPLOYEE
                                payrollViewModel.setRoleMode(next)
                            },
                            color = if (roleMode == RoleMode.ADMIN) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .testTag("role_toggle_pill"),
                        ) {
                            Text(
                                text = roleMode.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (roleMode == RoleMode.ADMIN) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes),
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useNavRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes),
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag),
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_ATTENDANCE,
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxSize(),
                    ) {
                        composable(ROUTE_ATTENDANCE) {
                            AttendanceScreen(
                                employeesState = employeesState,
                                attendanceState = attendanceState,
                                selectedEmployee = selectedEmployee,
                                roleMode = roleMode,
                                nowMillis = nowMillis,
                                isBusy = isBusy,
                                onSelectEmployee = payrollViewModel::selectEmployee,
                                onClockIn = payrollViewModel::clockIn,
                                onClockOut = payrollViewModel::clockOut,
                                onAddManualAttendance = payrollViewModel::addManualAttendance,
                                onDeleteAttendance = payrollViewModel::deleteAttendance,
                                onAskAssistantAboutAttendance = { log ->
                                    val prompt = "Interpreta esta marcación exacta de ${log.employeeName}: " +
                                        "fecha ${log.workDate}, entrada ${FormatUtils.formatDateTime(log.clockInMillis)}, " +
                                        "salida ${if (log.clockOutMillis > 0L) FormatUtils.formatDateTime(log.clockOutMillis) else "pendiente"}, " +
                                        "ordinarias ${FormatUtils.formatHours(log.regularHours)}, extras ${FormatUtils.formatHours(log.overtimeHours)}, " +
                                        "estado ${log.status}."
                                    navigateToTab(ROUTE_ASSISTANT)
                                    assistantViewModel.sendMessage(
                                        userQuestion = prompt,
                                        roleMode = roleMode,
                                        selectedEmployee = selectedEmployee,
                                        attendanceLogs = allLogs,
                                        currentBreakdown = liveBreakdown,
                                        savedPayrolls = allPayrolls,
                                        allEmployees = allEmployees,
                                    )
                                },
                            )
                        }
                        composable(ROUTE_PAYROLL) {
                            PayrollScreen(
                                selectedEmployee = selectedEmployee,
                                attendanceOvertimeHours = attendanceOvertime,
                                inputs = calcInputs,
                                breakdown = liveBreakdown,
                                payrollsState = payrollsState,
                                isBusy = isBusy,
                                onUpdateInputs = payrollViewModel::updateCalcInputs,
                                onSavePayroll = payrollViewModel::saveCurrentPayroll,
                                onMarkPayrollPaid = payrollViewModel::markPayrollPaid,
                                onDeletePayroll = payrollViewModel::deletePayroll,
                                onExplainCurrentBreakdownWithAi = {
                                    val prompt = "Por favor explica detalladamente mi desglose de nómina ${liveBreakdown.periodType} " +
                                        "(${calcInputs.periodLabel}): Salario Base ${FormatUtils.formatCurrency(liveBreakdown.baseSalaryPeriod)}, " +
                                        "Horas Extras ${FormatUtils.formatHours(liveBreakdown.overtimeHours)} (${FormatUtils.formatCurrency(liveBreakdown.overtimePay)}), " +
                                        "INSS Laboral 7% (${FormatUtils.formatCurrency(liveBreakdown.inssEmployee)}), " +
                                        "IR según tabla proyectada anual (${FormatUtils.formatCurrency(liveBreakdown.irPeriod)}) y Neto a recibir."
                                    navigateToTab(ROUTE_ASSISTANT)
                                    assistantViewModel.sendMessage(
                                        userQuestion = prompt,
                                        roleMode = roleMode,
                                        selectedEmployee = selectedEmployee,
                                        attendanceLogs = allLogs,
                                        currentBreakdown = liveBreakdown,
                                        savedPayrolls = allPayrolls,
                                        allEmployees = allEmployees,
                                    )
                                },
                                onExplainSavedPayrollWithAi = { record ->
                                    val prompt = "Explica este recibo emitido de ${record.employeeName} (${record.periodLabel}, ${record.periodType}): " +
                                        "Base ${FormatUtils.formatCurrency(record.baseSalaryPeriod)}, Extras ${FormatUtils.formatCurrency(record.overtimePay)}, " +
                                        "Bruto ${FormatUtils.formatCurrency(record.grossPay)}, INSS Laboral 7% ${FormatUtils.formatCurrency(record.inssEmployee)}, " +
                                        "Proyección anual ${FormatUtils.formatCurrency(record.projectedAnnualIncome)} (${record.irBracketLabel}), " +
                                        "IR retenido ${FormatUtils.formatCurrency(record.irPeriod)}, Neto ${FormatUtils.formatCurrency(record.netPay)}."
                                    navigateToTab(ROUTE_ASSISTANT)
                                    assistantViewModel.sendMessage(
                                        userQuestion = prompt,
                                        roleMode = roleMode,
                                        selectedEmployee = selectedEmployee,
                                        attendanceLogs = allLogs,
                                        currentBreakdown = liveBreakdown,
                                        savedPayrolls = allPayrolls,
                                        allEmployees = allEmployees,
                                    )
                                },
                            )
                        }
                        composable(ROUTE_ASSISTANT) {
                            AssistantScreen(
                                messages = assistantMessages,
                                status = assistantStatus,
                                roleMode = roleMode,
                                selectedEmployee = selectedEmployee,
                                onSendMessage = { question ->
                                    assistantViewModel.sendMessage(
                                        userQuestion = question,
                                        roleMode = roleMode,
                                        selectedEmployee = selectedEmployee,
                                        attendanceLogs = allLogs,
                                        currentBreakdown = liveBreakdown,
                                        savedPayrolls = allPayrolls,
                                        allEmployees = allEmployees,
                                    )
                                },
                                onClearConversation = assistantViewModel::clearConversation,
                            )
                        }
                        composable(ROUTE_TEAM) {
                            TeamAdminScreen(
                                userEmail = userEmail,
                                roleMode = roleMode,
                                employeesState = employeesState,
                                selectedEmployee = selectedEmployee,
                                onRoleModeChanged = payrollViewModel::setRoleMode,
                                onSelectEmployee = payrollViewModel::selectEmployee,
                                onSaveEmployee = payrollViewModel::saveEmployee,
                                onSignOut = {
                                    signOut(credentialManager, coroutineScope)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
