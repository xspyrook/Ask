package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.ai.AiAssistantResult
import com.example.data.ai.AiRepository
import com.example.data.ai.PlaceholderAiRepository
import com.example.data.local.AppPreferencesState
import com.example.data.local.SessionPreferences
import com.example.data.model.AppNotification
import com.example.data.model.AuthSession
import com.example.data.model.CoinTransaction
import com.example.data.model.CommentItem
import com.example.data.model.FeedSort
import com.example.data.model.Question
import com.example.data.model.SupabaseSchemaInfo
import com.example.data.model.ThemeMode
import com.example.data.model.UserProfile
import com.example.data.remote.SignUpOutcome
import com.example.data.remote.SupabaseAuthClient
import com.example.data.remote.SupabaseDbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class HalaSualRepository(
    private val context: Context,
    private val prefs: SessionPreferences = SessionPreferences(context),
    private val authClient: SupabaseAuthClient = SupabaseAuthClient(),
    private val dbClient: SupabaseDbClient = SupabaseDbClient(authClient.httpClient),
    val aiRepository: AiRepository = PlaceholderAiRepository()
) {
    val preferencesFlow: Flow<AppPreferencesState> = prefs.preferencesFlow

    suspend fun inspectSchema(forceRefresh: Boolean = false): SupabaseSchemaInfo {
        val session = prefs.getSession()
        return dbClient.inspectSchema(session?.accessToken, forceRefresh)
    }

    suspend fun signIn(email: String, password: String): Result<UserProfile> {
        val res = authClient.signIn(email, password)
        return res.mapCatching { session ->
            prefs.saveSession(session)
            dbClient.inspectSchema(session.accessToken, forceRefresh = true)
            dbClient.fetchProfile(session.userId, session.accessToken)
                .getOrElse { UserProfile(id = session.userId, username = email.substringBefore("@")) }
        }
    }

    suspend fun signUp(
        email: String,
        password: String,
        username: String,
        fullName: String
    ): Result<SignUpOutcome> {
        val res = authClient.signUp(email, password, username, fullName)
        res.getOrNull()?.let { outcome ->
            if (outcome is SignUpOutcome.Authenticated) {
                prefs.saveSession(outcome.session)
                dbClient.inspectSchema(outcome.session.accessToken, forceRefresh = true)
            }
        }
        return res
    }

    suspend fun recoverPassword(email: String): Result<Unit> = authClient.recoverPassword(email)

    suspend fun signOut() {
        val session = prefs.getSession()
        if (session != null) {
            authClient.signOut(session.accessToken)
        }
        prefs.clearSession()
    }

    suspend fun fetchQuestions(
        sort: FeedSort,
        category: String,
        query: String,
        authorId: String? = null
    ): Result<List<Question>> = withValidToken { session ->
        dbClient.fetchQuestions(
            sort = sort,
            category = category,
            searchQuery = query,
            authorId = authorId,
            accessToken = session?.accessToken,
            currentUserId = session?.userId
        )
    }

    suspend fun fetchQuestionDetail(questionId: String): Result<Pair<Question, List<CommentItem>>> =
        withValidToken { session ->
            dbClient.fetchQuestionDetail(
                questionId = questionId,
                accessToken = session?.accessToken,
                currentUserId = session?.userId
            )
        }

    suspend fun createQuestion(
        title: String,
        body: String,
        category: String,
        privacy: String,
        imageUri: Uri?
    ): Result<Question> = requireAuthenticated { session ->
        val uploadedUrl = if (imageUri != null) {
            uploadUri(imageUri, "questions", session.accessToken).getOrThrow()
        } else null
        dbClient.createQuestion(
            userId = session.userId,
            title = title,
            body = body,
            category = category,
            privacy = privacy,
            imageUrl = uploadedUrl,
            accessToken = session.accessToken
        )
    }

    suspend fun updateQuestion(
        questionId: String,
        title: String,
        body: String,
        category: String,
        privacy: String,
        imageUri: Uri?,
        existingImageUrl: String?
    ): Result<Unit> = requireAuthenticated { session ->
        val finalUrl = if (imageUri != null) {
            uploadUri(imageUri, "questions", session.accessToken).getOrThrow()
        } else existingImageUrl
        dbClient.updateQuestion(
            questionId = questionId,
            title = title,
            body = body,
            category = category,
            privacy = privacy,
            imageUrl = finalUrl,
            accessToken = session.accessToken
        )
    }

    suspend fun deleteQuestion(questionId: String): Result<Unit> = requireAuthenticated { session ->
        dbClient.deleteQuestion(questionId, session.accessToken)
    }

    suspend fun addComment(
        questionId: String,
        content: String,
        parentId: String?,
        isAnswer: Boolean
    ): Result<CommentItem> = requireAuthenticated { session ->
        dbClient.addComment(
            questionId = questionId,
            userId = session.userId,
            content = content,
            parentId = parentId,
            isAnswer = isAnswer,
            accessToken = session.accessToken
        )
    }

    suspend fun toggleQuestionLike(questionId: String, currentlyLiked: Boolean): Result<Boolean> =
        requireAuthenticated { session ->
            dbClient.toggleQuestionLike(questionId, session.userId, currentlyLiked, session.accessToken)
        }

    suspend fun toggleCommentLike(commentId: String, currentlyLiked: Boolean): Result<Boolean> =
        requireAuthenticated { session ->
            dbClient.toggleCommentLike(commentId, session.userId, currentlyLiked, session.accessToken)
        }

    suspend fun markBestAnswer(questionId: String, commentId: String): Result<Unit> =
        requireAuthenticated { session ->
            dbClient.markBestAnswer(questionId, commentId, session.accessToken)
        }

    suspend fun reportContent(questionId: String?, commentId: String?, reason: String): Result<String> =
        requireAuthenticated { session ->
            dbClient.reportContent(session.userId, questionId, commentId, reason, session.accessToken)
        }

    suspend fun fetchProfile(userId: String): Result<UserProfile> = withValidToken { session ->
        dbClient.fetchProfile(userId, session?.accessToken)
    }

    suspend fun updateProfile(
        username: String,
        fullName: String,
        bio: String,
        avatarUri: Uri?,
        coverUri: Uri?
    ): Result<UserProfile> = requireAuthenticated { session ->
        val avatarUrl = avatarUri?.let { uploadUri(it, "avatars", session.accessToken).getOrThrow() }
        val coverUrl = coverUri?.let { uploadUri(it, "covers", session.accessToken).getOrThrow() }
        dbClient.updateProfile(
            userId = session.userId,
            username = username,
            fullName = fullName,
            bio = bio,
            avatarUrl = avatarUrl,
            coverUrl = coverUrl,
            accessToken = session.accessToken
        )
    }

    suspend fun fetchUserComments(userId: String): Result<List<CommentItem>> = withValidToken { session ->
        dbClient.fetchUserComments(userId, session?.accessToken)
    }

    suspend fun fetchNotifications(): Result<List<AppNotification>> = requireAuthenticated { session ->
        dbClient.fetchNotifications(session.userId, session.accessToken)
    }

    suspend fun markNotificationRead(id: String): Result<Unit> = requireAuthenticated { session ->
        dbClient.markNotificationRead(id, session.accessToken)
    }

    suspend fun fetchTransactions(): Result<List<CoinTransaction>> = requireAuthenticated { session ->
        dbClient.fetchCoinTransactions(session.userId, session.accessToken)
    }

    suspend fun setThemeMode(mode: ThemeMode) = prefs.setThemeMode(mode)
    suspend fun setNotificationsEnabled(enabled: Boolean) = prefs.setNotificationsEnabled(enabled)
    suspend fun setAnswerAlertsEnabled(enabled: Boolean) = prefs.setAnswerAlertsEnabled(enabled)
    suspend fun setLikeAlertsEnabled(enabled: Boolean) = prefs.setLikeAlertsEnabled(enabled)
    suspend fun markOnboardingCompleted() = prefs.markOnboardingCompleted()

    // AI methods delegated through AiRepository interface
    suspend fun aiRefineQuestion(title: String, details: String): AiAssistantResult =
        aiRepository.suggestQuestionRefinement(title, details)

    suspend fun aiImproveAnswer(questionTitle: String, draft: String): AiAssistantResult =
        aiRepository.improveAnswer(questionTitle, draft)

    suspend fun aiSummarizeDiscussion(questionTitle: String, answers: List<String>): AiAssistantResult =
        aiRepository.summarizeDiscussion(questionTitle, answers)

    suspend fun aiSuggestSimilar(query: String, existingTitles: List<String>): AiAssistantResult =
        aiRepository.suggestSimilarQuestions(query, existingTitles)

    suspend fun aiAssistSearch(query: String): AiAssistantResult =
        aiRepository.assistSearch(query)

    private suspend fun uploadUri(uri: Uri, folder: String, accessToken: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri) ?: "image/jpeg"
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("تعذر قراءة ملف الصورة المختار.")
                dbClient.uploadImage(bytes, mimeType, folder, accessToken).getOrThrow()
            }
        }

    private suspend fun <T> withValidToken(block: suspend (AuthSession?) -> Result<T>): Result<T> {
        val current = prefs.getSession()
        val firstAttempt = block(current)
        if (firstAttempt.isFailure && current != null && current.refreshToken.isNotBlank()) {
            val msg = firstAttempt.exceptionOrNull()?.message.orEmpty()
            if (msg.contains("انتهت صلاحية") || msg.contains("401") || msg.contains("JWT")) {
                val refreshed = authClient.refreshSession(current.refreshToken).getOrNull()
                if (refreshed != null) {
                    prefs.saveSession(refreshed)
                    return block(refreshed)
                }
            }
        }
        return firstAttempt
    }

    private suspend fun <T> requireAuthenticated(block: suspend (AuthSession) -> Result<T>): Result<T> {
        val current = prefs.getSession()
            ?: return Result.failure(IllegalStateException("يرجى تسجيل الدخول أولاً لتنفيذ هذا الإجراء."))
        val firstAttempt = block(current)
        if (firstAttempt.isFailure && current.refreshToken.isNotBlank()) {
            val msg = firstAttempt.exceptionOrNull()?.message.orEmpty()
            if (msg.contains("انتهت صلاحية") || msg.contains("401") || msg.contains("JWT")) {
                val refreshed = authClient.refreshSession(current.refreshToken).getOrNull()
                if (refreshed != null) {
                    prefs.saveSession(refreshed)
                    return block(refreshed)
                }
            }
        }
        return firstAttempt
    }
}
