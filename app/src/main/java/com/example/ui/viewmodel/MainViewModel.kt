package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiAssistantResult
import com.example.data.local.AppPreferencesState
import com.example.data.model.AchievementBadge
import com.example.data.model.AppCategories
import com.example.data.model.AppNotification
import com.example.data.model.CoinTransaction
import com.example.data.model.CommentItem
import com.example.data.model.FeedSort
import com.example.data.model.Question
import com.example.data.model.ResourceState
import com.example.data.model.SupabaseSchemaInfo
import com.example.data.model.ThemeMode
import com.example.data.model.UserProfile
import com.example.data.remote.SignUpOutcome
import com.example.data.remote.SupabaseConfig
import com.example.data.repository.HalaSualRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuestionDetailData(
    val question: Question,
    val comments: List<CommentItem>
)

data class ProfileBundle(
    val profile: UserProfile,
    val questions: List<Question>,
    val answers: List<CommentItem>
)

data class GamificationBundle(
    val profile: UserProfile,
    val transactions: List<CoinTransaction>,
    val badges: List<AchievementBadge>
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HalaSualRepository(application.applicationContext)

    private val _prefsState = MutableStateFlow(AppPreferencesState())
    val prefsState: StateFlow<AppPreferencesState> = _prefsState.asStateFlow()

    private val _schemaInfo = MutableStateFlow(SupabaseSchemaInfo())
    val schemaInfo: StateFlow<SupabaseSchemaInfo> = _schemaInfo.asStateFlow()

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    val currentUserProfile: StateFlow<UserProfile?> = _currentUserProfile.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    // Home Feed
    private val _selectedSort = MutableStateFlow(FeedSort.RECENT)
    val selectedSort: StateFlow<FeedSort> = _selectedSort.asStateFlow()

    private val _selectedCategory = MutableStateFlow(AppCategories.ALL)
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _homeFeedState = MutableStateFlow<ResourceState<List<Question>>>(ResourceState.Idle)
    val homeFeedState: StateFlow<ResourceState<List<Question>>> = _homeFeedState.asStateFlow()

    // Explore
    private val _exploreCategory = MutableStateFlow(AppCategories.ALL)
    val exploreCategory: StateFlow<String> = _exploreCategory.asStateFlow()

    private val _exploreState = MutableStateFlow<ResourceState<List<Question>>>(ResourceState.Idle)
    val exploreState: StateFlow<ResourceState<List<Question>>> = _exploreState.asStateFlow()

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchState = MutableStateFlow<ResourceState<List<Question>>>(ResourceState.Idle)
    val searchState: StateFlow<ResourceState<List<Question>>> = _searchState.asStateFlow()

    // Question Detail
    private val _detailState = MutableStateFlow<ResourceState<QuestionDetailData>>(ResourceState.Idle)
    val detailState: StateFlow<ResourceState<QuestionDetailData>> = _detailState.asStateFlow()

    // Notifications
    private val _notificationsState = MutableStateFlow<ResourceState<List<AppNotification>>>(ResourceState.Idle)
    val notificationsState: StateFlow<ResourceState<List<AppNotification>>> = _notificationsState.asStateFlow()

    // Profile
    private val _profileState = MutableStateFlow<ResourceState<ProfileBundle>>(ResourceState.Idle)
    val profileState: StateFlow<ResourceState<ProfileBundle>> = _profileState.asStateFlow()

    // Coins & Levels
    private val _gamificationState = MutableStateFlow<ResourceState<GamificationBundle>>(ResourceState.Idle)
    val gamificationState: StateFlow<ResourceState<GamificationBundle>> = _gamificationState.asStateFlow()

    // AI Assistant state
    private val _aiResult = MutableStateFlow<AiAssistantResult?>(null)
    val aiResult: StateFlow<AiAssistantResult?> = _aiResult.asStateFlow()

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading: StateFlow<Boolean> = _aiLoading.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    val isSupabaseConfigured: Boolean
        get() = SupabaseConfig.isConfigured

    init {
        viewModelScope.launch {
            repository.preferencesFlow.collectLatest { state ->
                val prevUserId = _prefsState.value.session?.userId
                _prefsState.value = state
                if (state.session != null && state.session.userId != prevUserId) {
                    loadCurrentProfile(state.session.userId)
                } else if (state.session == null) {
                    _currentUserProfile.value = null
                }
            }
        }
        viewModelScope.launch {
            if (SupabaseConfig.isConfigured) {
                _schemaInfo.value = repository.inspectSchema()
                loadHomeFeed()
            }
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun clearAiResult() {
        _aiResult.value = null
    }

    fun inspectSchemaNow() {
        viewModelScope.launch {
            _schemaInfo.value = repository.inspectSchema(forceRefresh = true)
            _toastEvents.emit(
                if (_schemaInfo.value.inspected) "تم فحص مخطط Supabase بنجاح"
                else (_schemaInfo.value.rawError ?: "تعذر فحص المخطط")
            )
        }
    }

    fun signIn(email: String, password: String, onSuccess: () -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            _authError.value = "يرجى إدخال البريد الإلكتروني وكلمة المرور."
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            repository.signIn(email, password)
                .onSuccess { profile ->
                    _currentUserProfile.value = profile
                    _schemaInfo.value = repository.inspectSchema(forceRefresh = true)
                    loadHomeFeed()
                    onSuccess()
                }
                .onFailure { err ->
                    _authError.value = err.message ?: "تعذر تسجيل الدخول."
                }
            _authLoading.value = false
        }
    }

    fun signUp(
        email: String,
        password: String,
        username: String,
        fullName: String,
        onAuthenticated: () -> Unit,
        onConfirmationNeeded: (String) -> Unit
    ) {
        if (email.isBlank() || password.length < 6 || username.isBlank()) {
            _authError.value = "يرجى تعبئة اسم المستخدم والبريد الإلكتروني وكلمة مرور من 6 أحرف على الأقل."
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            repository.signUp(email, password, username, fullName)
                .onSuccess { outcome ->
                    when (outcome) {
                        is SignUpOutcome.Authenticated -> {
                            loadCurrentProfile(outcome.session.userId)
                            loadHomeFeed()
                            onAuthenticated()
                        }
                        is SignUpOutcome.EmailConfirmationRequired -> {
                            onConfirmationNeeded("تم إنشاء الحساب! يرجى تأكيد بريدك (${outcome.email}) ثم تسجيل الدخول.")
                        }
                    }
                }
                .onFailure { err ->
                    _authError.value = err.message ?: "تعذر إنشاء الحساب."
                }
            _authLoading.value = false
        }
    }

    fun recoverPassword(email: String, onSent: () -> Unit) {
        if (email.isBlank()) {
            viewModelScope.launch { _toastEvents.emit("أدخل البريد الإلكتروني أولاً.") }
            return
        }
        viewModelScope.launch {
            _authLoading.value = true
            repository.recoverPassword(email)
                .onSuccess {
                    _toastEvents.emit("تم إرسال رابط استعادة كلمة المرور إلى بريدك الإلكتروني.")
                    onSent()
                }
                .onFailure { err ->
                    _toastEvents.emit(err.message ?: "تعذر إرسال رابط الاستعادة.")
                }
            _authLoading.value = false
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            repository.signOut()
            _currentUserProfile.value = null
            onSignedOut()
        }
    }

    fun loadHomeFeed(
        sort: FeedSort = _selectedSort.value,
        category: String = _selectedCategory.value
    ) {
        _selectedSort.value = sort
        _selectedCategory.value = category
        viewModelScope.launch {
            _homeFeedState.value = ResourceState.Loading
            repository.fetchQuestions(sort = sort, category = category, query = "")
                .onSuccess { list -> _homeFeedState.value = ResourceState.Success(list) }
                .onFailure { err -> _homeFeedState.value = ResourceState.Error(err.message ?: "تعذر تحميل الأسئلة.") }
        }
    }

    fun loadExplore(category: String = _exploreCategory.value) {
        _exploreCategory.value = category
        viewModelScope.launch {
            _exploreState.value = ResourceState.Loading
            repository.fetchQuestions(sort = FeedSort.POPULAR, category = category, query = "")
                .onSuccess { list -> _exploreState.value = ResourceState.Success(list) }
                .onFailure { err -> _exploreState.value = ResourceState.Error(err.message ?: "تعذر تحميل صفحة الاستكشاف.") }
        }
    }

    fun performSearch(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            _searchState.value = ResourceState.Loading
            repository.fetchQuestions(sort = FeedSort.RECENT, category = AppCategories.ALL, query = query)
                .onSuccess { list -> _searchState.value = ResourceState.Success(list) }
                .onFailure { err -> _searchState.value = ResourceState.Error(err.message ?: "فشل البحث.") }
        }
    }

    fun loadQuestionDetail(questionId: String) {
        viewModelScope.launch {
            _detailState.value = ResourceState.Loading
            repository.fetchQuestionDetail(questionId)
                .onSuccess { (q, c) -> _detailState.value = ResourceState.Success(QuestionDetailData(q, c)) }
                .onFailure { err -> _detailState.value = ResourceState.Error(err.message ?: "تعذر تحميل تفاصيل السؤال.") }
        }
    }

    fun submitQuestion(
        editQuestionId: String?,
        title: String,
        body: String,
        category: String,
        privacy: String,
        imageUri: Uri?,
        existingImageUrl: String?,
        onSuccess: (String) -> Unit
    ) {
        if (title.isBlank() || body.isBlank()) {
            viewModelScope.launch { _toastEvents.emit("يرجى كتابة عنوان السؤال والوصف التفصيلي.") }
            return
        }
        viewModelScope.launch {
            _isSubmitting.value = true
            if (editQuestionId.isNullOrBlank()) {
                repository.createQuestion(title, body, category, privacy, imageUri)
                    .onSuccess { created ->
                        _toastEvents.emit("تم نشر سؤالك بنجاح!")
                        loadHomeFeed()
                        onSuccess(created.id)
                    }
                    .onFailure { err -> _toastEvents.emit(err.message ?: "تعذر نشر السؤال.") }
            } else {
                repository.updateQuestion(editQuestionId, title, body, category, privacy, imageUri, existingImageUrl)
                    .onSuccess {
                        _toastEvents.emit("تم تحديث السؤال بنجاح!")
                        loadHomeFeed()
                        onSuccess(editQuestionId)
                    }
                    .onFailure { err -> _toastEvents.emit(err.message ?: "تعذر تحديث السؤال.") }
            }
            _isSubmitting.value = false
        }
    }

    fun deleteQuestion(questionId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            _isSubmitting.value = true
            repository.deleteQuestion(questionId)
                .onSuccess {
                    _toastEvents.emit("تم حذف السؤال.")
                    loadHomeFeed()
                    onDeleted()
                }
                .onFailure { err -> _toastEvents.emit(err.message ?: "تعذر حذف السؤال.") }
            _isSubmitting.value = false
        }
    }

    fun addCommentOrAnswer(questionId: String, content: String, parentId: String? = null) {
        if (content.isBlank()) {
            viewModelScope.launch { _toastEvents.emit("يرجى كتابة نص الإجابة أو التعليق.") }
            return
        }
        viewModelScope.launch {
            _isSubmitting.value = true
            repository.addComment(questionId, content, parentId, isAnswer = parentId == null)
                .onSuccess {
                    _toastEvents.emit("تمت إضافة مشاركتك بنجاح.")
                    loadQuestionDetail(questionId)
                    loadHomeFeed()
                }
                .onFailure { err -> _toastEvents.emit(err.message ?: "تعذر إرسال المشاركة.") }
            _isSubmitting.value = false
        }
    }

    fun toggleQuestionLike(question: Question) {
        viewModelScope.launch {
            repository.toggleQuestionLike(question.id, question.isLikedByMe)
                .onSuccess { nowLiked ->
                    val delta = if (nowLiked) 1 else -1
                    updateQuestionInFeeds(question.id) { q ->
                        q.copy(
                            isLikedByMe = nowLiked,
                            likesCount = (q.likesCount + delta).coerceAtLeast(0)
                        )
                    }
                    val curDetail = _detailState.value
                    if (curDetail is ResourceState.Success && curDetail.data.question.id == question.id) {
                        _detailState.value = ResourceState.Success(
                            curDetail.data.copy(
                                question = curDetail.data.question.copy(
                                    isLikedByMe = nowLiked,
                                    likesCount = (curDetail.data.question.likesCount + delta).coerceAtLeast(0)
                                )
                            )
                        )
                    }
                }
                .onFailure { err -> _toastEvents.emit(err.message ?: "تعذر تحديث الإعجاب.") }
        }
    }

    fun toggleCommentLike(comment: CommentItem) {
        viewModelScope.launch {
            repository.toggleCommentLike(comment.id, comment.isLikedByMe)
                .onSuccess { nowLiked ->
                    val delta = if (nowLiked) 1 else -1
                    val curDetail = _detailState.value
                    if (curDetail is ResourceState.Success) {
                        val updatedComments = curDetail.data.comments.map { item ->
                            if (item.id == comment.id) {
                                item.copy(
                                    isLikedByMe = nowLiked,
                                    likesCount = (item.likesCount + delta).coerceAtLeast(0)
                                )
                            } else item
                        }
                        _detailState.value = ResourceState.Success(
                            curDetail.data.copy(comments = updatedComments)
                        )
                    }
                }
                .onFailure { err -> _toastEvents.emit(err.message ?: "تعذر تحديث الإعجاب.") }
        }
    }

    fun markBestAnswer(questionId: String, commentId: String) {
        viewModelScope.launch {
            repository.markBestAnswer(questionId, commentId)
                .onSuccess {
                    _toastEvents.emit("تم اعتماد أفضل إجابة بنجاح.")
                    loadQuestionDetail(questionId)
                }
                .onFailure { err -> _toastEvents.emit(err.message ?: "تعذر اعتماد أفضل إجابة.") }
        }
    }

    fun reportContent(questionId: String?, commentId: String?, reason: String) {
        if (reason.isBlank()) return
        viewModelScope.launch {
            repository.reportContent(questionId, commentId, reason)
                .onSuccess { msg -> _toastEvents.emit(msg) }
                .onFailure { err -> _toastEvents.emit(err.message ?: "تعذر إرسال البلاغ.") }
        }
    }

    fun loadProfile(userId: String?) {
        val targetId = userId ?: _prefsState.value.session?.userId
        if (targetId.isNullOrBlank()) {
            _profileState.value = ResourceState.Error("سجّل الدخول لعرض ملفك الشخصي.")
            return
        }
        viewModelScope.launch {
            _profileState.value = ResourceState.Loading
            val profRes = repository.fetchProfile(targetId)
            if (profRes.isFailure) {
                _profileState.value = ResourceState.Error(profRes.exceptionOrNull()?.message ?: "تعذر تحميل الملف الشخصي.")
                return@launch
            }
            val profile = profRes.getOrThrow()
            if (targetId == _prefsState.value.session?.userId) {
                _currentUserProfile.value = profile
            }
            val questions = repository.fetchQuestions(FeedSort.RECENT, AppCategories.ALL, "", authorId = targetId)
                .getOrDefault(emptyList())
            val answers = repository.fetchUserComments(targetId).getOrDefault(emptyList())
            _profileState.value = ResourceState.Success(ProfileBundle(profile, questions, answers))
        }
    }

    fun updateProfile(
        username: String,
        fullName: String,
        bio: String,
        avatarUri: Uri?,
        coverUri: Uri?,
        onSuccess: () -> Unit
    ) {
        if (username.isBlank()) {
            viewModelScope.launch { _toastEvents.emit("يرجى إدخال اسم المستخدم.") }
            return
        }
        viewModelScope.launch {
            _isSubmitting.value = true
            repository.updateProfile(username, fullName, bio, avatarUri, coverUri)
                .onSuccess { updated ->
                    _currentUserProfile.value = updated
                    _toastEvents.emit("تم تحديث الملف الشخصي بنجاح.")
                    loadProfile(updated.id)
                    onSuccess()
                }
                .onFailure { err -> _toastEvents.emit(err.message ?: "تعذر تحديث الملف الشخصي.") }
            _isSubmitting.value = false
        }
    }

    fun loadNotifications() {
        viewModelScope.launch {
            _notificationsState.value = ResourceState.Loading
            repository.fetchNotifications()
                .onSuccess { list -> _notificationsState.value = ResourceState.Success(list) }
                .onFailure { err -> _notificationsState.value = ResourceState.Error(err.message ?: "تعذر تحميل الإشعارات.") }
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            repository.markNotificationRead(notificationId)
            val cur = _notificationsState.value
            if (cur is ResourceState.Success) {
                _notificationsState.value = ResourceState.Success(
                    cur.data.map { if (it.id == notificationId) it.copy(isRead = true) else it }
                )
            }
        }
    }

    fun loadGamification() {
        val uid = _prefsState.value.session?.userId
        if (uid.isNullOrBlank()) {
            _gamificationState.value = ResourceState.Error("سجّل الدخول لمتابعة عملاتك ومستواك.")
            return
        }
        viewModelScope.launch {
            _gamificationState.value = ResourceState.Loading
            val prof = repository.fetchProfile(uid).getOrElse {
                _gamificationState.value = ResourceState.Error(it.message ?: "تعذر تحميل البيانات.")
                return@launch
            }
            _currentUserProfile.value = prof
            val tx = repository.fetchTransactions().getOrDefault(emptyList())
            val badges = buildBadges(prof, tx)
            _gamificationState.value = ResourceState.Success(GamificationBundle(prof, tx, badges))
        }
    }

    // AI Assistant actions
    fun requestAiQuestionRefinement(title: String, details: String) {
        viewModelScope.launch {
            _aiLoading.value = true
            _aiResult.value = repository.aiRefineQuestion(title, details)
            _aiLoading.value = false
        }
    }

    fun requestAiImproveAnswer(questionTitle: String, draft: String) {
        viewModelScope.launch {
            _aiLoading.value = true
            _aiResult.value = repository.aiImproveAnswer(questionTitle, draft)
            _aiLoading.value = false
        }
    }

    fun requestAiSummarize(questionTitle: String, answers: List<String>) {
        viewModelScope.launch {
            _aiLoading.value = true
            _aiResult.value = repository.aiSummarizeDiscussion(questionTitle, answers)
            _aiLoading.value = false
        }
    }

    fun requestAiSearchAssist(query: String) {
        viewModelScope.launch {
            _aiLoading.value = true
            val existingTitles = (_homeFeedState.value as? ResourceState.Success)?.data?.map { it.title }.orEmpty()
            val similar = repository.aiSuggestSimilar(query, existingTitles)
            val searchTips = repository.aiAssistSearch(query)
            _aiResult.value = similar.copy(
                content = "${searchTips.content}\n\n${similar.content}",
                suggestions = (searchTips.suggestions + similar.suggestions).distinct()
            )
            _aiLoading.value = false
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setNotificationsEnabled(enabled) }
    }

    fun setAnswerAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setAnswerAlertsEnabled(enabled) }
    }

    fun setLikeAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setLikeAlertsEnabled(enabled) }
    }

    fun markOnboardingCompleted() {
        viewModelScope.launch { repository.markOnboardingCompleted() }
    }

    private fun loadCurrentProfile(userId: String) {
        viewModelScope.launch {
            repository.fetchProfile(userId).onSuccess { _currentUserProfile.value = it }
        }
    }

    private fun updateQuestionInFeeds(questionId: String, transform: (Question) -> Question) {
        (_homeFeedState.value as? ResourceState.Success)?.let { s ->
            _homeFeedState.value = ResourceState.Success(
                s.data.map { if (it.id == questionId) transform(it) else it }
            )
        }
        (_exploreState.value as? ResourceState.Success)?.let { s ->
            _exploreState.value = ResourceState.Success(
                s.data.map { if (it.id == questionId) transform(it) else it }
            )
        }
        (_searchState.value as? ResourceState.Success)?.let { s ->
            _searchState.value = ResourceState.Success(
                s.data.map { if (it.id == questionId) transform(it) else it }
            )
        }
    }

    private fun buildBadges(profile: UserProfile, tx: List<CoinTransaction>): List<AchievementBadge> {
        return listOf(
            AchievementBadge(
                id = "pioneer",
                title = "بداية المعرفة",
                description = "الانضمام إلى مجتمع هلا سؤال وإكمال الملف الشخصي",
                unlocked = profile.xp >= 0,
                requirementLabel = "متاح عند التسجيل"
            ),
            AchievementBadge(
                id = "contributor",
                title = "مساهم نشط",
                description = "الوصول إلى 250 نقطة خبرة عبر الأسئلة والإجابات",
                unlocked = profile.xp >= 250 || tx.size >= 3,
                requirementLabel = "250 XP"
            ),
            AchievementBadge(
                id = "scholar",
                title = "مرجع موثوق",
                description = "الوصول إلى المستوى 3 وسمعة متميزة في المجتمع",
                unlocked = profile.level >= 3 || profile.reputation >= 100,
                requirementLabel = "المستوى 3"
            ),
            AchievementBadge(
                id = "verified",
                title = "حساب موثّق",
                description = "شارة التوثيق الرسمية الممنوحة من إدارة المنصة",
                unlocked = profile.isVerified,
                requirementLabel = "توثيق رسمي"
            )
        )
    }
}
