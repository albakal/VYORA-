package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "vyora_prefs")

data class AppPreferences(
    val language: String = "ar",
    val themeMode: String = "dark"
)

class AppPreferencesRepository(private val context: Context) {
    private val keyLanguage = stringPreferencesKey("language")
    private val keyTheme = stringPreferencesKey("theme_mode")

    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        AppPreferences(
            language = prefs[keyLanguage] ?: "ar",
            themeMode = prefs[keyTheme] ?: "dark"
        )
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { it[keyLanguage] = language }
    }

    suspend fun setThemeMode(themeMode: String) {
        context.dataStore.edit { it[keyTheme] = themeMode }
    }
}
