package com.example.data.ai

data class AiAssistantResult(
    val content: String,
    val suggestions: List<String> = emptyList(),
    val isPlaceholder: Boolean = true,
    val noticeText: String = FIREBASE_REQUIRED_NOTICE
) {
    companion object {
        const val FIREBASE_REQUIRED_NOTICE =
            "ملاحظة: ميزات الذكاء الاصطناعي عبر Gemini تتطلب تفعيل Firebase AI Logic في المشروع. الاستجابة المعروضة حالياً هي مثال توضيحي فقط."
    }
}

interface AiRepository {
    val isLiveGeminiEnabled: Boolean
    suspend fun suggestQuestionRefinement(title: String, details: String): AiAssistantResult
    suspend fun improveAnswer(questionTitle: String, draftAnswer: String): AiAssistantResult
    suspend fun summarizeDiscussion(questionTitle: String, answers: List<String>): AiAssistantResult
    suspend fun suggestSimilarQuestions(query: String, existingTitles: List<String>): AiAssistantResult
    suspend fun assistSearch(query: String): AiAssistantResult
}

/**
 * Placeholder implementation used when Firebase setup is declined.
 * Never embeds a client-side Gemini API key or calls a direct Gemini REST endpoint.
 */
class PlaceholderAiRepository : AiRepository {
    override val isLiveGeminiEnabled: Boolean = false

    override suspend fun suggestQuestionRefinement(title: String, details: String): AiAssistantResult {
        val cleanTitle = title.trim().ifBlank { "سؤال جديد" }
        val cleanDetails = details.trim()
        val refinedTitle = if (cleanTitle.endsWith("؟")) cleanTitle else "$cleanTitle؟"
        val bodySuggestion = buildString {
            append("[استجابة تجريبية - يتطلب تفعيل Firebase AI Logic]\n")
            append("عنوان مقترح أكثر وضوحاً:\n«$refinedTitle»\n\n")
            append("نصائح لصياغة أفضل:\n")
            append("• حدد السياق أو الهدف الأساسي من سؤالك بوضوح.\n")
            if (cleanDetails.length < 30) {
                append("• أضف مثالاً عملياً أو الخطوات التي جرّبتها حتى الآن.\n")
            }
            append("• اختر التصنيف الدقيق لتصل إجابات من المختصين بسرعة.")
        }
        return AiAssistantResult(
            content = bodySuggestion,
            suggestions = listOf(
                refinedTitle,
                "كيف يمكنني حل مشكلة: ${cleanTitle.removeSuffix("؟")} بأفضل طريقة؟"
            )
        )
    }

    override suspend fun improveAnswer(questionTitle: String, draftAnswer: String): AiAssistantResult {
        val draft = draftAnswer.trim().ifBlank { "اكتب مسودة إجابتك أولاً ليتم تنسيقها." }
        val improved = buildString {
            append("[استجابة تجريبية - يتطلب تفعيل Firebase AI Logic]\n")
            append("صياغة منظمة ومقترحة للإجابة:\n\n")
            append("الخلاصة:\n$draft\n\n")
            append("هيكل مقترح لتعزيز جودة الإجابة:\n")
            append("1. ابدأ بالإجابة المباشرة في السطر الأول.\n")
            append("2. أضف سبباً موجزاً أو دليلاً عملياً يدعم رأيك.\n")
            append("3. اختم بنصيحة سريعة أو خطوة تالية مفيدة لصاحب السؤال.")
        }
        return AiAssistantResult(content = improved)
    }

    override suspend fun summarizeDiscussion(questionTitle: String, answers: List<String>): AiAssistantResult {
        val summary = buildString {
            append("[استجابة تجريبية - يتطلب تفعيل Firebase AI Logic]\n")
            append("ملخص النقاش حول «$questionTitle»:\n\n")
            if (answers.isEmpty()) {
                append("لا توجد إجابات كافية لتلخيصها بعد. شارك أول إجابة مفيدة لكسب نقاط الخبرة!")
            } else {
                append("• إجمالي المشاركات التي تم فحصها: ${answers.size} مشاركة.\n")
                append("• أبرز محور متكرر: التركيز على الحلول العملية والخطوات الواضحة.\n")
                append("• أول مشاركة بارزة: ${answers.first().take(90)}...")
            }
        }
        return AiAssistantResult(content = summary)
    }

    override suspend fun suggestSimilarQuestions(
        query: String,
        existingTitles: List<String>
    ): AiAssistantResult {
        val cleanQuery = query.trim().ifBlank { "المعرفة" }
        val matched = existingTitles
            .filter { it.contains(cleanQuery, ignoreCase = true) }
            .take(3)
        val fallback = listOf(
            "ما هي أفضل الممارسات المتعلقة بـ $cleanQuery؟",
            "كيف أبدأ بشكل عملي في $cleanQuery؟",
            "ما الأخطاء الشائعة عند التعامل مع $cleanQuery؟"
        )
        val combined = (matched + fallback).distinct().take(3)
        return AiAssistantResult(
            content = "[استجابة تجريبية - يتطلب تفعيل Firebase AI Logic]\nأسئلة مشابهة مقترحة بناءً على بحثك:",
            suggestions = combined
        )
    }

    override suspend fun assistSearch(query: String): AiAssistantResult {
        val cleanQuery = query.trim().ifBlank { "التقنية" }
        return AiAssistantResult(
            content = "[استجابة تجريبية - يتطلب تفعيل Firebase AI Logic]\nكلمات مفتاحية مقترحة لتوسيع نتائج البحث عن «$cleanQuery»:",
            suggestions = listOf(
                cleanQuery,
                "شرح $cleanQuery",
                "تجارب $cleanQuery",
                "حل مشكلة $cleanQuery"
            )
        )
    }
}
