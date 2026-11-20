package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

enum class MainNavTab(
    val labelAr: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("الرئيسية", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    EXPLORE("استكشف", Icons.Filled.Explore, Icons.Outlined.Explore, "nav_explore"),
    NOTIFICATIONS("الإشعارات", Icons.Filled.Notifications, Icons.Outlined.Notifications, "nav_notifications"),
    PROFILE("حسابي", Icons.Filled.Person, Icons.Outlined.Person, "nav_profile")
}

@Composable
fun HalaMainScaffold(
    currentTab: MainNavTab?,
    onSelectTab: (MainNavTab) -> Unit,
    onCreateQuestionClick: (() -> Unit)?,
    snackbarHostState: SnackbarHostState,
    topBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp && currentTab != null

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail && currentTab != null) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    MainNavTab.entries.forEach { tab ->
                        val selected = tab == currentTab
                        NavigationRailItem(
                            selected = selected,
                            onClick = { onSelectTab(tab) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.labelAr
                                )
                            },
                            label = { Text(tab.labelAr) },
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            }

            Scaffold(
                topBar = topBar,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                floatingActionButton = {
                    if (onCreateQuestionClick != null) {
                        FloatingActionButton(
                            onClick = onCreateQuestionClick,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("fab_create_question")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "طرح سؤال جديد"
                            )
                        }
                    }
                },
                bottomBar = {
                    if (!useRail && currentTab != null) {
                        NavigationBar(
                            windowInsets = WindowInsets.navigationBars,
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            MainNavTab.entries.forEach { tab ->
                                val selected = tab == currentTab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { onSelectTab(tab) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.labelAr
                                        )
                                    },
                                    label = { Text(tab.labelAr) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Box(modifier = Modifier.widthIn(max = 760.dp)) {
                        content()
                    }
                }
            }
        }
    }
}
