package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object SplashRoute

@Serializable
data object LoginRoute

@Serializable
data object SignUpRoute

@Serializable
data object HomeRoute

@Serializable
data object ExploreRoute

@Serializable
data class QuestionDetailRoute(val questionId: String)

@Serializable
data class CreateQuestionRoute(val editQuestionId: String? = null)

@Serializable
data object NotificationsRoute

@Serializable
data class ProfileRoute(val userId: String? = null)

@Serializable
data object EditProfileRoute

@Serializable
data object CoinsLevelsRoute

@Serializable
data object SettingsRoute

@Serializable
data object SearchRoute
