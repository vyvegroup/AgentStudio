package com.agentstudio.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agentstudio.data.model.ChatSession
import com.agentstudio.data.model.FileItem
import com.agentstudio.data.repository.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private val fileRepository = FileRepository(application)
    
    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()
    
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()
    
    private val _workingDirectory = MutableStateFlow(fileRepository.getCurrentDirectory().absolutePath)
    val workingDirectory: StateFlow<String> = _workingDirectory.asStateFlow()
    
    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files.asStateFlow()
    
    private val _selectedFile = MutableStateFlow<FileItem?>(null)
    val selectedFile: StateFlow<FileItem?> = _selectedFile.asStateFlow()
    
    private val _currentScreen = MutableStateFlow(Screen.AGENT)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }
    
    fun createNewSession(title: String = "Cuộc trò chuyện mới"): ChatSession {
        val session = ChatSession(title = title)
        _sessions.update { it + session }
        _currentSessionId.value = session.id
        return session
    }
    
    fun selectSession(sessionId: String) {
        _currentSessionId.value = sessionId
    }
    
    fun deleteSession(sessionId: String) {
        _sessions.update { it.filter { session -> session.id != sessionId } }
        if (_currentSessionId.value == sessionId) {
            _currentSessionId.value = _sessions.value.firstOrNull()?.id
        }
    }
    
    fun updateWorkingDirectory(path: String) {
        val dir = File(path)
        if (dir.exists() && dir.isDirectory) {
            fileRepository.setCurrentDirectory(dir)
            _workingDirectory.value = path
            loadFiles()
        }
    }
    
    fun loadFiles() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = fileRepository.listDirectory(_workingDirectory.value)
            result.fold(
                onSuccess = { files ->
                    _files.value = files
                    _error.value = null
                },
                onFailure = { error ->
                    _error.value = error.message
                }
            )
            _isLoading.value = false
        }
    }
    
    fun selectFile(file: FileItem) {
        _selectedFile.value = file
    }
    
    fun clearSelectedFile() {
        _selectedFile.value = null
    }
    
    fun navigateToParent() {
        val parent = File(_workingDirectory.value).parentFile
        parent?.let {
            updateWorkingDirectory(it.absolutePath)
        }
    }
    
    fun navigateToChild(file: FileItem) {
        if (file.isDirectory) {
            updateWorkingDirectory(file.path)
        }
    }
    
    enum class Screen {
        AGENT,
        FILES,
        SETTINGS
    }
}
