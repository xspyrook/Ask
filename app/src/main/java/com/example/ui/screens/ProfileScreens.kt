package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.AchievementBadge
import com.example.data.model.Question
import com.example.data.model.ResourceState
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LevelBadge
import com.example.ui.components.LoadingFeedPlaceholder
import com.example.ui.components.QuestionCard
import com.example.ui.components.UserAvatar
import com.example.ui.components.VerifiedBadge
import com.example.ui.components.formatShortDate
import com.example.ui.theme.CoinGold
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.GamificationBundle
import com.example.ui.viewmodel.ProfileBundle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileContent(
    targetUserId: String?,
    currentUserId: String?,
    profileState: ResourceState<ProfileBundle>,
    onLoadProfile: (String?) -> Unit,
    onEditProfile: () -> Unit,
    onOpenCoinsLevels: () -> Unit,
    onOpenSettings: () -> Unit,
    onGoToLogin: () -> Unit,
    onQuestionClick: (String) -> Unit,
    onLikeQuestion: (Question) -> Unit,
    onShareQuestion: (Question) -> Unit
) {
    LaunchedEffect(targetUserId, currentUserId) {
        onLoadProfile(targetUserId)
    }

    var selectedTab by remember { mutableIntStateOf(0) }

    if (targetUserId == null && currentUserId == null) {
        EmptyStateView(
            title = "أنت تتصفح كزائر",
            subtitle = "سجّل دخولك لعرض ملفك الشخصي، نقاط الخبرة، العملات، وأسئلتك.",
            actionLabel = "تسجيل الدخول",
            onAction = onGoToLogin
        )
        return
    }

    when (profileState) {
        is ResourceState.Idle, ResourceState.Loading -> LoadingFeedPlaceholder()
        is ResourceState.Error -> ErrorStateView(
            message = profileState.message,
            onRetry = { onLoadProfile(targetUserId) }
        )
        is ResourceState.Success -> {
            val bundle = profileState.data
            val profile = bundle.profile
            val isOwnProfile = currentUserId != null && profile.id == currentUserId

            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (!profile.coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = profile.coverUrl,
                                contentDescription = "صورة الغلاف",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(148.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(148.dp)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.tertiary
                                            )
                                        )
                                    )
                            )
                        }

                        if (isOwnProfile) {
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                    .testTag("profile_settings_button")
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = "الإعدادات")
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .padding(start = 16.dp, top = 104.dp)
                                .size(86.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                UserAvatar(profile.displayName, profile.avatarUrl, size = 78.dp)
                            }
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = profile.displayName,
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                    if (profile.isVerified) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        VerifiedBadge()
                                    }
                                }
                                if (profile.username.isNotBlank()) {
                                    Text(
                                        text = "@${profile.username}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isOwnProfile) {
                                OutlinedButton(
                                    onClick = onEditProfile,
                                    modifier = Modifier.testTag("edit_profile_button")
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("تعديل الملف")
                                }
                            }
                        }

                        if (profile.bio.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = profile.bio,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ProfileStatCard(
                                label = "المستوى",
                                value = "${profile.level}",
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = isOwnProfile, onClick = onOpenCoinsLevels)
                            )
                            ProfileStatCard(
                                label = "العملات",
                                value = "${profile.coins}",
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = isOwnProfile, onClick = onOpenCoinsLevels)
                            )
                            ProfileStatCard(
                                label = "السمعة",
                                value = "${profile.reputation}",
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = isOwnProfile, onClick = onOpenCoinsLevels)
                            )
                            ProfileStatCard(
                                label = "الخبرة XP",
                                value = "${profile.xp}",
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(enabled = isOwnProfile, onClick = onOpenCoinsLevels)
                            )
                        }

                        if (profile.badges.isNotEmpty() || profile.isVerified) {
                            Spacer(modifier = Modifier.height(12.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (profile.isVerified) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("موثق", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                                profile.badges.forEach { badge ->
                                    LevelBadge(level = profile.level)
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = badge,
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (isOwnProfile) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onOpenCoinsLevels,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_coins_levels_button")
                            ) {
                                Icon(Icons.Filled.EmojiEvents, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("عرض العملات والمستويات والإنجازات")
                            }
                        }
                    }
                }

                item {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("الأسئلة (${bundle.questions.size})") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("الإجابات (${bundle.answers.size})") }
                        )
                    }
                }

                if (selectedTab == 0) {
                    if (bundle.questions.isEmpty()) {
                        item {
                            EmptyStateView(
                                title = "لا توجد أسئلة منشورة بعد",
                                subtitle = "الأسئلة التي يطرحها هذا العضو ستظهر هنا."
                            )
                        }
                    } else {
                        items(bundle.questions, key = { it.id }) { q ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                QuestionCard(
                                    question = q,
                                    onClick = { onQuestionClick(q.id) },
                                    onLikeClick = { onLikeQuestion(q) },
                                    onShareClick = { onShareQuestion(q) },
                                    onAuthorClick = {}
                                )
                            }
                        }
                    }
                } else {
                    if (bundle.answers.isEmpty()) {
                        item {
                            EmptyStateView(
                                title = "لا توجد إجابات بعد",
                                subtitle = "المشاركات والإجابات المقدمة ستظهر هنا."
                            )
                        }
                    } else {
                        items(bundle.answers, key = { it.id }) { ans ->
                            Card(
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .clickable { onQuestionClick(ans.questionId) }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = ans.content,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "اضغط لعرض السؤال • ${formatShortDate(ans.createdAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
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

@Composable
private fun ProfileStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    currentProfile: UserProfile?,
    isSubmitting: Boolean,
    onBack: () -> Unit,
    onSave: (username: String, fullName: String, bio: String, avatarUri: Uri?, coverUri: Uri?) -> Unit
) {
    var username by remember(currentProfile) { mutableStateOf(currentProfile?.username.orEmpty()) }
    var fullName by remember(currentProfile) { mutableStateOf(currentProfile?.fullName.orEmpty()) }
    var bio by remember(currentProfile) { mutableStateOf(currentProfile?.bio.orEmpty()) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var coverUri by remember { mutableStateOf<Uri?>(null) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) avatarUri = uri
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) coverUri = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تعديل الملف الشخصي", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("edit_profile_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (avatarUri != null) "تم اختيار صورة شخصية" else "تغيير الصورة الشخصية")
                }

                OutlinedButton(
                    onClick = {
                        coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (coverUri != null) "تم اختيار غلاف" else "تغيير صورة الغلاف")
                }
            }

            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("الاسم الكامل") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_fullname_input")
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("اسم المستخدم") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_username_input")
            )

            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("النبذة الشخصية") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_bio_input")
            )

            Button(
                onClick = { onSave(username, fullName, bio, avatarUri, coverUri) },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_profile_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text("حفظ التغييرات")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinsLevelsScreen(
    gamificationState: ResourceState<GamificationBundle>,
    onLoad: () -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) {
        onLoad()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("العملات والمستويات", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("coins_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (gamificationState) {
                is ResourceState.Idle, ResourceState.Loading -> LoadingFeedPlaceholder()
                is ResourceState.Error -> ErrorStateView(message = gamificationState.message, onRetry = onLoad)
                is ResourceState.Success -> {
                    val data = gamificationState.data
                    val p = data.profile
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
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column {
                                            Text(
                                                text = "المستوى الحالي: ${p.level}",
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = "نقاط الخبرة: ${p.xp} / ${p.nextLevelXp} XP",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        Icon(
                                            Icons.Filled.WorkspacePremium,
                                            contentDescription = null,
                                            tint = CoinGold,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    LinearProgressIndicator(
                                        progress = { p.levelProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .clip(RoundedCornerShape(50.dp))
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = MaterialTheme.shapes.medium,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(12.dp)
                                            ) {
                                                Icon(Icons.Filled.MonetizationOn, contentDescription = null, tint = CoinGold)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text("${p.coins}", style = MaterialTheme.typography.titleMedium)
                                                    Text("العملات", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = MaterialTheme.shapes.medium,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(12.dp)
                                            ) {
                                                Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text("${p.reputation}", style = MaterialTheme.typography.titleMedium)
                                                    Text("السمعة", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "حماية الرصيد: يتم احتساب وتحديث العملات ونقاط الخبرة عبر خادم Supabase وسياسات RLS فقط دون أي تعديل مباشر من الجهاز.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        item {
                            Text("الإنجازات والشارات", style = MaterialTheme.typography.titleMedium)
                        }

                        items(data.badges, key = { it.id }) { badge ->
                            BadgeItemCard(badge)
                        }

                        item {
                            Text("سجل العمليات والمكافآت", style = MaterialTheme.typography.titleMedium)
                        }

                        if (data.transactions.isEmpty()) {
                            item {
                                EmptyStateView(
                                    title = "لا توجد عمليات مسجلة بعد",
                                    subtitle = "ابدأ بطرح الأسئلة ومشاركة الإجابات المفيدة لكسب العملات والخبرة."
                                )
                            }
                        } else {
                            items(data.transactions, key = { it.id }) { tx ->
                                Card(
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.padding(14.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(tx.title, style = MaterialTheme.typography.titleSmall)
                                            if (tx.createdAt.isNotBlank()) {
                                                Text(
                                                    formatShortDate(tx.createdAt),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = if (tx.isCredit) "+${tx.amount} عملة" else "-${tx.amount} عملة",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (tx.isCredit) SuccessGreen else MaterialTheme.colorScheme.error
                                            )
                                            if (tx.xpDelta > 0) {
                                                Text(
                                                    text = "+${tx.xpDelta} XP",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
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
    }
}

@Composable
private fun BadgeItemCard(badge: AchievementBadge) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (badge.unlocked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            1.dp,
            if (badge.unlocked) CoinGold.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = if (badge.unlocked) CoinGold else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(badge.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    badge.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                color = if (badge.unlocked) SuccessGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (badge.unlocked) "مكتسبة" else badge.requirementLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (badge.unlocked) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
