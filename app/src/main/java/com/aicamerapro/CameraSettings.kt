package com.aicamerapro

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cameraSettingsDataStore by preferencesDataStore(name = "camera_settings")

object CameraSettings {
    private val saveModeKey = stringPreferencesKey("save_mode")

    /** Defaults to saving both source and enhanced output. */
    suspend fun readSaveMode(context: Context): String =
        context.cameraSettingsDataStore.data.map { it[saveModeKey] ?: "both" }.first()

    suspend fun writeSaveMode(context: Context, mode: String) {
        context.cameraSettingsDataStore.edit { preferences ->
            preferences[saveModeKey] = if (mode == "enhanced") "enhanced" else "both"
        }
    }
}
