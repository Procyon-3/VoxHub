package name.me.voxhub.ui.navigation

import android.graphics.Path
import android.view.animation.PathInterpolator
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import name.me.voxhub.R
import name.me.voxhub.VoxHubApplication
import name.me.voxhub.domain.model.ProviderConfigId
import name.me.voxhub.feature.playground.PlaygroundRoute
import name.me.voxhub.feature.providers.*
import name.me.voxhub.feature.settings.SettingsRoute
import name.me.voxhub.feature.usage.UsageRoute
import name.me.voxhub.feature.diagnostics.DiagnosticsRoute

private object Destinations {
    @Serializable data object Reading
    @Serializable data object Plans
    @Serializable data object Settings
    @Serializable data object Usage
    @Serializable data object Diagnostics
    @Serializable data object ChooseSource
    @Serializable data class PlanForm(val providerId: String, val configId: String? = null)
}

private data class TopDestination(val route: Any, val label: Int, val icon: Int)
private val topDestinations = listOf(
    TopDestination(Destinations.Reading, R.string.nav_playground, R.drawable.ic_reading),
    TopDestination(Destinations.Plans, R.string.nav_provider, R.drawable.ic_plans),
    TopDestination(Destinations.Settings, R.string.nav_status, R.drawable.ic_settings),
)

private const val PLAN_CONTAINER_KEY = "new-plan-container"
private const val PLAN_CONTAINER_DURATION_MS = 300
private const val PAGE_SLIDE_DURATION_MS = 300

// Material 3 emphasized easing is a two-segment curve, not a single cubic Bezier.
private val emphasizedEasing = PathInterpolator(Path().apply {
    moveTo(0f, 0f)
    cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
    cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
}).let { interpolator -> Easing(interpolator::getInterpolation) }

@Composable
private fun Modifier.planContainerModifier(
    transitionScope: SharedTransitionScope,
    visibilityScope: AnimatedVisibilityScope,
    isFab: Boolean = false,
): Modifier =
    with(transitionScope) {
        this@planContainerModifier.sharedBounds(
            sharedContentState = rememberSharedContentState(PLAN_CONTAINER_KEY),
            animatedVisibilityScope = visibilityScope,
            boundsTransform = BoundsTransform { _, _ ->
                tween(PLAN_CONTAINER_DURATION_MS, easing = emphasizedEasing)
            },
            // Keep the FAB icon at its normal size, but avoid re-laying out the whole destination page.
            resizeMode = if (isFab) SharedTransitionScope.ResizeMode.RemeasureToBounds
                else SharedTransitionScope.ResizeMode.scaleToBounds(),
            enter = fadeIn(tween(180, delayMillis = 120, easing = emphasizedEasing)),
            exit = fadeOut(tween(180, easing = emphasizedEasing)),
        )
    }

private fun planEnter() = fadeIn(tween(180, delayMillis = 120, easing = emphasizedEasing))
private fun planExit() = fadeOut(tween(180, easing = emphasizedEasing))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideForwardIn(): EnterTransition =
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left,
        tween(PAGE_SLIDE_DURATION_MS, easing = emphasizedEasing))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideForwardOut(): ExitTransition =
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left,
        tween(PAGE_SLIDE_DURATION_MS, easing = emphasizedEasing))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideBackIn(): EnterTransition =
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right,
        tween(PAGE_SLIDE_DURATION_MS, easing = emphasizedEasing))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideBackOut(): ExitTransition =
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right,
        tween(PAGE_SLIDE_DURATION_MS, easing = emphasizedEasing))

