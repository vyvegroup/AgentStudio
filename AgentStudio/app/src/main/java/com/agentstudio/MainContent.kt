package com.agentstudio

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentstudio.data.repository.PreferencesRepository
import com.agentstudio.ui.screens.ChatScreen
import com.agentstudio.ui.screens.ChatViewModelFactory

@Composable
fun AgentStudioContent() {
    val dataStore = remember { AgentStudioApp.getDataStore() }
    val preferencesRepository = remember { PreferencesRepository(dataStore) }
    
    val viewModel = viewModel<com.agentstudio.ui.screens.ChatViewModel>(
        factory = ChatViewModelFactory(preferencesRepository, AgentStudioApp.instance)
    )
    
    ChatScreen(viewModel = viewModel)
}
