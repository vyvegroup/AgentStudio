package com.agentstudio

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.agentstudio.data.local.LocalModelManager

class AgentStudioApp : Application() {
    
    lateinit var localModelManager: LocalModelManager
        private set
    
    companion object {
        lateinit var instance: AgentStudioApp private set
        
        private val Context._dataStore: DataStore<Preferences> by preferencesDataStore(name = "agent_studio_prefs")
        
        fun getDataStore(): DataStore<Preferences> {
            return instance._dataStore
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        localModelManager = LocalModelManager(this)
    }
}
