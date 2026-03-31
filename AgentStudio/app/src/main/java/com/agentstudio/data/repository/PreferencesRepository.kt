package com.agentstudio.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.agentstudio.data.model.FREE_MODELS
import com.agentstudio.data.model.ModelInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PreferencesRepository(private val dataStore: DataStore<Preferences>) {
    
    companion object {
        private val SELECTED_MODEL_KEY = stringPreferencesKey("selected_model")
        private val API_KEY_KEY = stringPreferencesKey("api_key")
    }
    
    val selectedModel: Flow<String> = dataStore.data.map { preferences ->
        preferences[SELECTED_MODEL_KEY] ?: FREE_MODELS.first().id
    }
    
    val apiKey: Flow<String> = dataStore.data.map { preferences ->
        preferences[API_KEY_KEY] ?: ""
    }
    
    suspend fun setSelectedModel(modelId: String) {
        dataStore.edit { preferences ->
            preferences[SELECTED_MODEL_KEY] = modelId
        }
    }
    
    suspend fun setApiKey(key: String) {
        dataStore.edit { preferences ->
            preferences[API_KEY_KEY] = key
        }
    }
}
