package com.example.data.remote

import com.example.data.model.AppNotification
import com.example.data.model.CommentItem
import com.example.data.model.NotificationType
import com.example.data.model.Question
import com.example.data.model.SupabaseSchemaInfo
import com.example.data.model.UserProfile
import org.json.JSONArray
import org.json.JSONObject

object SupabaseSchemaMapper {

    fun parseOpenApiSchema(rawJson: String): SupabaseSchemaInfo {
        val tables = mutableMapOf<String, Set<String>>()
        val root = JSONObject(rawJson)

        val definitions = root.optJSONObject("definitions")
            ?: root.optJSONObject("components")?.optJSONObject("schemas")

        if (definitions != null) {
            val keys = definitions.keys()
            while (keys.hasNext()) {
                val tableName = keys.next()
                val tableDef = definitions.optJSONObject(tableName)
                val props = tableDef?.optJSONObject("properties")
                if (props != null) {
                    val cols = mutableSetOf<String>()
                    val propKeys = props.keys()
                    while (propKeys.hasNext()) {
                        cols.add(propKeys.next())
                    }
                    tables[tableName] = cols
                }
            }
        }
        return SupabaseSchemaInfo(inspected = true, tables = tables)
    }

    fun parseProfile(obj: JSONObject): UserProfile {
        val badgesList = mutableListOf<String>()
        val badgesArr = obj.optJSONArray("badges")
        if (badgesArr != null) {
            for (i in 0 until badgesArr.length()) {
                val item = badgesArr.optString(i)
                if (item.isNotBlank()) badgesList.add(item)
            }
        } else {
            val badgesStr = obj.optString("badges")
            if (badgesStr.isNotBlank() && badgesStr != "null") {
                badgesStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    .forEach { badgesList.add(it) }
            }
        }

        val xpVal = firstInt(obj, listOf("xp", "experience", "experience_points"), 0)
        val levelVal = firstInt(obj, listOf("level"), (xpVal / 250) + 1).coerceAtLeast(1)

        return UserProfile(
            id = firstStr(obj, listOf("id", "user_id")),
            username = firstStr(obj, listOf("username", "user_name", "handle")),
            fullName = firstStr(obj, listOf("full_name", "display_name", "name")),
            bio = firstStr(obj, listOf("bio", "about", "description")),
            avatarUrl = firstNullableStr(obj, listOf("avatar_url", "avatar", "photo_url")),
            coverUrl = firstNullableStr(obj, listOf("cover_url", "cover_image", "banner_url")),
            level = levelVal,
            xp = xpVal,
            reputation = firstInt(obj, listOf("reputation", "reputation_score"), 0),
            coins = firstInt(obj, listOf("coins", "points", "balance"), 0),
            isVerified = firstBool(obj, listOf("is_verified", "verified")),
            badges = badgesList,
            createdAt = obj.optString("created_at")
        )
    }

    fun parseQuestion(
        obj: JSONObject,
        profilesMap: Map<String, UserProfile>,
        likesCountOverride: Int? = null,
        commentsCountOverride: Int? = null,
        isLikedByMe: Boolean = false
    ): Question {
        val userId = firstStr(obj, listOf("user_id", "author_id", "profile_id"))
        val profile = profilesMap[userId]
        val privacy = firstStr(obj, listOf("privacy", "visibility"), "public")
        val isAnon = privacy.equals("anonymous", ignoreCase = true)

        val tagsList = mutableListOf<String>()
        val tagsArr = obj.optJSONArray("tags")
        if (tagsArr != null) {
            for (i in 0 until tagsArr.length()) {
                val t = tagsArr.optString(i)
                if (t.isNotBlank()) tagsList.add(t)
            }
        }

        return Question(
            id = firstStr(obj, listOf("id")),
            userId = userId,
            authorName = if (isAnon) "عضو مجهول" else profile?.displayName ?: "مستخدم",
            authorUsername = if (isAnon) "" else profile?.username.orEmpty(),
            authorAvatarUrl = if (isAnon) null else profile?.avatarUrl,
            authorLevel = profile?.level ?: 1,
            authorVerified = if (isAnon) false else (profile?.isVerified ?: false),
            title = firstStr(obj, listOf("title", "question_title")),
            body = firstStr(obj, listOf("content", "body", "description", "details")),
            category = firstStr(obj, listOf("category", "topic", "tag"), "عام"),
            imageUrl = firstNullableStr(obj, listOf("image_url", "image", "attachment_url")),
            privacy = privacy,
            viewsCount = firstInt(obj, listOf("views_count", "views"), 0),
            likesCount = likesCountOverride ?: firstInt(obj, listOf("likes_count", "likes"), 0),
            commentsCount = commentsCountOverride ?: firstInt(obj, listOf("comments_count", "answers_count"), 0),
            isLikedByMe = isLikedByMe,
            bestAnswerId = firstNullableStr(obj, listOf("best_answer_id", "accepted_answer_id")),
            tags = tagsList,
            createdAt = obj.optString("created_at")
        )
    }

