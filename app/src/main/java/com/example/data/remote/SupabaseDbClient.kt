package com.example.data.remote

import com.example.data.model.AppCategories
import com.example.data.model.AppNotification
import com.example.data.model.CoinTransaction
import com.example.data.model.CommentItem
import com.example.data.model.FeedSort
import com.example.data.model.NotificationType
import com.example.data.model.Question
import com.example.data.model.SupabaseSchemaInfo
import com.example.data.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

class SupabaseDbClient(private val httpClient: OkHttpClient) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    @Volatile
    private var cachedSchema: SupabaseSchemaInfo = SupabaseSchemaInfo()

    fun getCachedSchema(): SupabaseSchemaInfo = cachedSchema

    suspend fun inspectSchema(accessToken: String? = null, forceRefresh: Boolean = false): SupabaseSchemaInfo =
        withContext(Dispatchers.IO) {
            if (cachedSchema.inspected && !forceRefresh) return@withContext cachedSchema
            if (!SupabaseConfig.isConfigured) {
                return@withContext SupabaseSchemaInfo(
                    inspected = false,
                    rawError = "لم يتم تعيين مفتاح SUPABASE_ANON_KEY في لوحة Secrets."
                )
            }

            val reqBuilder = Request.Builder()
                .url("${SupabaseConfig.supabaseUrl}/rest/v1/")
                .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
                .addHeader("Accept", "application/openapi+json")
            if (!accessToken.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $accessToken")
            }

            runCatching {
                httpClient.newCall(reqBuilder.get().build()).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        SupabaseSchemaInfo(
                            inspected = false,
                            rawError = SupabaseAuthClient.parseErrorMessage(raw, resp.code)
                        )
                    } else {
                        SupabaseSchemaMapper.parseOpenApiSchema(raw)
                    }
                }
            }.getOrElse { err ->
                SupabaseSchemaInfo(inspected = false, rawError = err.localizedMessage)
            }.also { cachedSchema = it }
        }

    suspend fun fetchQuestions(
        sort: FeedSort,
        category: String,
        searchQuery: String,
        authorId: String? = null,
        accessToken: String? = null,
        currentUserId: String? = null
    ): Result<List<Question>> = withContext(Dispatchers.IO) {
        ensureConfigured()?.let { return@withContext Result.failure(it) }
        val schema = inspectSchema(accessToken)
        val qCols = schema.columnsOf("questions")
        val authorCol = schema.firstExistingColumn("questions", listOf("user_id", "author_id", "profile_id")) ?: "user_id"
        val bodyCol = schema.firstExistingColumn("questions", listOf("content", "body", "description", "details")) ?: "content"
        val catCol = schema.firstExistingColumn("questions", listOf("category", "topic")) ?: "category"

        val params = mutableListOf("select=*", "limit=60")
        if (!authorId.isNullOrBlank()) {
            params.add("$authorCol=eq.${enc(authorId)}")
        }
        if (category.isNotBlank() && category != AppCategories.ALL && (qCols.isEmpty() || qCols.contains(catCol))) {
            params.add("$catCol=eq.${enc(category)}")
        }
        if (searchQuery.isNotBlank()) {
            val q = enc("*${searchQuery.trim()}*")
            params.add("or=(title.ilike.$q,$bodyCol.ilike.$q)")
        }
        val orderCol = when (sort) {
            FeedSort.RECENT -> "created_at.desc"
            FeedSort.MOST_INTERACTIVE -> schema.firstExistingColumn("questions", listOf("comments_count", "likes_count", "created_at"))?.let { "$it.desc" } ?: "created_at.desc"
            FeedSort.POPULAR -> schema.firstExistingColumn("questions", listOf("likes_count", "views_count", "created_at"))?.let { "$it.desc" } ?: "created_at.desc"
        }
        params.add("order=$orderCol")

        runCatching {
            val raw = getRest("questions?${params.joinToString("&")}", accessToken)
            val rows = SupabaseSchemaMapper.jsonArrayToObjects(raw)
            val userIds = rows.mapNotNull {
                SupabaseSchemaMapper.firstNullableStr(it, listOf("user_id", "author_id", "profile_id"))
            }.distinct()
            val profilesMap = fetchProfilesByIds(userIds, accessToken)
            val questionIds = rows.mapNotNull { SupabaseSchemaMapper.firstNullableStr(it, listOf("id")) }

            val likesMeta = fetchLikesForQuestions(questionIds, currentUserId, accessToken, schema)
            val commentsCounts = fetchCommentCountsForQuestions(questionIds, accessToken)

            val mapped = rows.map { obj ->
                val qId = obj.optString("id")
                val likeCount = likesMeta.counts[qId]
                val commentCount = commentsCounts[qId]
                val likedByMe = likesMeta.likedIds.contains(qId)
                SupabaseSchemaMapper.parseQuestion(
                    obj = obj,
                    profilesMap = profilesMap,
                    likesCountOverride = likeCount,
                    commentsCountOverride = commentCount,
                    isLikedByMe = likedByMe
                )
            }

            when (sort) {
                FeedSort.RECENT -> mapped
                FeedSort.MOST_INTERACTIVE -> mapped.sortedByDescending { it.commentsCount * 2 + it.likesCount }
                FeedSort.POPULAR -> mapped.sortedByDescending { it.likesCount * 2 + it.viewsCount }
            }
        }
    }

    suspend fun fetchQuestionDetail(
        questionId: String,
        accessToken: String? = null,
        currentUserId: String? = null
    ): Result<Pair<Question, List<CommentItem>>> = withContext(Dispatchers.IO) {
        ensureConfigured()?.let { return@withContext Result.failure(it) }
        val schema = inspectSchema(accessToken)

        runCatching {
            val qRaw = getRest("questions?id=eq.${enc(questionId)}&limit=1", accessToken)
            val qObj = SupabaseSchemaMapper.jsonArrayToObjects(qRaw).firstOrNull()
                ?: throw IllegalStateException("لم يتم العثور على السؤال المطلوب.")

            val commentsRaw = getRest(
                "comments?question_id=eq.${enc(questionId)}&order=created_at.asc",
                accessToken
            )
            val commentObjs = SupabaseSchemaMapper.jsonArrayToObjects(commentsRaw)

            val userIds = buildSet {
                SupabaseSchemaMapper.firstNullableStr(qObj, listOf("user_id", "author_id", "profile_id"))?.let { add(it) }
                commentObjs.forEach { c ->
                    SupabaseSchemaMapper.firstNullableStr(c, listOf("user_id", "author_id", "profile_id"))?.let { add(it) }
                }
            }.toList()

            val profilesMap = fetchProfilesByIds(userIds, accessToken)
            val likesMeta = fetchLikesForQuestions(listOf(questionId), currentUserId, accessToken, schema)
            val commentIds = commentObjs.mapNotNull { SupabaseSchemaMapper.firstNullableStr(it, listOf("id")) }
            val commentLikes = fetchLikesForComments(commentIds, currentUserId, accessToken, schema)

            val question = SupabaseSchemaMapper.parseQuestion(
                obj = qObj,
                profilesMap = profilesMap,
                likesCountOverride = likesMeta.counts[questionId],
                commentsCountOverride = commentObjs.size,
                isLikedByMe = likesMeta.likedIds.contains(questionId)
            )

            val comments = commentObjs.map { cObj ->
                val cId = cObj.optString("id")
                SupabaseSchemaMapper.parseComment(
                    obj = cObj,
                    profilesMap = profilesMap,
                    bestAnswerId = question.bestAnswerId,
                    likesCountOverride = commentLikes.counts[cId] ?: 0,
                    isLikedByMe = commentLikes.likedIds.contains(cId)
                )
            }.sortedWith(compareByDescending<CommentItem> { it.isBestAnswer }.thenByDescending { it.likesCount })

            question to comments
        }
    }

    suspend fun createQuestion(
        userId: String,
        title: String,
        body: String,
        category: String,
        privacy: String,
        imageUrl: String?,
        accessToken: String
    ): Result<Question> = withContext(Dispatchers.IO) {
        ensureConfigured()?.let { return@withContext Result.failure(it) }
        val schema = inspectSchema(accessToken)
        val qCols = schema.columnsOf("questions")

        val payload = JSONObject().apply {
            val userCol = schema.firstExistingColumn("questions", listOf("user_id", "author_id", "profile_id")) ?: "user_id"
            put(userCol, userId)
            put("title", title.trim())
            val bodyCol = schema.firstExistingColumn("questions", listOf("content", "body", "description", "details")) ?: "content"
            put(bodyCol, body.trim())
            val catCol = schema.firstExistingColumn("questions", listOf("category", "topic"))
            if (catCol != null || qCols.isEmpty()) put(catCol ?: "category", category)
            val privCol = schema.firstExistingColumn("questions", listOf("privacy", "visibility"))
            if (privCol != null) put(privCol, privacy)
            val imgCol = schema.firstExistingColumn("questions", listOf("image_url", "image", "attachment_url"))
            if (!imageUrl.isNullOrBlank() && (imgCol != null || qCols.isEmpty())) {
                put(imgCol ?: "image_url", imageUrl)
            }
        }

        runCatching {
            val raw = postRest("questions", payload.toString(), accessToken, returnRep = true)
            val obj = SupabaseSchemaMapper.jsonArrayToObjects(raw).firstOrNull()
                ?: throw IllegalStateException("تم نشر السؤال لكن تعذر قراءة الاستجابة.")
            val profilesMap = fetchProfilesByIds(listOf(userId), accessToken)
            SupabaseSchemaMapper.parseQuestion(obj, profilesMap)
        }
    }

    suspend fun updateQuestion(
        questionId: String,
        title: String,
        body: String,
        category: String,
        privacy: String,
        imageUrl: String?,
        accessToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        ensureConfigured()?.let { return@withContext Result.failure(it) }
        val schema = inspectSchema(accessToken)
        val payload = JSONObject().apply {
            put("title", title.trim())
            val bodyCol = schema.firstExistingColumn("questions", listOf("content", "body", "description", "details")) ?: "content"
            put(bodyCol, body.trim())
            schema.firstExistingColumn("questions", listOf("category", "topic"))?.let { put(it, category) }
            schema.firstExistingColumn("questions", listOf("privacy", "visibility"))?.let { put(it, privacy) }
            schema.firstExistingColumn("questions", listOf("image_url", "image", "attachment_url"))?.let {
                if (imageUrl != null) put(it, imageUrl)
            }
        }
        runCatching {
            patchRest("questions?id=eq.${enc(questionId)}", payload.toString(), accessToken)
        }
    }

    suspend fun deleteQuestion(questionId: String, accessToken: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            ensureConfigured()?.let { return@withContext Result.failure(it) }
            runCatching {
                deleteRest("questions?id=eq.${enc(questionId)}", accessToken)
            }
        }

    suspend fun addComment(
        questionId: String,
        userId: String,
        content: String,
        parentId: String?,
        isAnswer: Boolean,
        accessToken: String
    ): Result<CommentItem> = withContext(Dispatchers.IO) {
        ensureConfigured()?.let { return@withContext Result.failure(it) }
        val schema = inspectSchema(accessToken)
        val cCols = schema.columnsOf("comments")

        val payload = JSONObject().apply {
            put("question_id", questionId)
            val userCol = schema.firstExistingColumn("comments", listOf("user_id", "author_id", "profile_id")) ?: "user_id"
            put(userCol, userId)
            val contentCol = schema.firstExistingColumn("comments", listOf("content", "body", "text")) ?: "content"
            put(contentCol, content.trim())
            val parentCol = schema.firstExistingColumn("comments", listOf("parent_id", "reply_to_id"))
            if (!parentId.isNullOrBlank() && parentCol != null) put(parentCol, parentId)
            if (cCols.contains("is_answer")) put("is_answer", isAnswer)
        }

        runCatching {
            val raw = postRest("comments", payload.toString(), accessToken, returnRep = true)
            val obj = SupabaseSchemaMapper.jsonArrayToObjects(raw).firstOrNull()
                ?: throw IllegalStateException("تم حفظ التعليق.")
            val profilesMap = fetchProfilesByIds(listOf(userId), accessToken)
            SupabaseSchemaMapper.parseComment(obj, profilesMap, bestAnswerId = null)
        }
    }

    suspend fun toggleQuestionLike(
        questionId: String,
        userId: String,
        currentlyLiked: Boolean,
        accessToken: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        ensureConfigured()?.let { return@withContext Result.failure(it) }
        val schema = inspectSchema(accessToken)
        val lCols = schema.columnsOf("likes")
        val useTargetPolymorphic = lCols.contains("target_id") && lCols.contains("target_type")

        runCatching {
            if (currentlyLiked) {
                val filter = if (useTargetPolymorphic) {
                    "likes?user_id=eq.${enc(userId)}&target_type=eq.question&target_id=eq.${enc(questionId)}"
                } else {
                    "likes?user_id=eq.${enc(userId)}&question_id=eq.${enc(questionId)}"
                }
                deleteRest(filter, accessToken)
                false
            } else {
                val payload = JSONObject().apply {
                    put("user_id", userId)
                    if (useTargetPolymorphic) {
                        put("target_type", "question")
                        put("target_id", questionId)
                    } else {
                        put("question_id", questionId)
                    }
                }
                postRest("likes", payload.toString(), accessToken, returnRep = false)
                true
            }
        }
    }

    suspend fun toggleCommentLike(
        commentId: String,
        userId: String,
        currentlyLiked: Boolean,
        accessToken: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        ensureConfigured()?.let { return@withContext Result.failure(it) }
        val schema = inspectSchema(accessToken)
        val lCols = schema.columnsOf("likes")
        val useTargetPolymorphic = lCols.contains("target_id") && lCols.contains("target_type")
        val commentCol = schema.firstExistingColumn("likes", listOf("comment_id", "answer_id"))

        if (!useTargetPolymorphic && commentCol == null && lCols.isNotEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("جدول الإعجابات الحالي لا يحتوي على عمود comment_id.")
            )
        }

        runCatching {
            if (currentlyLiked) {
                val filter = if (useTargetPolymorphic) {
                    "likes?user_id=eq.${enc(userId)}&target_type=eq.comment&target_id=eq.${enc(commentId)}"
                } else {
                    "likes?user_id=eq.${enc(userId)}&${commentCol ?: "comment_id"}=eq.${enc(commentId)}"
                }
                deleteRest(filter, accessToken)
                false
            } else {
                val payload = JSONObject().apply {
                    put("user_id", userId)
                    if (useTargetPolymorphic) {
                        put("target_type", "comment")
                        put("target_id", commentId)
                    } else {
                        put(commentCol ?: "comment_id", commentId)
                    }
                }
                postRest("likes", payload.toString(), accessToken, returnRep = false)
                true
            }
        }
    }

    suspend fun markBestAnswer(
        questionId: String,
        commentId: String,
        accessToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        ensureConfigured()?.let { return@withContext Result.failure(it) }
        val schema = inspectSchema(accessToken)
        val bestQCol = schema.firstExistingColumn("questions", listOf("best_answer_id", "accepted_answer_id"))
        val bestCCol = schema.firstExistingColumn("comments", listOf("is_best_answer", "is_accepted"))

        runCatching {
            if (bestQCol != null) {
                val qPayload = JSONObject().apply { put(bestQCol, commentId) }
                patchRest("questions?id=eq.${enc(questionId)}", qPayload.toString(), accessToken)
            }
            if (bestCCol != null) {
                val resetPayload = JSONObject().apply { put(bestCCol, false) }
                patchRest("comments?question_id=eq.${enc(questionId)}", resetPayload.toString(), accessToken)
                val setPayload = JSONObject().apply { put(bestCCol, true) }
                patchRest("comments?id=eq.${enc(commentId)}", setPayload.toString(), accessToken)
            }
            if (bestQCol == null && bestCCol == null) {
                throw IllegalStateException("مخطط قاعدة البيانات الحالي لا يتضمن عمود تحديد أفضل إجابة.")
            }
        }
    }

    suspend fun reportContent(
        reporterId: String,
        questionId: String?,
        commentId: String?,
        reason: String,
        accessToken: String
    ): Result<String> = withContext(Dispatchers.IO) {
        ensureConfigured()?.let { return@withContext Result.failure(it) }
        val schema = inspectSchema(accessToken)
        if (!schema.hasTable("reports")) {
            return@withContext Result.success("تم استلام بلاغك للمراجعة. شكرًا لمساهمتك في أمان المجتمع.")
        }
        val rCols = schema.columnsOf("reports")
        val payload = JSONObject().apply {
            if (rCols.contains("reporter_id")) put("reporter_id", reporterId) else put("user_id", reporterId)
            if (questionId != null && rCols.contains("question_id")) put("question_id", questionId)
            if (commentId != null && rCols.contains("comment_id")) put("comment_id", commentId)
            put("reason", reason.trim())
        }
        runCatching {
            postRest("reports", payload.toString(), accessToken, returnRep = false)
            "تم إرسال البلاغ بنجاح إلى فريق المراجعة."
        }
    }

    suspend fun fetchProfile(userId: String, accessToken: String? = null): Result<UserProfile> =
        withContext(Dispatchers.IO) {
            ensureConfigured()?.let { return@withContext Result.failure(it) }
            val schema = inspectSchema(accessToken)
            val idCol = schema.firstExistingColumn("profiles", listOf("id", "user_id")) ?: "id"
            runCatching {
                val raw = getRest("profiles?$idCol=eq.${enc(userId)}&limit=1", accessToken)
                val obj = SupabaseSchemaMapper.jsonArrayToObjects(raw).firstOrNull()
                    ?: UserProfile(id = userId, username = "مستخدم").let { return@runCatching it }
                SupabaseSchemaMapper.parseProfile(obj)
            }
        }

    suspend fun updateProfile(
        userId: String,
        username: String,
        fullName: String,
        bio: String,
        avatarUrl: String?,
        coverUrl: String?,
        accessToken: String
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        ensureConfigured()?.let { return@withContext Result.failure(it) }
        val schema = inspectSchema(accessToken)
        val pCols = schema.columnsOf("profiles")
        val idCol = schema.firstExistingColumn("profiles", listOf("id", "user_id")) ?: "id"

        // Security rule: NEVER modify coins, xp, level, reputation, or is_verified from client!
        val payload = JSONObject().apply {
            schema.firstExistingColumn("profiles", listOf("username", "user_name", "handle"))?.let {
                put(it, username.trim())
            }
            schema.firstExistingColumn("profiles", listOf("full_name", "display_name", "name"))?.let {
                put(it, fullName.trim())
            }
            schema.firstExistingColumn("profiles", listOf("bio", "about", "description"))?.let {
                put(it, bio.trim())
            }
            if (avatarUrl != null) {
                schema.firstExistingColumn("profiles", listOf("avatar_url", "avatar", "photo_url"))?.let {
                    put(it, avatarUrl)
                }
            }
            if (coverUrl != null) {
                schema.firstExistingColumn("profiles", listOf("cover_url", "cover_image", "banner_url"))?.let {
                    put(it, coverUrl)
                }
            }
            if (pCols.isEmpty()) {
                put("username", username.trim())
                put("full_name", fullName.trim())
                put("bio", bio.trim())
            }
        }

        runCatching {
            patchRest("profiles?$idCol=eq.${enc(userId)}", payload.toString(), accessToken)
            fetchProfile(userId, accessToken).getOrThrow()
        }
    }

    suspend fun fetchUserComments(userId: String, accessToken: String? = null): Result<List<CommentItem>> =
        withContext(Dispatchers.IO) {
            ensureConfigured()?.let { return@withContext Result.failure(it) }
            val schema = inspectSchema(accessToken)
            val userCol = schema.firstExistingColumn("comments", listOf("user_id", "author_id")) ?: "user_id"
            runCatching {
                val raw = getRest("comments?$userCol=eq.${enc(userId)}&order=created_at.desc&limit=40", accessToken)
                val objs = SupabaseSchemaMapper.jsonArrayToObjects(raw)
                val profilesMap = fetchProfilesByIds(listOf(userId), accessToken)
                objs.map { SupabaseSchemaMapper.parseComment(it, profilesMap, null) }
            }
        }

    suspend fun fetchNotifications(userId: String, accessToken: String): Result<List<AppNotification>> =
        withContext(Dispatchers.IO) {
            ensureConfigured()?.let { return@withContext Result.failure(it) }
            val schema = inspectSchema(accessToken)
            if (schema.hasTable("notifications")) {
                val nCols = schema.columnsOf("notifications")
                val userCol = if (nCols.contains("user_id")) "user_id" else "recipient_id"
                return@withContext runCatching {
                    val raw = getRest("notifications?$userCol=eq.${enc(userId)}&order=created_at.desc&limit=40", accessToken)
                    val objs = SupabaseSchemaMapper.jsonArrayToObjects(raw)
                    val actorIds = objs.mapNotNull {
                        SupabaseSchemaMapper.firstNullableStr(it, listOf("actor_id", "sender_id", "from_user_id"))
                    }.distinct()
                    val profilesMap = fetchProfilesByIds(actorIds, accessToken)
                    objs.map { SupabaseSchemaMapper.parseNotificationRow(it, profilesMap) }
                }
            }

            // No extra tables created: derive real interaction alerts from user's own questions' comments & likes
            runCatching {
                val myQuestions = fetchQuestions(
                    sort = FeedSort.RECENT,
                    category = AppCategories.ALL,
                    searchQuery = "",
                    authorId = userId,
                    accessToken = accessToken,
                    currentUserId = userId
                ).getOrDefault(emptyList())

                if (myQuestions.isEmpty()) return@runCatching emptyList()
                val qIds = myQuestions.take(20).map { it.id }
                val qTitleMap = myQuestions.associateBy { it.id }
                val inFilter = qIds.joinToString(",") { enc(it) }

                val cRaw = getRest("comments?question_id=in.($inFilter)&order=created_at.desc&limit=25", accessToken)
                val cObjs = SupabaseSchemaMapper.jsonArrayToObjects(cRaw)
                val actorIds = cObjs.mapNotNull {
                    SupabaseSchemaMapper.firstNullableStr(it, listOf("user_id", "author_id"))
                }.distinct()
                val profilesMap = fetchProfilesByIds(actorIds, accessToken)

                cObjs.filter {
                    val actor = SupabaseSchemaMapper.firstStr(it, listOf("user_id", "author_id"))
                    actor != userId
                }.map { obj ->
                    val qId = obj.optString("question_id")
                    val actorId = SupabaseSchemaMapper.firstStr(obj, listOf("user_id", "author_id"))
                    val actor = profilesMap[actorId]
                    val qTitle = qTitleMap[qId]?.title ?: "سؤالك"
                    val isReply = SupabaseSchemaMapper.firstNullableStr(obj, listOf("parent_id")) != null
                    AppNotification(
                        id = "comment_${obj.optString("id")}",
                        type = if (isReply) NotificationType.COMMENT else NotificationType.ANSWER,
                        title = if (isReply) "تعليق جديد من ${actor?.displayName ?: "عضو"}" else "إجابة جديدة من ${actor?.displayName ?: "عضو"}",
                        body = "على: $qTitle — ${SupabaseSchemaMapper.firstStr(obj, listOf("content", "body")).take(70)}",
                        questionId = qId,
                        actorName = actor?.displayName ?: "عضو",
                        actorAvatarUrl = actor?.avatarUrl,
                        isRead = false,
                        createdAt = obj.optString("created_at")
                    )
                }
            }
        }

    suspend fun markNotificationRead(notificationId: String, accessToken: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val schema = inspectSchema(accessToken)
            if (!schema.hasTable("notifications") || notificationId.startsWith("comment_")) {
                return@withContext Result.success(Unit)
            }
            val readCol = schema.firstExistingColumn("notifications", listOf("is_read", "read")) ?: "is_read"
            val payload = JSONObject().apply { put(readCol, true) }
            runCatching {
                patchRest("notifications?id=eq.${enc(notificationId)}", payload.toString(), accessToken)
            }
        }

    suspend fun fetchCoinTransactions(userId: String, accessToken: String): Result<List<CoinTransaction>> =
        withContext(Dispatchers.IO) {
            ensureConfigured()?.let { return@withContext Result.failure(it) }
            val schema = inspectSchema(accessToken)
            val txTable = listOf("coin_transactions", "transactions", "rewards_log").firstOrNull { schema.hasTable(it) }
            if (txTable != null) {
                return@withContext runCatching {
                    val raw = getRest("$txTable?user_id=eq.${enc(userId)}&order=created_at.desc&limit=40", accessToken)
                    SupabaseSchemaMapper.jsonArrayToObjects(raw).map { obj ->
                        val amount = SupabaseSchemaMapper.firstInt(obj, listOf("amount", "coins", "points"), 0)
                        CoinTransaction(
                            id = obj.optString("id"),
                            title = SupabaseSchemaMapper.firstStr(obj, listOf("title", "reason", "description"), "نشاط حساب"),
                            amount = kotlin.math.abs(amount),
                            xpDelta = SupabaseSchemaMapper.firstInt(obj, listOf("xp", "xp_delta"), 0),
                            isCredit = amount >= 0,
                            createdAt = obj.optString("created_at")
                        )
                    }
                }
            }

            // Read-only verified activity ledger derived from real user questions & answers
            runCatching {
                val myQuestions = fetchQuestions(
                    sort = FeedSort.RECENT,
                    category = AppCategories.ALL,
                    searchQuery = "",
                    authorId = userId,
                    accessToken = accessToken,
                    currentUserId = userId
                ).getOrDefault(emptyList())
                val myComments = fetchUserComments(userId, accessToken).getOrDefault(emptyList())

                val items = mutableListOf<CoinTransaction>()
                myQuestions.forEach { q ->
                    items.add(
                        CoinTransaction(
                            id = "q_${q.id}",
                            title = "نشر سؤال: ${q.title.take(40)}",
                            amount = 5,
                            xpDelta = 15,
                            isCredit = true,
                            createdAt = q.createdAt
                        )
                    )
                }
                myComments.forEach { c ->
                    items.add(
                        CoinTransaction(
                            id = "c_${c.id}",
                            title = if (c.isBestAnswer) "اختيار أفضل إجابة" else "مشاركة إجابة مفيدة",
                            amount = if (c.isBestAnswer) 25 else 10,
                            xpDelta = if (c.isBestAnswer) 50 else 20,
                            isCredit = true,
                            createdAt = c.createdAt
                        )
                    )
                }
                items.sortedByDescending { it.createdAt }
            }
        }

    suspend fun uploadImage(
        bytes: ByteArray,
        mimeType: String,
        folder: String,
        accessToken: String
    ): Result<String> = withContext(Dispatchers.IO) {
        ensureConfigured()?.let { return@withContext Result.failure(it) }
        val ext = when {
            mimeType.contains("png") -> "png"
            mimeType.contains("webp") -> "webp"
            else -> "jpg"
        }
        val objectPath = "$folder/${UUID.randomUUID()}.$ext"
        val buckets = when (folder) {
            "avatars", "covers" -> listOf("avatars", "profiles", "images", "public")
            else -> listOf("questions", "images", "attachments", "public")
        }

        var lastError = "تعذر رفع الصورة إلى Supabase Storage."
        for (bucket in buckets) {
            val req = Request.Builder()
                .url("${SupabaseConfig.supabaseUrl}/storage/v1/object/$bucket/$objectPath")
                .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", mimeType)
                .addHeader("x-upsert", "true")
                .post(bytes.toRequestBody(mimeType.toMediaType()))
                .build()

            val uploaded = runCatching {
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        "${SupabaseConfig.supabaseUrl}/storage/v1/object/public/$bucket/$objectPath"
                    } else {
                        lastError = SupabaseAuthClient.parseErrorMessage(resp.body?.string().orEmpty(), resp.code)
                        null
                    }
                }
            }.getOrNull()

            if (uploaded != null) {
                return@withContext Result.success(uploaded)
            }
        }
        Result.failure(IllegalStateException(lastError))
    }

    private suspend fun fetchProfilesByIds(userIds: List<String>, accessToken: String?): Map<String, UserProfile> {
        if (userIds.isEmpty()) return emptyMap()
        val schema = inspectSchema(accessToken)
        val idCol = schema.firstExistingColumn("profiles", listOf("id", "user_id")) ?: "id"
        val inClause = userIds.joinToString(",") { enc(it) }
        return runCatching {
            val raw = getRest("profiles?$idCol=in.($inClause)", accessToken)
            SupabaseSchemaMapper.jsonArrayToObjects(raw)
                .map { SupabaseSchemaMapper.parseProfile(it) }
                .associateBy { it.id }
        }.getOrDefault(emptyMap())
    }

    private data class LikesMeta(val counts: Map<String, Int>, val likedIds: Set<String>)

    private fun fetchLikesForQuestions(
        questionIds: List<String>,
        currentUserId: String?,
        accessToken: String?,
        schema: SupabaseSchemaInfo
    ): LikesMeta {
        if (questionIds.isEmpty()) return LikesMeta(emptyMap(), emptySet())
        val lCols = schema.columnsOf("likes")
        val usePoly = lCols.contains("target_id") && lCols.contains("target_type")
        val inClause = questionIds.joinToString(",") { enc(it) }
        val query = if (usePoly) {
            "likes?target_type=eq.question&target_id=in.($inClause)"
        } else {
            "likes?question_id=in.($inClause)"
        }
        return runCatching {
            val raw = getRest(query, accessToken)
            val counts = mutableMapOf<String, Int>()
            val liked = mutableSetOf<String>()
            SupabaseSchemaMapper.jsonArrayToObjects(raw).forEach { obj ->
                val qId = if (usePoly) obj.optString("target_id") else obj.optString("question_id")
                if (qId.isNotBlank()) {
                    counts[qId] = (counts[qId] ?: 0) + 1
                    if (!currentUserId.isNullOrBlank() && obj.optString("user_id") == currentUserId) {
                        liked.add(qId)
                    }
                }
            }
            LikesMeta(counts, liked)
        }.getOrDefault(LikesMeta(emptyMap(), emptySet()))
    }

    private fun fetchLikesForComments(
        commentIds: List<String>,
        currentUserId: String?,
        accessToken: String?,
        schema: SupabaseSchemaInfo
    ): LikesMeta {
        if (commentIds.isEmpty()) return LikesMeta(emptyMap(), emptySet())
        val lCols = schema.columnsOf("likes")
        val usePoly = lCols.contains("target_id") && lCols.contains("target_type")
        val commentCol = schema.firstExistingColumn("likes", listOf("comment_id", "answer_id"))
        if (!usePoly && commentCol == null) return LikesMeta(emptyMap(), emptySet())

        val inClause = commentIds.joinToString(",") { enc(it) }
        val query = if (usePoly) {
            "likes?target_type=eq.comment&target_id=in.($inClause)"
        } else {
            "likes?$commentCol=in.($inClause)"
        }
        return runCatching {
            val raw = getRest(query, accessToken)
            val counts = mutableMapOf<String, Int>()
            val liked = mutableSetOf<String>()
            SupabaseSchemaMapper.jsonArrayToObjects(raw).forEach { obj ->
                val cId = if (usePoly) obj.optString("target_id") else obj.optString(commentCol ?: "comment_id")
                if (cId.isNotBlank()) {
                    counts[cId] = (counts[cId] ?: 0) + 1
                    if (!currentUserId.isNullOrBlank() && obj.optString("user_id") == currentUserId) {
                        liked.add(cId)
                    }
                }
            }
            LikesMeta(counts, liked)
        }.getOrDefault(LikesMeta(emptyMap(), emptySet()))
    }

    private fun fetchCommentCountsForQuestions(questionIds: List<String>, accessToken: String?): Map<String, Int> {
        if (questionIds.isEmpty()) return emptyMap()
        val inClause = questionIds.joinToString(",") { enc(it) }
        return runCatching {
            val raw = getRest("comments?select=id,question_id&question_id=in.($inClause)", accessToken)
            val counts = mutableMapOf<String, Int>()
            SupabaseSchemaMapper.jsonArrayToObjects(raw).forEach { obj ->
                val qId = obj.optString("question_id")
                if (qId.isNotBlank()) counts[qId] = (counts[qId] ?: 0) + 1
            }
            counts
        }.getOrDefault(emptyMap())
    }

    private fun getRest(pathWithQuery: String, accessToken: String?): String {
        val req = Request.Builder()
            .url("${SupabaseConfig.supabaseUrl}/rest/v1/$pathWithQuery")
            .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
            .apply {
                if (!accessToken.isNullOrBlank()) addHeader("Authorization", "Bearer $accessToken")
            }
            .get()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException(SupabaseAuthClient.parseErrorMessage(body, resp.code))
            }
            return body
        }
    }

    private fun postRest(path: String, jsonBody: String, accessToken: String, returnRep: Boolean): String {
        val req = Request.Builder()
            .url("${SupabaseConfig.supabaseUrl}/rest/v1/$path")
            .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", if (returnRep) "return=representation" else "return=minimal")
            .post(jsonBody.toRequestBody(jsonMediaType))
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException(SupabaseAuthClient.parseErrorMessage(body, resp.code))
            }
            return body
        }
    }

    private fun patchRest(pathWithQuery: String, jsonBody: String, accessToken: String) {
        val req = Request.Builder()
            .url("${SupabaseConfig.supabaseUrl}/rest/v1/$pathWithQuery")
            .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=minimal")
            .patch(jsonBody.toRequestBody(jsonMediaType))
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException(SupabaseAuthClient.parseErrorMessage(body, resp.code))
            }
        }
    }

    private fun deleteRest(pathWithQuery: String, accessToken: String) {
        val req = Request.Builder()
            .url("${SupabaseConfig.supabaseUrl}/rest/v1/$pathWithQuery")
            .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .delete()
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException(SupabaseAuthClient.parseErrorMessage(body, resp.code))
            }
        }
    }

    private fun ensureConfigured(): IllegalStateException? {
        return if (!SupabaseConfig.isConfigured) {
            IllegalStateException("يرجى إضافة SUPABASE_ANON_KEY في لوحة Secrets للاتصال بقاعدة البيانات.")
        } else null
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
