package com.example.data.service

import android.content.Context
import android.util.Log
import com.example.data.local.dao.ConversationDao
import com.example.data.remote.GeminiApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatTitleSummaryService(
    private val context: Context,
    private val conversationDao: ConversationDao,
    private val apiClient: GeminiApiClient
) {
    companion object {
        private const val TAG = "ChatTitleSummaryService"
    }

    /**
     * Generates a concise, context-aware 2-5 word title for a conversation
     * based on the initial user message, saving it to the database.
     */
    suspend fun analyzeAndSaveTitle(
        conversationId: String,
        firstUserMessage: String,
        customApiKey: String? = null
    ): String = withContext(Dispatchers.IO) {
        val trimmed = firstUserMessage.trim()
        if (trimmed.isEmpty()) return@withContext "New Chat"

        var title: String
        try {
            title = apiClient.generateShortTitle(trimmed, customApiKey)
            if (title.isBlank() || title.length > 50 || title.contains("Error", ignoreCase = true)) {
                title = generateSmartFallbackTitle(trimmed)
            }
        } catch (e: Exception) {
            Log.w(TAG, "AI title generation failed, using heuristic fallback", e)
            title = generateSmartFallbackTitle(trimmed)
        }

        val cleanTitle = cleanUpTitle(title)
        try {
            conversationDao.updateTitle(conversationId, cleanTitle)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update title in DB", e)
        }
        cleanTitle
    }

    /**
     * Heuristic fallback title generator when offline or API call is skipped.
     */
    fun generateSmartFallbackTitle(prompt: String): String {
        val clean = prompt
            .replace(Regex("^(can you|please|help me|i want to|how do i|how to|what is|tell me about|give me|write a|generate a|create a)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
            .capitalizeFirstLetter()

        val words = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
        val maxWords = words.take(5).joinToString(" ")
        return if (maxWords.length > 32) {
            maxWords.take(30).trimEnd() + "…"
        } else if (maxWords.isNotBlank()) {
            maxWords
        } else {
            "New Chat"
        }
    }

    private fun cleanUpTitle(raw: String): String {
        var res = raw.trim().removeSurrounding("\"", "\"").removeSurrounding("'", "'").trim()
        res = res.replace(Regex("^Title:\\s*", RegexOption.IGNORE_CASE), "")
        res = res.replace(Regex("^[#*•\\-\\s]+"), "")
        if (res.length > 40) {
            res = res.take(38).trimEnd() + "…"
        }
        return if (res.isBlank()) "Chat" else res.capitalizeFirstLetter()
    }

    private fun String.capitalizeFirstLetter(): String {
        return if (isNotEmpty()) this[0].uppercaseChar() + substring(1) else this
    }
}
