package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AuthSession
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hala_sual_prefs")

data class AppPreferencesState(
    val session: AuthSession? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val answerAlertsEnabled: Boolean = true,
    val likeAlertsEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false
)

class SessionPreferences(private val context: Context) {

    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val ANSWER_ALERTS = booleanPreferencesKey("answer_alerts")
        val LIKE_ALERTS = booleanPreferencesKey("like_alerts")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val preferencesFlow: Flow<AppPreferencesState> = context.dataStore.data.map { prefs ->
        val token = prefs[Keys.ACCESS_TOKEN].orEmpty()
        val refresh = prefs[Keys.REFRESH_TOKEN].orEmpty()
        val uid = prefs[Keys.USER_ID].orEmpty()
        val email = prefs[Keys.USER_EMAIL].orEmpty()
        val session = if (token.isNotBlank() && uid.isNotBlank()) {
            AuthSession(token, refresh, uid, email)
        } else null

        val themeMode = runCatching {
            ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)

        AppPreferencesState(
            session = session,
            themeMode = themeMode,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            answerAlertsEnabled = prefs[Keys.ANSWER_ALERTS] ?: true,
            likeAlertsEnabled = prefs[Keys.LIKE_ALERTS] ?: true,
            onboardingCompleted = prefs[Keys.ONBOARDING_DONE] ?: false
        )
    }

    suspend fun getSession(): AuthSession? = preferencesFlow.first().session

    suspend fun saveSession(session: AuthSession) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = session.accessToken
            prefs[Keys.REFRESH_TOKEN] = session.refreshToken
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.USER_EMAIL] = session.email
            prefs[Keys.ONBOARDING_DONE] = true
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.USER_EMAIL)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setAnswerAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ANSWER_ALERTS] = enabled
        }
    }

    suspend fun setLikeAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LIKE_ALERTS] = enabled
        }
    }

    suspend fun markOnboardingCompleted() {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_DONE] = true
        }
    }
}
