package com.example.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.model.Question
import com.example.data.model.ResourceState
import com.example.ui.components.HalaMainScaffold
import com.example.ui.components.MainNavTab
import com.example.ui.screens.CoinsLevelsScreen
import com.example.ui.screens.CreateQuestionScreen
import com.example.ui.screens.EditProfileScreen
import com.example.ui.screens.ExploreContent
import com.example.ui.screens.HomeFeedContent
import com.example.ui.screens.HomeTopBar
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.NotificationsContent
import com.example.ui.screens.ProfileContent
import com.example.ui.screens.QuestionDetailScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SignUpScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HalaSualNavGraph(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val prefsState by viewModel.prefsState.collectAsStateWithLifecycle()
    val currentUserProfile by viewModel.currentUserProfile.collectAsStateWithLifecycle()
    val schemaInfo by viewModel.schemaInfo.collectAsStateWithLifecycle()
    val authLoading by viewModel.authLoading.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val selectedSort by viewModel.selectedSort.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val homeFeedState by viewModel.homeFeedState.collectAsStateWithLifecycle()
    val exploreCategory by viewModel.exploreCategory.collectAsStateWithLifecycle()
    val exploreState by viewModel.exploreState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val gamificationState by viewModel.gamificationState.collectAsStateWithLifecycle()
    val aiResult by viewModel.aiResult.collectAsStateWithLifecycle()
    val aiLoading by viewModel.aiLoading.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.toastEvents.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun navigateToTab(tab: MainNavTab) {
        val target: Any = when (tab) {
            MainNavTab.HOME -> HomeRoute
            MainNavTab.EXPLORE -> ExploreRoute
            MainNavTab.NOTIFICATIONS -> NotificationsRoute
            MainNavTab.PROFILE -> ProfileRoute(userId = null)
        }
        navController.navigate(target) {
            popUpTo(HomeRoute) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = SplashRoute
    ) {
        // 1. Splash Screen
        composable<SplashRoute> {
            SplashScreen(
                hasActiveSession = prefsState.session != null,
                onboardingCompleted = prefsState.onboardingCompleted,
                isSupabaseConfigured = viewModel.isSupabaseConfigured,
                onContinueToHome = {
                    viewModel.markOnboardingCompleted()
                    navController.navigate(HomeRoute) {
                        popUpTo(SplashRoute) { inclusive = true }
                    }
                },
                onGoToLogin = {
                    viewModel.markOnboardingCompleted()
                    navController.navigate(LoginRoute)
                },
                onGoToSignUp = {
                    viewModel.markOnboardingCompleted()
                    navController.navigate(SignUpRoute)
                }
            )
        }

        // 2. Login Screen
        composable<LoginRoute> {
            LoginScreen(
                isLoading = authLoading,
                errorMessage = authError,
                isSupabaseConfigured = viewModel.isSupabaseConfigured,
                onClearError = viewModel::clearAuthError,
                onLogin = { email, pass ->
                    viewModel.signIn(email, pass) {
                        navController.navigate(HomeRoute) {
                            popUpTo(SplashRoute) { inclusive = true }
                        }
                    }
                },
                onRecoverPassword = { email, onSent ->
                    viewModel.recoverPassword(email, onSent)
                },
                onGoToSignUp = { navController.navigate(SignUpRoute) },
                onBackToHome = {
                    navController.navigate(HomeRoute) {
                        popUpTo(SplashRoute) { inclusive = true }
                    }
                }
            )
        }

        // 3. Sign Up Screen
        composable<SignUpRoute> {
            SignUpScreen(
                isLoading = authLoading,
                errorMessage = authError,
                onClearError = viewModel::clearAuthError,
                onSignUp = { email, pass, username, fullName, onConfirmMsg ->
                    viewModel.signUp(
                        email = email,
                        password = pass,
                        username = username,
                        fullName = fullName,
                        onAuthenticated = {
                            navController.navigate(HomeRoute) {
                                popUpTo(SplashRoute) { inclusive = true }
                            }
                        },
                        onConfirmationNeeded = onConfirmMsg
                    )
                },
                onGoToLogin = {
                    navController.navigate(LoginRoute) {
                        popUpTo(SignUpRoute) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // 4. Home Screen
        composable<HomeRoute> {
            HalaMainScaffold(
                currentTab = MainNavTab.HOME,
                onSelectTab = ::navigateToTab,
                onCreateQuestionClick = {
                    viewModel.clearAiResult()
                    if (prefsState.session != null) {
                        navController.navigate(CreateQuestionRoute(null))
                    } else {
                        navController.navigate(LoginRoute)
                    }
                },
                snackbarHostState = snackbarHostState,
                topBar = {
                    HomeTopBar(
                        currentUser = currentUserProfile,
                        onSearchClick = {
                            viewModel.clearAiResult()
                            navController.navigate(SearchRoute)
                        },
                        onCoinsClick = { navController.navigate(CoinsLevelsRoute) },
                        onRefreshClick = { viewModel.loadHomeFeed() },
                        onSettingsClick = { navController.navigate(SettingsRoute) }
                    )
                }
            ) {
                HomeFeedContent(
                    selectedSort = selectedSort,
                    selectedCategory = selectedCategory,
                    feedState = homeFeedState,
                    isSupabaseConfigured = viewModel.isSupabaseConfigured,
                    onSortChange = { viewModel.loadHomeFeed(sort = it) },
                    onCategoryChange = { viewModel.loadHomeFeed(category = it) },
                    onRefresh = { viewModel.loadHomeFeed() },
                    onQuestionClick = { qId ->
                        viewModel.clearAiResult()
                        navController.navigate(QuestionDetailRoute(qId))
                    },
                    onLikeClick = { viewModel.toggleQuestionLike(it) },
                    onShareClick = { shareQuestion(context, it) },
                    onAuthorClick = { uid -> navController.navigate(ProfileRoute(uid)) },
                    onCreateQuestionClick = {
                        if (prefsState.session != null) {
                            navController.navigate(CreateQuestionRoute(null))
                        } else {
                            navController.navigate(LoginRoute)
                        }
                    }
                )
            }
        }

        // 5. Explore Screen
        composable<ExploreRoute> {
            LaunchedEffect(Unit) {
                if (exploreState is ResourceState.Idle) viewModel.loadExplore()
            }
            HalaMainScaffold(
                currentTab = MainNavTab.EXPLORE,
                onSelectTab = ::navigateToTab,
                onCreateQuestionClick = {
                    viewModel.clearAiResult()
                    if (prefsState.session != null) navController.navigate(CreateQuestionRoute(null))
                    else navController.navigate(LoginRoute)
                },
                snackbarHostState = snackbarHostState,
                topBar = {
                    TopAppBar(
                        title = { Text("استكشف الأسئلة", style = MaterialTheme.typography.titleLarge) },
                        actions = {
                            IconButton(onClick = { navController.navigate(SearchRoute) }) {
                                Icon(Icons.Filled.Search, contentDescription = "البحث")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                }
            ) {
                ExploreContent(
                    selectedCategory = exploreCategory,
                    exploreState = exploreState,
                    onCategorySelected = { viewModel.loadExplore(it) },
                    onRetry = { viewModel.loadExplore() },
                    onQuestionClick = { navController.navigate(QuestionDetailRoute(it)) },
                    onLikeClick = { viewModel.toggleQuestionLike(it) },
                    onShareClick = { shareQuestion(context, it) },
                    onAuthorClick = { navController.navigate(ProfileRoute(it)) }
                )
            }
        }

        // 6. Question Detail Screen
        composable<QuestionDetailRoute> { entry ->
            val route = entry.toRoute<QuestionDetailRoute>()
            QuestionDetailScreen(
                questionId = route.questionId,
                currentUserId = prefsState.session?.userId,
                detailState = detailState,
                isSubmitting = isSubmitting,
                aiResult = aiResult,
                aiLoading = aiLoading,
                onLoadDetail = viewModel::loadQuestionDetail,
                onBack = { navController.popBackStack() },
                onEditQuestion = { qId ->
                    viewModel.clearAiResult()
                    navController.navigate(CreateQuestionRoute(editQuestionId = qId))
                },
                onDeleteQuestion = { qId ->
                    viewModel.deleteQuestion(qId) { navController.popBackStack() }
                },
                onToggleQuestionLike = viewModel::toggleQuestionLike,
                onToggleCommentLike = viewModel::toggleCommentLike,
                onMarkBestAnswer = viewModel::markBestAnswer,
                onSubmitComment = { qId, content, parentId ->
                    viewModel.addCommentOrAnswer(qId, content, parentId)
                },
                onShareQuestion = { shareQuestion(context, it) },
                onReportContent = viewModel::reportContent,
                onAuthorClick = { uid -> navController.navigate(ProfileRoute(uid)) },
                onAiSummarize = viewModel::requestAiSummarize,
                onAiImproveAnswer = viewModel::requestAiImproveAnswer,
                onDismissAi = viewModel::clearAiResult
            )
        }

        // 7. Create / Edit Question Screen
        composable<CreateQuestionRoute> { entry ->
            val route = entry.toRoute<CreateQuestionRoute>()
            val existingQuestion = remember(route.editQuestionId, detailState, homeFeedState) {
                route.editQuestionId?.let { editId ->
                    (detailState as? ResourceState.Success)?.data?.question?.takeIf { it.id == editId }
                        ?: (homeFeedState as? ResourceState.Success)?.data?.firstOrNull { it.id == editId }
                }
            }
            CreateQuestionScreen(
                existingQuestion = existingQuestion,
                isSubmitting = isSubmitting,
                aiResult = aiResult,
                aiLoading = aiLoading,
                onBack = { navController.popBackStack() },
                onAiRefine = viewModel::requestAiQuestionRefinement,
                onDismissAi = viewModel::clearAiResult,
                onSubmit = { title, body, cat, priv, uri, existingUrl ->
                    viewModel.submitQuestion(
                        editQuestionId = route.editQuestionId,
                        title = title,
                        body = body,
                        category = cat,
                        privacy = priv,
                        imageUri = uri,
                        existingImageUrl = existingUrl
                    ) { qId ->
                        navController.navigate(QuestionDetailRoute(qId)) {
                            popUpTo(CreateQuestionRoute(route.editQuestionId)) { inclusive = true }
                        }
                    }
                }
            )
        }

        // 8. Notifications Screen
        composable<NotificationsRoute> {
            HalaMainScaffold(
                currentTab = MainNavTab.NOTIFICATIONS,
                onSelectTab = ::navigateToTab,
                onCreateQuestionClick = null,
                snackbarHostState = snackbarHostState,
                topBar = {
                    TopAppBar(
                        title = { Text("الإشعارات", style = MaterialTheme.typography.titleLarge) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                }
            ) {
                NotificationsContent(
                    isAuthenticated = prefsState.session != null,
                    notificationsState = notificationsState,
                    onLoad = viewModel::loadNotifications,
                    onNotificationClick = { notif ->
                        viewModel.markNotificationRead(notif.id)
                        notif.questionId?.let { navController.navigate(QuestionDetailRoute(it)) }
                    },
                    onGoToLogin = { navController.navigate(LoginRoute) }
                )
            }
        }

        // 9. Profile Screen
        composable<ProfileRoute> { entry ->
            val route = entry.toRoute<ProfileRoute>()
            val isRootOwnProfile = route.userId == null
            HalaMainScaffold(
                currentTab = if (isRootOwnProfile) MainNavTab.PROFILE else null,
                onSelectTab = ::navigateToTab,
                onCreateQuestionClick = null,
                snackbarHostState = snackbarHostState,
                topBar = {
                    TopAppBar(
                        title = { Text("الملف الشخصي", style = MaterialTheme.typography.titleLarge) },
                        actions = {
                            IconButton(onClick = { navController.navigate(SettingsRoute) }) {
                                Icon(Icons.Filled.Settings, contentDescription = "الإعدادات")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                }
            ) {
                ProfileContent(
                    targetUserId = route.userId,
                    currentUserId = prefsState.session?.userId,
                    profileState = profileState,
                    onLoadProfile = viewModel::loadProfile,
                    onEditProfile = { navController.navigate(EditProfileRoute) },
                    onOpenCoinsLevels = { navController.navigate(CoinsLevelsRoute) },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onGoToLogin = { navController.navigate(LoginRoute) },
                    onQuestionClick = { navController.navigate(QuestionDetailRoute(it)) },
                    onLikeQuestion = viewModel::toggleQuestionLike,
                    onShareQuestion = { shareQuestion(context, it) }
                )
            }
        }

        // 10. Edit Profile Screen
        composable<EditProfileRoute> {
            EditProfileScreen(
                currentProfile = currentUserProfile,
                isSubmitting = isSubmitting,
                onBack = { navController.popBackStack() },
                onSave = { username, fullName, bio, avatarUri, coverUri ->
                    viewModel.updateProfile(username, fullName, bio, avatarUri, coverUri) {
                        navController.popBackStack()
                    }
                }
            )
        }

        // 11. Coins & Levels Screen
        composable<CoinsLevelsRoute> {
            CoinsLevelsScreen(
                gamificationState = gamificationState,
                onLoad = viewModel::loadGamification,
                onBack = { navController.popBackStack() }
            )
        }

        // 12. Settings Screen
        composable<SettingsRoute> {
            SettingsScreen(
                prefsState = prefsState,
                schemaInfo = schemaInfo,
                isSupabaseConfigured = viewModel.isSupabaseConfigured,
                onBack = { navController.popBackStack() },
                onSelectTheme = viewModel::setThemeMode,
                onToggleNotifications = viewModel::setNotificationsEnabled,
                onToggleAnswerAlerts = viewModel::setAnswerAlertsEnabled,
                onToggleLikeAlerts = viewModel::setLikeAlertsEnabled,
                onInspectSchema = viewModel::inspectSchemaNow,
                onSignOut = {
                    viewModel.signOut {
                        navController.navigate(LoginRoute) {
                            popUpTo(HomeRoute) { inclusive = true }
                        }
                    }
                },
                onGoToLogin = { navController.navigate(LoginRoute) }
            )
        }

        // 13. Search Screen
        composable<SearchRoute> {
            SearchScreen(
                currentQuery = searchQuery,
                searchState = searchState,
                aiResult = aiResult,
                aiLoading = aiLoading,
                onSearch = viewModel::performSearch,
                onAiSearchAssist = viewModel::requestAiSearchAssist,
                onDismissAi = viewModel::clearAiResult,
                onBack = { navController.popBackStack() },
                onQuestionClick = { navController.navigate(QuestionDetailRoute(it)) },
                onLikeClick = viewModel::toggleQuestionLike,
                onShareClick = { shareQuestion(context, it) },
                onAuthorClick = { navController.navigate(ProfileRoute(it)) }
            )
        }
    }
}

private fun shareQuestion(context: Context, question: Question) {
    val shareText = buildString {
        append("سؤال على تطبيق هلا سؤال:\n")
        append(question.title)
        if (question.body.isNotBlank()) {
            append("\n\n")
            append(question.body.take(180))
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, question.title)
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(intent, "مشاركة السؤال"))
}
