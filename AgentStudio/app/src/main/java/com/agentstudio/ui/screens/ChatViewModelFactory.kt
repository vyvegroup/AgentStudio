package com.agentstudio.ui.screens

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.agentstudio.AgentStudioApp
import com.agentstudio.data.repository.PreferencesRepository

class ChatViewModelFactory(
    private val preferencesRepository: PreferencesRepository,
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(preferencesRepository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
