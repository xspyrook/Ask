package com.example

import com.example.data.ai.PlaceholderAiRepository
import com.example.data.remote.SupabaseSchemaMapper
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SupabaseSchemaMapperTest {

    @Test
    fun parseOpenApiSchema_extractsCoreTablesAndColumns() {
        val sampleOpenApi = """
            {
              "definitions": {
                "profiles": {
                  "properties": {
                    "id": {"type": "string"},
                    "username": {"type": "string"},
                    "coins": {"type": "integer"}
                  }
                },
                "questions": {
                  "properties": {
                    "id": {"type": "string"},
                    "title": {"type": "string"},
                    "content": {"type": "string"}
                  }
                },
                "likes": {
                  "properties": {
                    "id": {"type": "string"},
                    "user_id": {"type": "string"},
                    "question_id": {"type": "string"}
                  }
                },
                "comments": {
                  "properties": {
                    "id": {"type": "string"},
                    "question_id": {"type": "string"},
                    "body": {"type": "string"}
                  }
                }
              }
            }
        """.trimIndent()

        val schema = SupabaseSchemaMapper.parseOpenApiSchema(sampleOpenApi)
        assertTrue(schema.inspected)
        assertTrue(schema.hasTable("profiles"))
        assertTrue(schema.hasTable("questions"))
        assertTrue(schema.hasTable("likes"))
        assertTrue(schema.hasTable("comments"))
        assertEquals("content", schema.firstExistingColumn("questions", listOf("content", "body")))
        assertEquals("body", schema.firstExistingColumn("comments", listOf("content", "body")))
    }

    @Test
    fun parseQuestion_mapsProfileAndCountsCorrectly() {
        val profileObj = JSONObject("""{"id":"u1","username":"ahmed","full_name":"أحمد علي","level":4,"xp":800,"is_verified":true}""")
        val profile = SupabaseSchemaMapper.parseProfile(profileObj)
        val questionObj = JSONObject("""{"id":"q1","user_id":"u1","title":"ما أفضل لغة برمجة؟","body":"أريد تعلم تطوير التطبيقات","category":"التقنية والبرمجة"}""")

        val question = SupabaseSchemaMapper.parseQuestion(
            obj = questionObj,
            profilesMap = mapOf("u1" to profile),
            likesCountOverride = 12,
            commentsCountOverride = 5,
            isLikedByMe = true
        )

        assertEquals("أحمد علي", question.authorName)
        assertTrue(question.authorVerified)
        assertEquals(4, question.authorLevel)
        assertEquals(12, question.likesCount)
        assertEquals(5, question.commentsCount)
        assertTrue(question.isLikedByMe)
    }

    @Test
    fun placeholderAiRepository_returnsLabeledPlaceholderNotice() = runTest {
        val repo = PlaceholderAiRepository()
        assertFalse(repo.isLiveGeminiEnabled)
        val result = repo.suggestQuestionRefinement("كيف أتعلم كوتلن", "أريد البدء")
        assertTrue(result.isPlaceholder)
        assertTrue(result.content.contains("استجابة تجريبية"))
        assertTrue(result.noticeText.contains("Firebase"))
    }
}
