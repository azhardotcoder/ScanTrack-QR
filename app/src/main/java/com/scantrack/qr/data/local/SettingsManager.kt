package com.scantrack.qr.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val DARK_MODE = stringPreferencesKey("dark_mode") // "SYSTEM", "LIGHT", "DARK"
    }

    val hapticEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[HAPTIC_ENABLED] ?: true }

    val darkMode: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[DARK_MODE] ?: "SYSTEM" }

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAPTIC_ENABLED] = enabled
        }
    }

    suspend fun setDarkMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE] = mode
        }
    }
}