    fun parseComment(
        obj: JSONObject,
        profilesMap: Map<String, UserProfile>,
        bestAnswerId: String?,
        likesCountOverride: Int = 0,
        isLikedByMe: Boolean = false
    ): CommentItem {
        val id = firstStr(obj, listOf("id"))
        val userId = firstStr(obj, listOf("user_id", "author_id", "profile_id"))
        val profile = profilesMap[userId]
        val parentId = firstNullableStr(obj, listOf("parent_id", "reply_to_id"))
        val flagBest = firstBool(obj, listOf("is_best_answer", "is_accepted"))
        val isBest = flagBest || (bestAnswerId != null && bestAnswerId == id)
        val isAns = if (obj.has("is_answer")) obj.optBoolean("is_answer", true) else parentId == null

        return CommentItem(
            id = id,
            questionId = firstStr(obj, listOf("question_id")),
            userId = userId,
            parentId = parentId,
            authorName = profile?.displayName ?: "مستخدم",
            authorAvatarUrl = profile?.avatarUrl,
            authorLevel = profile?.level ?: 1,
            authorVerified = profile?.isVerified ?: false,
            content = firstStr(obj, listOf("content", "body", "text")),
            isAnswer = isAns,
            isBestAnswer = isBest,
            likesCount = maxOf(likesCountOverride, firstInt(obj, listOf("likes_count"), 0)),
            isLikedByMe = isLikedByMe,
            createdAt = obj.optString("created_at")
        )
    }

    fun parseNotificationRow(obj: JSONObject, profilesMap: Map<String, UserProfile>): AppNotification {
        val rawType = firstStr(obj, listOf("type", "notification_type"), "system").lowercase()
        val type = when {
            rawType.contains("answer") -> NotificationType.ANSWER
            rawType.contains("comment") || rawType.contains("reply") -> NotificationType.COMMENT
            rawType.contains("like") -> NotificationType.LIKE
            else -> NotificationType.SYSTEM
        }
        val actorId = firstStr(obj, listOf("actor_id", "sender_id", "from_user_id"))
        val actor = profilesMap[actorId]
        return AppNotification(
            id = firstStr(obj, listOf("id")),
            type = type,
            title = firstStr(obj, listOf("title"), type.labelAr),
            body = firstStr(obj, listOf("body", "message", "content")),
            questionId = firstNullableStr(obj, listOf("question_id")),
            actorName = actor?.displayName ?: firstStr(obj, listOf("actor_name")),
            actorAvatarUrl = actor?.avatarUrl,
            isRead = firstBool(obj, listOf("is_read", "read")),
            createdAt = obj.optString("created_at")
        )
    }

    fun firstStr(obj: JSONObject, keys: List<String>, default: String = ""): String {
        for (k in keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                val v = obj.optString(k)
                if (v.isNotBlank() && v != "null") return v
            }
        }
        return default
    }

    fun firstNullableStr(obj: JSONObject, keys: List<String>): String? {
        val s = firstStr(obj, keys, "")
        return s.ifBlank { null }
    }

    fun firstInt(obj: JSONObject, keys: List<String>, default: Int): Int {
        for (k in keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                return obj.optInt(k, default)
            }
        }
        return default
    }

    fun firstBool(obj: JSONObject, keys: List<String>): Boolean {
        for (k in keys) {
            if (obj.has(k) && !obj.isNull(k)) {
                return obj.optBoolean(k, false)
            }
        }
        return false
    }

    fun jsonArrayToObjects(raw: String): List<JSONObject> {
        val arr = JSONArray(raw)
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { add(it) }
            }
        }
    }
}