private fun NavDestination.isSettingsDetail() =
    hasRoute<Destinations.Usage>() || hasRoute<Destinations.Diagnostics>()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoxHubApp(startAtProviders: Boolean, initialProviderId: String? = null, initialProviderConfigId: String? = null) {
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val container = (LocalContext.current.applicationContext as VoxHubApplication).container
    val destination = navController.currentBackStackEntryAsState().value?.destination
    val topLevel = destination.isTopLevel()
    var initialRedirectDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!initialRedirectDone) {
            initialRedirectDone = true
            if (startAtProviders) {
                navController.navigateTopLevel(Destinations.Plans)
                if (initialProviderId != null && initialProviderConfigId != null) {
                    navController.navigate(Destinations.PlanForm(initialProviderId, initialProviderConfigId))
                }
            }
        }
    }
    val edit: (ProviderConfigId) -> Unit = { id ->
        container.configRepository.currentState.configs.firstOrNull { it.id == id }?.let {
            navController.navigate(Destinations.PlanForm(it.providerId, it.id.value))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = maxWidth >= 600.dp
        val panes = maxWidth >= 840.dp
        val showNavigation = topLevel
        Row(Modifier.fillMaxSize()) {
            if (rail && showNavigation) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    Spacer(Modifier.height(24.dp))
                    topDestinations.forEach { item ->
                        NavigationRailItem(
                            selected = destination.belongsTo(item.route),
                            onClick = { navController.navigateTopLevel(item.route) },
                            icon = { Icon(painterResource(item.icon), null) },
                            label = { Text(stringResource(item.label)) }, alwaysShowLabel = true)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
            Scaffold(modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.surface,
                snackbarHost = { SnackbarHost(snackbar, Modifier.imePadding()) },
                bottomBar = {
                    if (!rail && topLevel) {
                        // Keep the bar composed at the bottom; the IME covers it instead of lifting it.
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            topDestinations.forEach { item ->
                                NavigationBarItem(selected = destination.belongsTo(item.route),
                                    onClick = { navController.navigateTopLevel(item.route) },
                                    icon = { Icon(painterResource(item.icon), null) },
                                    label = { Text(stringResource(item.label)) }, alwaysShowLabel = true)
                            }
                        }
                    }
                }) { padding ->
                SharedTransitionLayout {
                    NavHost(
                        navController,
                        startDestination = Destinations.Reading,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None },
                        // Predictive gestures bypass the destinations' normal pop transitions.
                        predictivePopEnterTransition = { _ ->
                            if (targetState.destination.hasRoute<Destinations.Settings>() &&
                                initialState.destination.isSettingsDetail()) slideBackIn()
                            else fadeIn(spring(dampingRatio = 1f, stiffness = 1600f))
                        },
                        predictivePopExitTransition = { _ ->
                            if (targetState.destination.hasRoute<Destinations.Settings>() &&
                                initialState.destination.isSettingsDetail()) slideBackOut()
                            else scaleOut(targetScale = 0.7f)
                        },
                    ) {
                        composable<Destinations.Reading> {
                            PlaygroundRoute(padding, onManageProviders = { navController.navigate(Destinations.ChooseSource) },
                                showMessage = { snackbar.showSnackbar(it) },
                                onSettings = { navController.navigateTopLevel(Destinations.Settings) }, onEdit = edit, expanded = panes)
                        }
                        composable<Destinations.Plans>(
                            exitTransition = {
                                when {
                                    targetState.destination.hasRoute<Destinations.ChooseSource>() -> planExit()
                                    targetState.destination.hasRoute<Destinations.PlanForm>() -> slideForwardOut()
                                    else -> ExitTransition.None
                                }
                            },
                            popEnterTransition = {
                                when {
                                    initialState.destination.hasRoute<Destinations.ChooseSource>() -> planEnter()
                                    initialState.destination.hasRoute<Destinations.PlanForm>() -> slideBackIn()
                                    else -> EnterTransition.None
                                }
                            },
                        ) {
                            ProvidersRoute(padding, onAdd = { navController.navigate(Destinations.ChooseSource) },
                                onEdit = { edit(it.id) }, showMessage = { snackbar.showSnackbar(it) },
                                addButtonModifier = Modifier.planContainerModifier(this@SharedTransitionLayout, this@composable, isFab = true))
                        }
                        composable<Destinations.Settings>(
                            exitTransition = {
                                if (targetState.destination.isSettingsDetail()) slideForwardOut() else ExitTransition.None
                            },
                            popEnterTransition = { slideBackIn() },
                        ) {
                            SettingsRoute(padding, onUsage = { navController.navigate(Destinations.Usage) },
                                onDiagnostics = { navController.navigate(Destinations.Diagnostics) }, showMessage = { snackbar.showSnackbar(it) })
                        }
                        composable<Destinations.Usage>(
                            enterTransition = {
                                if (initialState.destination.hasRoute<Destinations.Settings>()) slideForwardIn() else EnterTransition.None
                            },
                            popExitTransition = { slideBackOut() },
                        ) {
                            UsageRoute(padding, onBack = { navController.popBackStack() })
                        }
                        composable<Destinations.Diagnostics>(
                            enterTransition = {
                                if (initialState.destination.hasRoute<Destinations.Settings>()) slideForwardIn() else EnterTransition.None
                            },
                            popExitTransition = { slideBackOut() },
                        ) {
                            DiagnosticsRoute(padding, onBack = { navController.popBackStack() }, showMessage = { snackbar.showSnackbar(it) })
                        }
                        composable<Destinations.ChooseSource>(
                            enterTransition = {
                                if (initialState.destination.hasRoute<Destinations.Plans>()) planEnter() else EnterTransition.None
                            },
                            exitTransition = {
                                if (targetState.destination.hasRoute<Destinations.PlanForm>()) slideForwardOut()
                                else ExitTransition.None
                            },
                            popEnterTransition = {
                                if (initialState.destination.hasRoute<Destinations.PlanForm>()) slideBackIn()
                                else EnterTransition.None
                            },
                            popExitTransition = {
                                if (targetState.destination.hasRoute<Destinations.Plans>()) planExit() else ExitTransition.None
                            },
                        ) {
                            Surface(
                                modifier = Modifier.planContainerModifier(this@SharedTransitionLayout, this@composable)
                                    .fillMaxSize(),
                                color = MaterialTheme.colorScheme.surface,
                            ) {
                                ChooseProviderRoute(padding, onBack = { navController.popBackStack() },
                                    onChoose = { navController.navigate(Destinations.PlanForm(it)) })
                            }
                        }
                        composable<Destinations.PlanForm>(
                            enterTransition = {
                                if (initialState.destination.hasRoute<Destinations.ChooseSource>() ||
                                    initialState.destination.hasRoute<Destinations.Plans>()) slideForwardIn()
                                else EnterTransition.None
                            },
                            popExitTransition = {
                                if (targetState.destination.hasRoute<Destinations.ChooseSource>() ||
                                    targetState.destination.hasRoute<Destinations.Plans>()) slideBackOut()
                                else ExitTransition.None
                            },
                        ) { entry ->
                            val args = entry.toRoute<Destinations.PlanForm>()
                            ProviderFormRoute(args.providerId, args.configId?.let(::ProviderConfigId), padding,
                                onBack = { navController.popBackStack() },
                                onSavedNew = { _, _ ->
                                    // Return to the plan list so the saved plan and its enabled state are visible.
                                    navController.popBackStack(Destinations.Reading, inclusive = false)
                                    navController.navigateTopLevel(Destinations.Plans)
                                }, showMessage = { snackbar.showSnackbar(it) })
                        }
                    }
                }
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: Any) {
    // Save only root tabs. Restoring a tab must never resurrect an abandoned editor.
    while (!currentDestination.isTopLevel()) {
        if (!popBackStack()) break
    }
    navigate(route) {
        popUpTo<Destinations.Reading> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavDestination?.belongsTo(route: Any): Boolean = when (route) {
    Destinations.Reading -> this == null || hasRoute<Destinations.Reading>()
    Destinations.Plans -> this?.let { it.hasRoute<Destinations.Plans>() || it.hasRoute<Destinations.ChooseSource>() || it.hasRoute<Destinations.PlanForm>() } == true
    Destinations.Settings -> this?.let { it.hasRoute<Destinations.Settings>() || it.hasRoute<Destinations.Usage>() || it.hasRoute<Destinations.Diagnostics>() } == true
    else -> false
}

private fun NavDestination?.isTopLevel(): Boolean =
    this == null || topDestinations.any { hasRoute(it.route::class) }
