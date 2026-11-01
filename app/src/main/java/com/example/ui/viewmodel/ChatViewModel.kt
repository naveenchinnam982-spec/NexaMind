package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.SettingsEntity
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val conversations: StateFlow<List<ConversationEntity>> = _searchQuery
        .flatMapLatest { query -> repository.searchConversations(query) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeConversation: StateFlow<ConversationEntity?> = _activeConversationId
        .flatMapLatest { id ->
            if (id != null) repository.getConversation(id) else flowOf(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<MessageEntity>> = _activeConversationId
        .flatMapLatest { id ->
            if (id != null) repository.getMessages(id) else flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val settings: StateFlow<SettingsEntity> = repository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsEntity()
        )

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isImageMode = MutableStateFlow(false)
    val isImageMode: StateFlow<Boolean> = _isImageMode.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _isVoiceRecording = MutableStateFlow(false)
    val isVoiceRecording: StateFlow<Boolean> = _isVoiceRecording.asStateFlow()

    private val _inputDraft = MutableStateFlow("")
    val inputDraft: StateFlow<String> = _inputDraft.asStateFlow()

    private var activeJob: Job? = null

    init {
        // Auto-select or create first conversation if available
        viewModelScope.launch {
            repository.conversations.collect { list ->
                if (_activeConversationId.value == null && list.isNotEmpty()) {
                    _activeConversationId.value = list.first().id
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onInputDraftChange(text: String) {
        _inputDraft.value = text
    }

    fun toggleImageMode() {
        _isImageMode.value = !_isImageMode.value
    }

    fun setImageMode(enabled: Boolean) {
        _isImageMode.value = enabled
    }

    fun setSelectedImageUri(uri: Uri?) {
        _selectedImageUri.value = uri
    }

    fun setVoiceRecording(recording: Boolean) {
        _isVoiceRecording.value = recording
    }

    fun appendVoiceTranscription(text: String) {
        if (text.isNotBlank()) {
            val current = _inputDraft.value
            _inputDraft.value = if (current.isBlank()) text else "$current $text"
        }
    }

    fun selectConversation(id: String) {
        if (_isGenerating.value) {
            stopGenerating()
        }
        _activeConversationId.value = id
    }

    fun startNewChat(initialModel: String? = null) {
        if (_isGenerating.value) {
            stopGenerating()
        }
        viewModelScope.launch {
            val model = initialModel ?: settings.value.defaultModel
            val newConv = repository.createNewConversation(model = model)
            _activeConversationId.value = newConv.id
            _inputDraft.value = ""
            _selectedImageUri.value = null
            _isImageMode.value = false
        }
    }

    fun sendMessage(promptText: String? = null) {
        val prompt = (promptText ?: _inputDraft.value).trim()
        val imageUri = _selectedImageUri.value
        val forceImage = _isImageMode.value

        if (prompt.isBlank() && imageUri == null) return
        if (_isGenerating.value) return

        _inputDraft.value = ""
        _selectedImageUri.value = null
        _isImageMode.value = false
        _isGenerating.value = true

        activeJob = viewModelScope.launch {
            var currentId = _activeConversationId.value
            if (currentId == null) {
                val newConv = repository.createNewConversation()
                currentId = newConv.id
                _activeConversationId.value = currentId
            }

            try {
                repository.sendMessage(
                    conversationId = currentId,
                    prompt = prompt,
                    imageUri = imageUri,
                    forceImageMode = forceImage
                )
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun stopGenerating() {
        activeJob?.cancel()
        activeJob = null
        _isGenerating.value = false
    }

    fun regenerate(assistantMessageId: String, modelOverride: String? = null) {
        val convId = _activeConversationId.value ?: return
        if (_isGenerating.value) return

        _isGenerating.value = true
        activeJob = viewModelScope.launch {
            try {
                repository.regenerateResponse(convId, assistantMessageId, modelOverride)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun updateActiveConversationModel(model: String) {
        val convId = _activeConversationId.value
        viewModelScope.launch {
            if (convId != null) {
                repository.updateConversationModel(convId, model)
            }
            val current = settings.value
            repository.updateSettings(current.copy(defaultModel = model))
        }
    }

    fun editAndRegenerate(userMessageId: String, newPrompt: String) {
        val convId = _activeConversationId.value ?: return
        if (_isGenerating.value) return

        _isGenerating.value = true
        activeJob = viewModelScope.launch {
            try {
                repository.editAndRegenerate(convId, userMessageId, newPrompt)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun setFeedback(messageId: String, feedback: Int) {
        viewModelScope.launch {
            repository.setFeedback(messageId, feedback)
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameConversation(id, newTitle)
        }
    }

    fun autoSummarizeTitle(id: String) {
        viewModelScope.launch {
            repository.summarizeConversationTitle(id)
        }
    }

    fun togglePin(id: String, isPinned: Boolean) {
        viewModelScope.launch {
            repository.togglePinConversation(id, isPinned)
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            if (_activeConversationId.value == id) {
                _activeConversationId.value = null
            }
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            repository.clearAllConversations()
            _activeConversationId.value = null
        }
    }

    fun updateSettings(newSettings: SettingsEntity) {
        viewModelScope.launch {
            repository.updateSettings(newSettings)
        }
    }

    suspend fun exportActiveChat(format: String): String {
        val convId = _activeConversationId.value ?: return ""
        return repository.exportConversation(convId, format)
    }
}
