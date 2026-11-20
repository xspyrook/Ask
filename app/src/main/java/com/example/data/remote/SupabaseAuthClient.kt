package com.example.data.remote

import com.example.data.model.AuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface SignUpOutcome {
    data class Authenticated(val session: AuthSession) : SignUpOutcome
    data class EmailConfirmationRequired(val email: String) : SignUpOutcome
}

class SupabaseAuthClient {

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun signIn(email: String, password: String): Result<AuthSession> =
        withContext(Dispatchers.IO) {
            if (!SupabaseConfig.isConfigured) {
                return@withContext Result.failure(
                    IllegalStateException("مفتاح Supabase Anon Key غير مهيأ في لوحة Secrets.")
                )
            }
            val payload = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
            }
            val request = Request.Builder()
                .url("${SupabaseConfig.supabaseUrl}/auth/v1/token?grant_type=password")
                .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            executeForSession(request, fallbackEmail = email.trim())
        }

    suspend fun signUp(
        email: String,
        password: String,
        username: String,
        fullName: String
    ): Result<SignUpOutcome> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext Result.failure(
                IllegalStateException("مفتاح Supabase Anon Key غير مهيأ في لوحة Secrets.")
            )
        }
        val payload = JSONObject().apply {
            put("email", email.trim())
            put("password", password)
            put("data", JSONObject().apply {
                put("username", username.trim())
                put("full_name", fullName.trim().ifBlank { username.trim() })
            })
        }
        val request = Request.Builder()
            .url("${SupabaseConfig.supabaseUrl}/auth/v1/signup")
            .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(parseErrorMessage(raw, response.code))
                }
                val json = JSONObject(raw)
                val accessToken = json.optString("access_token")
                val userObj = json.optJSONObject("user")
                val userId = userObj?.optString("id").orEmpty().ifBlank { json.optString("id") }
                if (accessToken.isNotBlank() && userId.isNotBlank()) {
                    SignUpOutcome.Authenticated(
                        AuthSession(
                            accessToken = accessToken,
                            refreshToken = json.optString("refresh_token"),
                            userId = userId,
                            email = userObj?.optString("email").orEmpty().ifBlank { email.trim() }
                        )
                    )
                } else {
                    SignUpOutcome.EmailConfirmationRequired(email.trim())
                }
            }
        }
    }

    suspend fun recoverPassword(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) {
            return@withContext Result.failure(
                IllegalStateException("مفتاح Supabase Anon Key غير مهيأ في لوحة Secrets.")
            )
        }
        val payload = JSONObject().apply {
            put("email", email.trim())
        }
        val request = Request.Builder()
            .url("${SupabaseConfig.supabaseUrl}/auth/v1/recover")
            .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(parseErrorMessage(raw, response.code))
                }
            }
        }
    }

    suspend fun refreshSession(refreshToken: String): Result<AuthSession> =
        withContext(Dispatchers.IO) {
            if (!SupabaseConfig.isConfigured || refreshToken.isBlank()) {
                return@withContext Result.failure(IllegalStateException("انتهت صلاحية الجلسة."))
            }
            val payload = JSONObject().apply {
                put("refresh_token", refreshToken)
            }
            val request = Request.Builder()
                .url("${SupabaseConfig.supabaseUrl}/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            executeForSession(request, fallbackEmail = "")
        }

    suspend fun signOut(accessToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured || accessToken.isBlank()) return@withContext Result.success(Unit)
        val request = Request.Builder()
            .url("${SupabaseConfig.supabaseUrl}/auth/v1/logout")
            .addHeader("apikey", SupabaseConfig.supabaseAnonKey)
            .addHeader("Authorization", "Bearer $accessToken")
            .post("{}".toRequestBody(jsonMediaType))
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { /* ignore logout network errors */ }
        }
    }

    private fun executeForSession(request: Request, fallbackEmail: String): Result<AuthSession> {
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(parseErrorMessage(raw, response.code))
                }
                val json = JSONObject(raw)
                val accessToken = json.optString("access_token")
                val refreshToken = json.optString("refresh_token")
                val userObj = json.optJSONObject("user")
                val userId = userObj?.optString("id").orEmpty()
                val email = userObj?.optString("email").orEmpty().ifBlank { fallbackEmail }
                if (accessToken.isBlank() || userId.isBlank()) {
                    throw IllegalStateException("لم يتم استلام بيانات الجلسة من الخادم.")
                }
                AuthSession(accessToken, refreshToken, userId, email)
            }
        }
    }

    companion object {
        fun parseErrorMessage(rawBody: String, statusCode: Int): String {
            val message = runCatching {
                val obj = JSONObject(rawBody)
                obj.optString("msg").ifBlank {
                    obj.optString("message").ifBlank {
                        obj.optString("error_description").ifBlank {
                            obj.optString("error")
                        }
                    }
                }
            }.getOrDefault("")

            return when {
                message.contains("Invalid login credentials", ignoreCase = true) ->
                    "البريد الإلكتروني أو كلمة المرور غير صحيحة."
                message.contains("Email not confirmed", ignoreCase = true) ->
                    "يرجى تأكيد بريدك الإلكتروني أولاً عبر الرابط المرسل إليك."
                message.contains("User already registered", ignoreCase = true) ->
                    "هذا البريد الإلكتروني مسجل مسبقاً."
                message.contains("Password should be", ignoreCase = true) ->
                    "كلمة المرور قصيرة جداً، يرجى إدخال 6 أحرف على الأقل."
                message.contains("row-level security", ignoreCase = true) || statusCode == 403 ->
                    "لا تملك صلاحية تنفيذ هذا الإجراء وفق سياسات الأمان (RLS)."
                statusCode == 401 ->
                    "انتهت صلاحية تسجيل الدخول، يرجى تسجيل الدخول مجدداً."
                message.isNotBlank() -> message
                else -> "تعذر إتمام الطلب (رمز الحالة: $statusCode)."
            }
        }
    }
}
