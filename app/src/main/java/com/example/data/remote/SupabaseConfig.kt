package com.example.data.remote

import com.example.BuildConfig

object SupabaseConfig {
    const val PROJECT_REF = "qievpkbojqqswvntxiru"

    val supabaseUrl: String
        get() = BuildConfig.SUPABASE_URL
            .ifBlank { "https://$PROJECT_REF.supabase.co" }
            .trimEnd('/')

    val supabaseAnonKey: String
        get() = BuildConfig.SUPABASE_ANON_KEY.trim()

    val isConfigured: Boolean
        get() = supabaseUrl.startsWith("https://") &&
            supabaseAnonKey.isNotBlank() &&
            supabaseAnonKey != "MY_SUPABASE_ANON_KEY"
}
