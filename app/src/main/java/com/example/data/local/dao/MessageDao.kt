package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesForConversationSync(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET text = :text, status = :status, errorMessage = :errorMessage, suggestedPrompts = :suggestedPrompts WHERE id = :messageId")
    suspend fun updateMessageContent(
        messageId: String,
        text: String,
        status: String,
        errorMessage: String? = null,
        suggestedPrompts: String? = null
    )

    @Query("UPDATE messages SET imageUrl = :imageUrl, status = :status, errorMessage = :errorMessage WHERE id = :messageId")
    suspend fun updateMessageImage(
        messageId: String,
        imageUrl: String,
        status: String,
        errorMessage: String? = null
    )

    @Query("UPDATE messages SET feedback = :feedback WHERE id = :messageId")
    suspend fun updateFeedback(messageId: String, feedback: Int)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND timestamp >= :timestamp")
    suspend fun deleteMessagesAfter(conversationId: String, timestamp: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)
}
