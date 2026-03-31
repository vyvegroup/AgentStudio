package com.agentstudio

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentstudio.data.repository.PreferencesRepository
import com.agentstudio.ui.screens.ChatScreen
import com.agentstudio.ui.screens.ChatViewModelFactory

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "agent_studio_prefs")

@Composable
fun AgentStudioApp() {
    val context = LocalContext.current
    val preferencesRepository = remember { PreferencesRepository(context.dataStore) }
    
    val viewModel = viewModel<com.agentstudio.ui.screens.ChatViewModel>(
        factory = ChatViewModelFactory(preferencesRepository)
    )
    
    ChatScreen(viewModel = viewModel)
}
