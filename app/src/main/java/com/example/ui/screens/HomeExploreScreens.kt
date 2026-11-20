package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppCategories
import com.example.data.model.FeedSort
import com.example.data.model.Question
import com.example.data.model.ResourceState
import com.example.data.model.UserProfile
import com.example.ui.components.CategoryChipRow
import com.example.ui.components.ConfigurationBanner
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LoadingFeedPlaceholder
import com.example.ui.components.QuestionCard
import com.example.ui.theme.CoinGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    currentUser: UserProfile?,
    onSearchClick: () -> Unit,
    onCoinsClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "هلا سؤال",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "اسأل، شارك، وتعلّم",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            if (currentUser != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(50.dp),
                    modifier = Modifier
                        .clickable(onClick = onCoinsClick)
                        .testTag("home_coins_badge")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MonetizationOn,
                            contentDescription = "العملات",
                            tint = CoinGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${currentUser.coins}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            IconButton(onClick = onSearchClick, modifier = Modifier.testTag("home_search_button")) {
                Icon(Icons.Filled.Search, contentDescription = "البحث")
            }
            IconButton(onClick = onRefreshClick, modifier = Modifier.testTag("home_refresh_button")) {
                Icon(Icons.Filled.Refresh, contentDescription = "تحديث المحتوى")
            }
            IconButton(onClick = onSettingsClick, modifier = Modifier.testTag("home_settings_button")) {
                Icon(Icons.Filled.Settings, contentDescription = "الإعدادات")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
fun HomeFeedContent(
    selectedSort: FeedSort,
    selectedCategory: String,
    feedState: ResourceState<List<Question>>,
    isSupabaseConfigured: Boolean,
    onSortChange: (FeedSort) -> Unit,
    onCategoryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onQuestionClick: (String) -> Unit,
    onLikeClick: (Question) -> Unit,
    onShareClick: (Question) -> Unit,
    onAuthorClick: (String) -> Unit,
    onCreateQuestionClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (!isSupabaseConfigured) {
            ConfigurationBanner()
        }

        val tabs = FeedSort.entries
        TabRow(
            selectedTabIndex = tabs.indexOf(selectedSort),
            containerColor = MaterialTheme.colorScheme.background
        ) {
            tabs.forEach { sort ->
                Tab(
                    selected = sort == selectedSort,
                    onClick = { onSortChange(sort) },
                    text = {
                        Text(
                            text = sort.labelAr,
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    modifier = Modifier.testTag("feed_tab_${sort.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        CategoryChipRow(
            selectedCategory = selectedCategory,
            onCategorySelected = onCategoryChange
        )
        Spacer(modifier = Modifier.height(8.dp))

        when (feedState) {
            is ResourceState.Idle, ResourceState.Loading -> LoadingFeedPlaceholder()
            is ResourceState.Error -> ErrorStateView(
                message = feedState.message,
                onRetry = onRefresh
            )
            is ResourceState.Success -> {
                if (feedState.data.isEmpty()) {
                    EmptyStateView(
                        title = "لا توجد أسئلة في هذا التصنيف حالياً",
                        subtitle = "كن أول من يطرح سؤالاً ملهماً في مجتمع هلا سؤال!",
                        actionLabel = "اطرح سؤالاً الآن",
                        onAction = onCreateQuestionClick
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(feedState.data, key = { it.id }) { question ->
                            QuestionCard(
                                question = question,
                                onClick = { onQuestionClick(question.id) },
                                onLikeClick = { onLikeClick(question) },
                                onShareClick = { onShareClick(question) },
                                onAuthorClick = { onAuthorClick(question.userId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExploreContent(
    selectedCategory: String,
    exploreState: ResourceState<List<Question>>,
    onCategorySelected: (String) -> Unit,
    onRetry: () -> Unit,
    onQuestionClick: (String) -> Unit,
    onLikeClick: (Question) -> Unit,
    onShareClick: (Question) -> Unit,
    onAuthorClick: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "استكشف موضوعات المعرفة",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "تصفح أبرز الأسئلة الشائعة والموضوعات المتخصصة",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        item {
            val featuredCategories = AppCategories.items.filter { it != AppCategories.ALL }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                featuredCategories.chunked(2).forEach { pair ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        pair.forEach { cat ->
                            val isSelected = selectedCategory == cat
                            Surface(
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onCategorySelected(if (isSelected) AppCategories.ALL else cat)
                                    }
                                    .testTag("explore_cat_$cat")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Icon(
                                        imageVector = categoryIcon(cat),
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = cat,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Text(
                text = if (selectedCategory == AppCategories.ALL) "الأسئلة الشائعة الآن" else "أسئلة في $selectedCategory",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        when (exploreState) {
            is ResourceState.Idle, ResourceState.Loading -> item { LoadingFeedPlaceholder() }
            is ResourceState.Error -> item {
                ErrorStateView(message = exploreState.message, onRetry = onRetry)
            }
            is ResourceState.Success -> {
                if (exploreState.data.isEmpty()) {
                    item {
                        EmptyStateView(
                            title = "لا توجد أسئلة مطابقة",
                            subtitle = "جرّب اختيار تصنيف آخر أو تحديث القائمة."
                        )
                    }
                } else {
                    items(exploreState.data, key = { it.id }) { question ->
                        QuestionCard(
                            question = question,
                            onClick = { onQuestionClick(question.id) },
                            onLikeClick = { onLikeClick(question) },
                            onShareClick = { onShareClick(question) },
                            onAuthorClick = { onAuthorClick(question.userId) }
                        )
                    }
                }
            }
        }
    }
}

private fun categoryIcon(cat: String): ImageVector {
    return when {
        cat.contains("التقنية") -> Icons.Filled.Code
        cat.contains("التعليم") -> Icons.Filled.School
        cat.contains("الصحة") -> Icons.Filled.VolunteerActivism
        cat.contains("الأعمال") -> Icons.Filled.WorkOutline
        cat.contains("الثقافة") -> Icons.Filled.MenuBook
        cat.contains("التصميم") -> Icons.Filled.Palette
        else -> Icons.Filled.TrendingUp
    }
}
