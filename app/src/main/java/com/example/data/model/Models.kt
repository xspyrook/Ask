package com.example.data.model

enum class ThemeMode(val labelAr: String) {
    SYSTEM("تلقائي (حسب النظام)"),
    LIGHT("الوضع الفاتح"),
    DARK("الوضع الداكن")
}

enum class FeedSort(val labelAr: String) {
    RECENT("الأحدث"),
    MOST_INTERACTIVE("الأكثر تفاعلاً"),
    POPULAR("الشائعة")
}

enum class QuestionPrivacy(val value: String, val labelAr: String) {
    PUBLIC("public", "عام للجميع"),
    FOLLOWERS("followers", "للمتابعين فقط"),
    ANONYMOUS("anonymous", "طرح بهوية مخفية")
}

object AppCategories {
    const val ALL = "الكل"
    val items = listOf(
        ALL,
        "التقنية والبرمجة",
        "التعليم والدراسة",
        "الصحة والطب",
        "الأعمال والمال",
        "الثقافة والأدب",
        "الحياة اليومية",
        "الدين والمجتمع",
        "التصميم والإبداع"
    )
}

data class UserProfile(
    val id: String,
    val username: String,
    val fullName: String = "",
    val bio: String = "",
    val avatarUrl: String? = null,
    val coverUrl: String? = null,
    val level: Int = 1,
    val xp: Int = 0,
    val reputation: Int = 0,
    val coins: Int = 0,
    val isVerified: Boolean = false,
    val badges: List<String> = emptyList(),
    val createdAt: String = ""
) {
    val displayName: String
        get() = fullName.ifBlank { username.ifBlank { "مستخدم هلا سؤال" } }

    val nextLevelXp: Int
        get() = level * 250

    val levelProgress: Float
        get() {
            val prevThreshold = ((level - 1).coerceAtLeast(0)) * 250
            val span = (nextLevelXp - prevThreshold).coerceAtLeast(1)
            return ((xp - prevThreshold).toFloat() / span.toFloat()).coerceIn(0f, 1f)
        }
}

data class Question(
    val id: String,
    val userId: String,
    val authorName: String,
    val authorUsername: String = "",
    val authorAvatarUrl: String? = null,
    val authorLevel: Int = 1,
    val authorVerified: Boolean = false,
    val title: String,
    val body: String,
    val category: String = "عام",
    val imageUrl: String? = null,
    val privacy: String = "public",
    val viewsCount: Int = 0,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val isLikedByMe: Boolean = false,
    val bestAnswerId: String? = null,
    val tags: List<String> = emptyList(),
    val createdAt: String = ""
)

data class CommentItem(
    val id: String,
    val questionId: String,
    val userId: String,
    val parentId: String? = null,
    val authorName: String,
    val authorAvatarUrl: String? = null,
    val authorLevel: Int = 1,
    val authorVerified: Boolean = false,
    val content: String,
    val isAnswer: Boolean = true,
    val isBestAnswer: Boolean = false,
    val likesCount: Int = 0,
    val isLikedByMe: Boolean = false,
    val createdAt: String = ""
)

enum class NotificationType(val labelAr: String) {
    ANSWER("إجابة جديدة"),
    COMMENT("تعليق جديد"),
    LIKE("إعجاب"),
    SYSTEM("تفاعل")
}

data class AppNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val questionId: String? = null,
    val actorName: String = "",
    val actorAvatarUrl: String? = null,
    val isRead: Boolean = false,
    val createdAt: String = ""
)

data class CoinTransaction(
    val id: String,
    val title: String,
    val amount: Int,
    val xpDelta: Int = 0,
    val isCredit: Boolean = true,
    val createdAt: String = ""
)

data class AchievementBadge(
    val id: String,
    val title: String,
    val description: String,
    val unlocked: Boolean,
    val requirementLabel: String
)

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String
)

data class SupabaseSchemaInfo(
    val inspected: Boolean = false,
    val tables: Map<String, Set<String>> = emptyMap(),
    val rawError: String? = null
) {
    fun hasTable(name: String): Boolean = tables.containsKey(name)
    fun columnsOf(table: String): Set<String> = tables[table].orEmpty()
    fun firstExistingColumn(table: String, candidates: List<String>): String? {
        val cols = columnsOf(table)
        return candidates.firstOrNull { cols.contains(it) }
    }
}

sealed interface ResourceState<out T> {
    data object Idle : ResourceState<Nothing>
    data object Loading : ResourceState<Nothing>
    data class Success<T>(val data: T) : ResourceState<T>
    data class Error(val message: String) : ResourceState<Nothing>
}
