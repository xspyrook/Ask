package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.ai.AiAssistantResult
import com.example.data.model.CommentItem
import com.example.data.model.Question
import com.example.data.model.ResourceState
import com.example.ui.components.AiResultCard
import com.example.ui.components.EmptyStateView
import com.example.ui.components.ErrorStateView
import com.example.ui.components.LevelBadge
import com.example.ui.components.LoadingFeedPlaceholder
import com.example.ui.components.UserAvatar
import com.example.ui.components.VerifiedBadge
import com.example.ui.components.formatShortDate
import com.example.ui.theme.LikeCoral
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.QuestionDetailData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionDetailScreen(
    questionId: String,
    currentUserId: String?,
    detailState: ResourceState<QuestionDetailData>,
    isSubmitting: Boolean,
    aiResult: AiAssistantResult?,
    aiLoading: Boolean,
    onLoadDetail: (String) -> Unit,
    onBack: () -> Unit,
    onEditQuestion: (String) -> Unit,
    onDeleteQuestion: (String) -> Unit,
    onToggleQuestionLike: (Question) -> Unit,
    onToggleCommentLike: (CommentItem) -> Unit,
    onMarkBestAnswer: (String, String) -> Unit,
    onSubmitComment: (String, String, String?) -> Unit,
    onShareQuestion: (Question) -> Unit,
    onReportContent: (String?, String?, String) -> Unit,
    onAuthorClick: (String) -> Unit,
    onAiSummarize: (String, List<String>) -> Unit,
    onAiImproveAnswer: (String, String) -> Unit,
    onDismissAi: () -> Unit
) {
    LaunchedEffect(questionId) {
        onLoadDetail(questionId)
    }

    var answerDraft by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<CommentItem?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<Pair<String?, String?>?>(null) }
    var reportReason by remember { mutableStateOf("") }

    val loadedData = (detailState as? ResourceState.Success)?.data
    val isOwner = loadedData != null && currentUserId != null && loadedData.question.userId == currentUserId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تفاصيل السؤال", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (loadedData != null) {
                        IconButton(onClick = { onShareQuestion(loadedData.question) }) {
                            Icon(Icons.Filled.Share, contentDescription = "مشاركة")
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "خيارات إضافية")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                if (isOwner) {
                                    DropdownMenuItem(
                                        text = { Text("تعديل السؤال") },
                                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            onEditQuestion(loadedData.question.id)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("حذف السؤال", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        },
                                        onClick = {
                                            showMenu = false
                                            showDeleteConfirm = true
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("الإبلاغ عن محتوى مخالف") },
                                    leadingIcon = { Icon(Icons.Filled.Flag, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        reportTarget = loadedData.question.id to null
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (loadedData != null) {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        if (replyTarget != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                Text(
                                    text = "رد على: ${replyTarget!!.authorName}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(onClick = { replyTarget = null }) {
                                    Text("إلغاء")
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = {
                                    onAiImproveAnswer(loadedData.question.title, answerDraft)
                                },
                                modifier = Modifier.testTag("detail_ai_improve_button")
                            ) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = "تحسين الإجابة بالذكاء الاصطناعي",
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            OutlinedTextField(
                                value = answerDraft,
                                onValueChange = { answerDraft = it },
                                placeholder = {
                                    Text(if (replyTarget == null) "اكتب إجابتك هنا..." else "اكتب ردك هنا...")
                                },
                                maxLines = 3,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("detail_answer_input")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    val text = answerDraft.trim()
                                    if (text.isNotBlank()) {
                                        onSubmitComment(questionId, text, replyTarget?.id)
                                        answerDraft = ""
                                        replyTarget = null
                                    }
                                },
                                enabled = !isSubmitting && answerDraft.isNotBlank(),
                                modifier = Modifier.testTag("detail_send_answer_button")
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "إرسال",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (detailState) {
                is ResourceState.Idle, ResourceState.Loading -> LoadingFeedPlaceholder()
                is ResourceState.Error -> ErrorStateView(
                    message = detailState.message,
                    onRetry = { onLoadDetail(questionId) }
                )
                is ResourceState.Success -> {
                    val question = detailState.data.question
                    val comments = detailState.data.comments

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            QuestionHeroCard(
                                question = question,
                                onAuthorClick = { onAuthorClick(question.userId) },
                                onLikeClick = { onToggleQuestionLike(question) },
                                onSummarizeClick = {
                                    onAiSummarize(question.title, comments.map { it.content })
                                }
                            )
                        }

                        item {
                            AiResultCard(
                                result = aiResult,
                                isLoading = aiLoading,
                                onDismiss = onDismissAi
                            )
                        }

                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "الإجابات والتعليقات (${comments.size})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }

                        if (comments.isEmpty()) {
                            item {
                                EmptyStateView(
                                    title = "لا توجد إجابات بعد",
                                    subtitle = "كن أول من يقدّم إجابة مفيدة ويساهم في إثراء المعرفة!"
                                )
                            }
                        } else {
                            items(comments, key = { it.id }) { comment ->
                                CommentCard(
                                    comment = comment,
                                    isQuestionOwner = isOwner,
                                    onLikeClick = { onToggleCommentLike(comment) },
                                    onReplyClick = { replyTarget = comment },
                                    onMarkBestClick = { onMarkBestAnswer(question.id, comment.id) },
                                    onReportClick = { reportTarget = question.id to comment.id },
                                    onAuthorClick = { onAuthorClick(comment.userId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm && loadedData != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("تأكيد حذف السؤال") },
            text = { Text("هل أنت متأكد من حذف هذا السؤال؟ لا يمكن التراجع عن هذا الإجراء.") },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirm = false
                    onDeleteQuestion(loadedData.question.id)
                }) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("إلغاء") }
            }
        )
    }

    if (reportTarget != null) {
        AlertDialog(
            onDismissRequest = { reportTarget = null },
            title = { Text("الإبلاغ عن محتوى مخالف") },
            text = {
                OutlinedTextField(
                    value = reportReason,
                    onValueChange = { reportReason = it },
                    label = { Text("سبب البلاغ") },
                    placeholder = { Text("اذكر سبب المخالفة باختصار...") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (qId, cId) = reportTarget!!
                        onReportContent(qId, cId, reportReason.ifBlank { "محتوى غير مناسب" })
                        reportReason = ""
                        reportTarget = null
                    }
                ) {
                    Text("إرسال البلاغ")
                }
            },
            dismissButton = {
                TextButton(onClick = { reportTarget = null }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun QuestionHeroCard(
    question: Question,
    onAuthorClick: () -> Unit,
    onLikeClick: () -> Unit,
    onSummarizeClick: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onAuthorClick)
                ) {
                    UserAvatar(question.authorName, question.authorAvatarUrl, size = 44.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(question.authorName, style = MaterialTheme.typography.titleSmall)
                            if (question.authorVerified) {
                                Spacer(modifier = Modifier.width(4.dp))
                                VerifiedBadge()
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            LevelBadge(question.authorLevel)
                        }
                        if (question.createdAt.isNotBlank()) {
                            Text(
                                text = formatShortDate(question.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = question.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = question.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = question.body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!question.imageUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                AsyncImage(
                    model = question.imageUrl,
                    contentDescription = "صورة السؤال",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onLikeClick)
                            .padding(horizontal = 8.dp)
                            .testTag("detail_like_button")
                    ) {
                        Icon(
                            imageVector = if (question.isLikedByMe) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "إعجاب",
                            tint = if (question.isLikedByMe) LikeCoral else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${question.likesCount}")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Visibility,
                            contentDescription = "المشاهدات",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${question.viewsCount} مشاهدة", style = MaterialTheme.typography.bodySmall)
                    }
                }

                OutlinedButton(
                    onClick = onSummarizeClick,
                    modifier = Modifier.testTag("detail_ai_summarize_button")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تلخيص النقاش")
                }
            }
        }
    }
}

@Composable
private fun CommentCard(
    comment: CommentItem,
    isQuestionOwner: Boolean,
    onLikeClick: () -> Unit,
    onReplyClick: () -> Unit,
    onMarkBestClick: () -> Unit,
    onReportClick: () -> Unit,
    onAuthorClick: () -> Unit
) {
    val borderColor = if (comment.isBestAnswer) SuccessGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (comment.isBestAnswer) {
                SuccessGreen.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(if (comment.isBestAnswer) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (comment.parentId != null) 20.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (comment.isBestAnswer) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "أفضل إجابة معتمدة",
                        style = MaterialTheme.typography.labelLarge,
                        color = SuccessGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onAuthorClick)
            ) {
                UserAvatar(comment.authorName, comment.authorAvatarUrl, size = 34.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(comment.authorName, style = MaterialTheme.typography.titleSmall)
                        if (comment.authorVerified) {
                            Spacer(modifier = Modifier.width(4.dp))
                            VerifiedBadge()
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        LevelBadge(comment.authorLevel)
                    }
                    if (comment.createdAt.isNotBlank()) {
                        Text(
                            text = formatShortDate(comment.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onLikeClick) {
                        Icon(
                            imageVector = if (comment.isLikedByMe) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "إعجاب بالإجابة",
                            tint = if (comment.isLikedByMe) LikeCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${comment.likesCount}")
                    }
                    TextButton(onClick = onReplyClick) {
                        Text("رد")
                    }
                    if (isQuestionOwner && !comment.isBestAnswer && comment.parentId == null) {
                        TextButton(onClick = onMarkBestClick) {
                            Text("اعتماد كأفضل إجابة", color = SuccessGreen)
                        }
                    }
                }

                IconButton(onClick = onReportClick) {
                    Icon(
                        Icons.Filled.Flag,
                        contentDescription = "إبلاغ",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
