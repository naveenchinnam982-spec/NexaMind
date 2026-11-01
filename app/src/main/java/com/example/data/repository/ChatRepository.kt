package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.SettingsEntity
import com.example.data.remote.GeminiApiClient
import com.example.data.service.ChatTitleSummaryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ChatRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context),
    private val apiClient: GeminiApiClient = GeminiApiClient(context),
    private val titleSummaryService: ChatTitleSummaryService = ChatTitleSummaryService(context, database.conversationDao(), apiClient)
) {
    private val conversationDao = database.conversationDao()
    private val messageDao = database.messageDao()
    private val settingsDao = database.settingsDao()

    val conversations: Flow<List<ConversationEntity>> = conversationDao.getAllConversations()

    fun searchConversations(query: String): Flow<List<ConversationEntity>> {
        return if (query.isBlank()) {
            conversationDao.getAllConversations()
        } else {
            conversationDao.searchConversations(query.trim())
        }
    }

    fun getConversation(id: String): Flow<ConversationEntity?> = conversationDao.getConversationById(id)

    fun getMessages(conversationId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForConversation(conversationId)

    val settings: Flow<SettingsEntity> = settingsDao.getSettings().map {
        it ?: SettingsEntity()
    }

    suspend fun updateSettings(settingsEntity: SettingsEntity) {
        settingsDao.insertOrUpdateSettings(settingsEntity)
    }

    suspend fun createNewConversation(
        title: String = "New Chat",
        model: String = "gemini-2.5-flash"
    ): ConversationEntity = withContext(Dispatchers.IO) {
        val newConv = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isPinned = false,
            modelUsed = model
        )
        conversationDao.insertConversation(newConv)
        newConv
    }

    suspend fun updateConversationModel(id: String, model: String) = withContext(Dispatchers.IO) {
        conversationDao.updateModel(id, model)
    }

    suspend fun renameConversation(id: String, newTitle: String) = withContext(Dispatchers.IO) {
        conversationDao.updateTitle(id, newTitle.trim())
    }

    suspend fun summarizeConversationTitle(id: String): String = withContext(Dispatchers.IO) {
        val firstMessage = messageDao.getMessagesForConversationSync(id)
            .firstOrNull { it.role == "user" }?.text ?: return@withContext "Chat"
        val currentSettings = settingsDao.getSettingsSync() ?: SettingsEntity()
        titleSummaryService.analyzeAndSaveTitle(id, firstMessage, currentSettings.customApiKey)
    }

    suspend fun togglePinConversation(id: String, isPinned: Boolean) = withContext(Dispatchers.IO) {
        conversationDao.updatePinStatus(id, isPinned)
    }

    suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
        messageDao.deleteMessagesForConversation(id)
        conversationDao.deleteConversationById(id)
    }

    suspend fun clearAllConversations() = withContext(Dispatchers.IO) {
        conversationDao.clearAllConversations()
    }

    suspend fun setFeedback(messageId: String, feedback: Int) = withContext(Dispatchers.IO) {
        messageDao.updateFeedback(messageId, feedback)
    }

    suspend fun deleteMessage(messageId: String) = withContext(Dispatchers.IO) {
        messageDao.deleteMessageById(messageId)
    }

    /**
     * Sends a user message and generates the assistant response (streaming text or image generation).
     */
    suspend fun sendMessage(
        conversationId: String,
        prompt: String,
        imageUri: Uri? = null,
        forceImageMode: Boolean = false,
        modelOverride: String? = null,
        onChunk: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val currentSettings = settingsDao.getSettingsSync() ?: SettingsEntity()
        val conversation = conversationDao.getConversationByIdSync(conversationId)
            ?: createNewConversation()

        val rawModel = modelOverride ?: conversation.modelUsed.ifBlank { currentSettings.defaultModel }
        val activeModel = apiClient.normalizeModelName(rawModel)
        val isImageIntent = forceImageMode || apiClient.isImageGenerationIntent(prompt, hasAttachment = imageUri != null)

        // 1. Insert User Message
        val userMsgId = UUID.randomUUID().toString()
        val userMessage = MessageEntity(
            id = userMsgId,
            conversationId = conversation.id,
            role = "user",
            text = prompt.trim(),
            imageUrl = imageUri?.toString(),
            isImageGeneration = isImageIntent,
            timestamp = System.currentTimeMillis(),
            status = "SUCCESS"
        )
        messageDao.insertMessage(userMessage)

        // Automatically analyze first user message to generate concise summary title
        val currentMessages = messageDao.getMessagesForConversationSync(conversation.id)
        if (currentMessages.size <= 2 && (conversation.title == "New Chat" || conversation.title.isBlank())) {
            val instantTitle = titleSummaryService.generateSmartFallbackTitle(prompt)
            conversationDao.updateTitle(conversation.id, instantTitle)
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    titleSummaryService.analyzeAndSaveTitle(
                        conversationId = conversation.id,
                        firstUserMessage = prompt,
                        customApiKey = currentSettings.customApiKey
                    )
                } catch (_: Exception) {}
            }
        } else {
            conversationDao.updateTimestamp(conversation.id)
        }

        // 2. Prepare Assistant Message PlaceHolder
        val assistantMsgId = UUID.randomUUID().toString()

        if (isImageIntent) {
            // Image Generation Flow
            val assistantMsg = MessageEntity(
                id = assistantMsgId,
                conversationId = conversation.id,
                role = "assistant",
                text = "Generating your image with Gemini...",
                imageUrl = null,
                isImageGeneration = true,
                promptRefinement = prompt.trim(),
                timestamp = System.currentTimeMillis() + 1,
                status = "GENERATING_IMAGE"
            )
            messageDao.insertMessage(assistantMsg)

            try {
                val (imagePath, companionText) = apiClient.generateImage(
                    prompt = prompt.trim(),
                    customApiKey = currentSettings.customApiKey
                )
                val finalText = companionText ?: "Here is the generated image for: \"${prompt.trim()}\""
                messageDao.updateMessageImage(
                    messageId = assistantMsgId,
                    imageUrl = imagePath,
                    status = "SUCCESS",
                    errorMessage = null
                )
                messageDao.updateMessageContent(
                    messageId = assistantMsgId,
                    text = finalText,
                    status = "SUCCESS",
                    errorMessage = null,
                    suggestedPrompts = "Refine details|Change background|Make it artistic"
                )
                conversationDao.updateTimestamp(conversation.id)
                return@withContext assistantMsgId
            } catch (e: Exception) {
                messageDao.updateMessageContent(
                    messageId = assistantMsgId,
                    text = "Could not generate image: ${e.localizedMessage ?: "Unknown error"}",
                    status = "ERROR",
                    errorMessage = e.localizedMessage ?: "Image generation failed"
                )
                return@withContext assistantMsgId
            }
        } else {
            // Text Chat Streaming Flow
            val assistantMsg = MessageEntity(
                id = assistantMsgId,
                conversationId = conversation.id,
                role = "assistant",
                text = "",
                imageUrl = null,
                isImageGeneration = false,
                timestamp = System.currentTimeMillis() + 1,
                status = "STREAMING"
            )
            messageDao.insertMessage(assistantMsg)

            // Gather context history
            val historyPairs = currentMessages
                .filter { it.id != userMsgId && it.status == "SUCCESS" && !it.isImageGeneration }
                .takeLast(10)
                .map { Pair(it.role, it.text) }

            val accumulatedText = StringBuilder()
            try {
                apiClient.streamChatResponse(
                    conversationHistory = historyPairs,
                    currentPrompt = prompt,
                    attachedImageUri = imageUri,
                    modelName = activeModel,
                    systemPrompt = currentSettings.customInstructions,
                    responseLength = currentSettings.responseLength,
                    customApiKey = currentSettings.customApiKey,
                    enableAutoFallback = currentSettings.autoFallbackOnRateLimit
                ).collect { chunk ->
                    accumulatedText.append(chunk)
                    onChunk(chunk)
                    messageDao.updateMessageContent(
                        messageId = assistantMsgId,
                        text = accumulatedText.toString(),
                        status = "STREAMING",
                        errorMessage = null
                    )
                }

                // Generate contextual follow-up suggestions locally
                val suggestions = apiClient.generateLocalFollowUpSuggestions(prompt, accumulatedText.toString())
                val suggestionsStr = suggestions.joinToString("|")

                messageDao.updateMessageContent(
                    messageId = assistantMsgId,
                    text = accumulatedText.toString(),
                    status = "SUCCESS",
                    errorMessage = null,
                    suggestedPrompts = suggestionsStr
                )
                conversationDao.updateTimestamp(conversation.id)
                return@withContext assistantMsgId
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "Network error"
                messageDao.updateMessageContent(
                    messageId = assistantMsgId,
                    text = if (accumulatedText.isNotEmpty()) accumulatedText.toString() else "Error: $errorMsg",
                    status = "ERROR",
                    errorMessage = errorMsg
                )
                return@withContext assistantMsgId
            }
        }
    }

    /**
     * Regenerates an assistant response from the last exchange.
     */
    suspend fun regenerateResponse(
        conversationId: String,
        assistantMessageId: String,
        modelOverride: String? = null,
        onChunk: (String) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val messages = messageDao.getMessagesForConversationSync(conversationId)
        val assistantIndex = messages.indexOfFirst { it.id == assistantMessageId }
        if (assistantIndex <= 0) return@withContext

        val userMessage = messages[assistantIndex - 1]
        val userPrompt = userMessage.text
        val imageUri = userMessage.imageUrl?.let { Uri.parse(it) }
        val isImageIntent = userMessage.isImageGeneration
        val currentSettings = settingsDao.getSettingsSync() ?: SettingsEntity()

        // Reset assistant message state
        messageDao.updateMessageContent(
            messageId = assistantMessageId,
            text = if (isImageIntent) "Regenerating image..." else "",
            status = if (isImageIntent) "GENERATING_IMAGE" else "STREAMING",
            errorMessage = null,
            suggestedPrompts = null
        )

        if (isImageIntent) {
            try {
                val refinedPrompt = userMessage.promptRefinement ?: userPrompt
                val (imagePath, companionText) = apiClient.generateImage(
                    prompt = refinedPrompt,
                    customApiKey = currentSettings.customApiKey
                )
                val finalText = companionText ?: "Here is the regenerated image for: \"$refinedPrompt\""
                messageDao.updateMessageImage(
                    messageId = assistantMessageId,
                    imageUrl = imagePath,
                    status = "SUCCESS",
                    errorMessage = null
                )
                messageDao.updateMessageContent(
                    messageId = assistantMessageId,
                    text = finalText,
                    status = "SUCCESS",
                    errorMessage = null,
                    suggestedPrompts = "Refine details|Change style|Add lighting"
                )
            } catch (e: Exception) {
                messageDao.updateMessageContent(
                    messageId = assistantMessageId,
                    text = "Regeneration failed: ${e.localizedMessage ?: "Unknown error"}",
                    status = "ERROR",
                    errorMessage = e.localizedMessage ?: "Image regeneration failed"
                )
            }
        } else {
            val conversation = conversationDao.getConversationByIdSync(conversationId)
            val rawModel = modelOverride ?: conversation?.modelUsed?.ifBlank { currentSettings.defaultModel } ?: "gemini-2.5-flash"
            val model = apiClient.normalizeModelName(rawModel)

            val historyPairs = messages.subList(0, assistantIndex - 1)
                .filter { it.status == "SUCCESS" && !it.isImageGeneration }
                .takeLast(10)
                .map { Pair(it.role, it.text) }

            val accumulated = StringBuilder()
            try {
                apiClient.streamChatResponse(
                    conversationHistory = historyPairs,
                    currentPrompt = userPrompt,
                    attachedImageUri = imageUri,
                    modelName = model,
                    systemPrompt = currentSettings.customInstructions,
                    responseLength = currentSettings.responseLength,
                    customApiKey = currentSettings.customApiKey,
                    enableAutoFallback = currentSettings.autoFallbackOnRateLimit
                ).collect { chunk ->
                    accumulated.append(chunk)
                    onChunk(chunk)
                    messageDao.updateMessageContent(
                        messageId = assistantMessageId,
                        text = accumulated.toString(),
                        status = "STREAMING",
                        errorMessage = null
                    )
                }

                val suggestions = apiClient.generateLocalFollowUpSuggestions(userPrompt, accumulated.toString())
                messageDao.updateMessageContent(
                    messageId = assistantMessageId,
                    text = accumulated.toString(),
                    status = "SUCCESS",
                    errorMessage = null,
                    suggestedPrompts = suggestions.joinToString("|")
                )
            } catch (e: Exception) {
                messageDao.updateMessageContent(
                    messageId = assistantMessageId,
                    text = if (accumulated.isNotEmpty()) accumulated.toString() else "Error: ${e.localizedMessage}",
                    status = "ERROR",
                    errorMessage = e.localizedMessage
                )
            }
        }
    }

    /**
     * Edits a previous user message and re-runs the conversation from that turn onwards.
     */
    suspend fun editAndRegenerate(
        conversationId: String,
        userMessageId: String,
        newPrompt: String,
        onChunk: (String) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val messages = messageDao.getMessagesForConversationSync(conversationId)
        val userIndex = messages.indexOfFirst { it.id == userMessageId }
        if (userIndex < 0) return@withContext

        val targetUserMsg = messages[userIndex]
        // Delete all messages starting from the next one
        messageDao.deleteMessagesAfter(conversationId, targetUserMsg.timestamp + 1)

        // Update target user message text
        val updatedUserMsg = targetUserMsg.copy(
            text = newPrompt.trim(),
            timestamp = System.currentTimeMillis()
        )
        messageDao.updateMessage(updatedUserMsg)

        // Now stream new response
        val assistantMsgId = UUID.randomUUID().toString()
        val assistantMsg = MessageEntity(
            id = assistantMsgId,
            conversationId = conversationId,
            role = "assistant",
            text = "",
            timestamp = System.currentTimeMillis() + 1,
            status = "STREAMING"
        )
        messageDao.insertMessage(assistantMsg)

        val currentSettings = settingsDao.getSettingsSync() ?: SettingsEntity()
        val conversation = conversationDao.getConversationByIdSync(conversationId)
        val model = conversation?.modelUsed?.ifBlank { currentSettings.defaultModel } ?: "gemini-flash-latest"

        val historyPairs = messages.subList(0, userIndex)
            .filter { it.status == "SUCCESS" && !it.isImageGeneration }
            .takeLast(10)
            .map { Pair(it.role, it.text) }

        val accumulated = StringBuilder()
        try {
            apiClient.streamChatResponse(
                conversationHistory = historyPairs,
                currentPrompt = newPrompt,
                attachedImageUri = targetUserMsg.imageUrl?.let { Uri.parse(it) },
                modelName = model,
                systemPrompt = currentSettings.customInstructions,
                responseLength = currentSettings.responseLength,
                customApiKey = currentSettings.customApiKey,
                enableAutoFallback = currentSettings.autoFallbackOnRateLimit
            ).collect { chunk ->
                accumulated.append(chunk)
                onChunk(chunk)
                messageDao.updateMessageContent(
                    messageId = assistantMsgId,
                    text = accumulated.toString(),
                    status = "STREAMING",
                    errorMessage = null
                )
            }

            val suggestions = apiClient.generateLocalFollowUpSuggestions(newPrompt, accumulated.toString())
            messageDao.updateMessageContent(
                messageId = assistantMsgId,
                text = accumulated.toString(),
                status = "SUCCESS",
                errorMessage = null,
                suggestedPrompts = suggestions.joinToString("|")
            )
            conversationDao.updateTimestamp(conversationId)
        } catch (e: Exception) {
            messageDao.updateMessageContent(
                messageId = assistantMsgId,
                text = if (accumulated.isNotEmpty()) accumulated.toString() else "Error: ${e.localizedMessage}",
                status = "ERROR",
                errorMessage = e.localizedMessage
            )
        }
    }

    /**
     * Exports conversation to Markdown or Plain text
     */
    suspend fun exportConversation(conversationId: String, format: String): String = withContext(Dispatchers.IO) {
        val conversation = conversationDao.getConversationByIdSync(conversationId) ?: return@withContext ""
        val messages = messageDao.getMessagesForConversationSync(conversationId)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        buildString {
            if (format.lowercase() == "markdown" || format.lowercase() == "md") {
                append("# ${conversation.title}\n\n")
                append("*Exported on ${dateFormat.format(Date())}*\n\n---\n\n")
                for (msg in messages) {
                    val roleLabel = if (msg.role == "user") "### 👤 You" else "### 🤖 Assistant"
                    append("$roleLabel\n\n")
                    if (!msg.imageUrl.isNullOrBlank()) {
                        append("*(Attached/Generated Image: ${msg.imageUrl})*\n\n")
                    }
                    append("${msg.text}\n\n---\n\n")
                }
            } else {
                append("=== ${conversation.title} ===\n")
                append("Date: ${dateFormat.format(Date())}\n\n")
                for (msg in messages) {
                    val roleLabel = if (msg.role == "user") "You: " else "AI: "
                    append("$roleLabel${msg.text}\n\n")
                }
            }
        }
    }
}
