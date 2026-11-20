package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ai.AiAssistantResult
import com.example.data.local.AppPreferencesState
import com.example.data.model.AppNotification
import com.example.data.model.NotificationType
import com.example.data.model.Question
import com.example.data.model.ResourceState
import com.example.data.model.SupabaseSchemaInfo
import com.example.data.model.ThemeMode
import com.example.ui.components.AiResultCard
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LoadingFeedPlaceholder
import com.example.ui.components.QuestionCard
import com.example.ui.components.formatShortDate
import com.example.ui.theme.LikeCoral

@Composable
fun NotificationsContent(
    isAuthenticated: Boolean,
    notificationsState: ResourceState<List<AppNotification>>,
    onLoad: () -> Unit,
    onNotificationClick: (AppNotification) -> Unit,
    onGoToLogin: () -> Unit
) {
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) onLoad()
    }

    if (!isAuthenticated) {
        EmptyStateView(
            title = "سجّل دخولك لمتابعة الإشعارات",
            subtitle = "ستصلك إشعارات الإجابات الجديدة والتعليقات والإعجابات فورياً هنا.",
            actionLabel = "تسجيل الدخول",
            onAction = onGoToLogin
        )
        return
    }

    when (notificationsState) {
        is ResourceState.Idle, ResourceState.Loading -> LoadingFeedPlaceholder()
        is ResourceState.Error -> ErrorStateView(message = notificationsState.message, onRetry = onLoad)
        is ResourceState.Success -> {
            if (notificationsState.data.isEmpty()) {
                EmptyStateView(
                    title = "لا توجد إشعارات جديدة",
                    subtitle = "عندما يجيب أحدهم على أسئلتك أو يتفاعل معها ستظهر التنبيهات هنا."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(notificationsState.data, key = { it.id }) { notif ->
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = if (notif.isRead) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNotificationClick(notif) }
                                .testTag("notification_item_${notif.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                val icon = when (notif.type) {
                                    NotificationType.ANSWER -> Icons.Filled.QuestionAnswer
                                    NotificationType.COMMENT -> Icons.AutoMirrored.Filled.Chat
                                    NotificationType.LIKE -> Icons.Filled.Favorite
                                    NotificationType.SYSTEM -> Icons.Filled.Notifications
                                }
                                val tint = when (notif.type) {
                                    NotificationType.LIKE -> LikeCoral
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                Icon(icon, contentDescription = notif.type.labelAr, tint = tint)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(notif.title, style = MaterialTheme.typography.titleSmall)
                                    if (notif.body.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            notif.body,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (notif.createdAt.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            formatShortDate(notif.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefsState: AppPreferencesState,
    schemaInfo: SupabaseSchemaInfo,
    isSupabaseConfigured: Boolean,
    onBack: () -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onToggleAnswerAlerts: (Boolean) -> Unit,
    onToggleLikeAlerts: (Boolean) -> Unit,
    onInspectSchema: () -> Unit,
    onSignOut: () -> Unit,
    onGoToLogin: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("المظهر والوضع الداكن", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectTheme(mode) }
                                .padding(vertical = 4.dp)
                                .testTag("theme_option_${mode.name.lowercase()}")
                        ) {
                            RadioButton(
                                selected = prefsState.themeMode == mode,
                                onClick = { onSelectTheme(mode) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(mode.labelAr, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تفضيلات الإشعارات", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    SettingSwitchRow("تفعيل الإشعارات", prefsState.notificationsEnabled, onToggleNotifications)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    SettingSwitchRow("تنبيهات الإجابات الجديدة", prefsState.answerAlertsEnabled, onToggleAnswerAlerts)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    SettingSwitchRow("تنبيهات الإعجابات والتعليقات", prefsState.likeAlertsEnabled, onToggleLikeAlerts)
                }
            }

            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("اتصال قاعدة البيانات (Supabase)", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "المشروع: qievpkbojqqswvntxiru",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (isSupabaseConfigured) "مفتاح Anon Key مهيأ بأمان عبر Secrets" else "مفتاح Anon Key غير مضاف بعد في Secrets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (schemaInfo.inspected) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "الجداول المكتشفة (${schemaInfo.tables.size}): ${schemaInfo.tables.keys.joinToString("، ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (!schemaInfo.rawError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = schemaInfo.rawError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(onClick = onInspectSchema, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("فحص مخطط الجداول والعلاقات")
                    }
                }
            }

            if (prefsState.session != null) {
                Button(
                    onClick = onSignOut,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("settings_logout_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("تسجيل الخروج (${prefsState.session.email})")
                }
            } else {
                Button(
                    onClick = onGoToLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("تسجيل الدخول")
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    currentQuery: String,
    searchState: ResourceState<List<Question>>,
    aiResult: AiAssistantResult?,
    aiLoading: Boolean,
    onSearch: (String) -> Unit,
    onAiSearchAssist: (String) -> Unit,
    onDismissAi: () -> Unit,
    onBack: () -> Unit,
    onQuestionClick: (String) -> Unit,
    onLikeClick: (Question) -> Unit,
    onShareClick: (Question) -> Unit,
    onAuthorClick: (String) -> Unit
) {
    var query by remember(currentQuery) { mutableStateOf(currentQuery) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("البحث الذكي", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("search_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("ابحث عن سؤال أو موضوع...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onSearch(query) },
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("search_submit_button")
                ) {
                    Text("بحث")
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                OutlinedButton(
                    onClick = { onAiSearchAssist(query) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_ai_assist_button")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("المساعد الذكي للبحث والأسئلة المشابهة")
                }
            }

            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                AiResultCard(
                    result = aiResult,
                    isLoading = aiLoading,
                    onDismiss = onDismissAi,
                    onApplySuggestion = { suggestion ->
                        query = suggestion
                        onSearch(suggestion)
                    }
                )
            }

            when (searchState) {
                is ResourceState.Idle -> {
                    EmptyStateView(
                        title = "ابحث في مجتمع هلا سؤال",
                        subtitle = "اكتب كلمات مفتاحية للوصول السريع إلى الأسئلة والإجابات المرتبطة."
                    )
                }
                is ResourceState.Loading -> LoadingFeedPlaceholder()
                is ResourceState.Error -> ErrorStateView(
                    message = searchState.message,
                    onRetry = { onSearch(query) }
                )
                is ResourceState.Success -> {
                    if (searchState.data.isEmpty()) {
                        EmptyStateView(
                            title = "لم يتم العثور على نتائج",
                            subtitle = "جرّب كلمات بحث أخرى أو استخدم المساعد الذكي لاقتراح صياغات مشابهة."
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(searchState.data, key = { it.id }) { q ->
                                QuestionCard(
                                    question = q,
                                    onClick = { onQuestionClick(q.id) },
                                    onLikeClick = { onLikeClick(q) },
                                    onShareClick = { onShareClick(q) },
                                    onAuthorClick = { onAuthorClick(q.userId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
