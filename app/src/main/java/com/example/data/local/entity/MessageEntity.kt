package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["conversationId"])]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val role: String, // "user" or "assistant"
    val text: String,
    val imageUrl: String? = null,
    val isImageGeneration: Boolean = false,
    val promptRefinement: String? = null,
    val feedback: Int = 0, // 0 = none, 1 = liked, -1 = disliked
    val suggestedPrompts: String? = null, // pipe or json-separated follow-up suggestions
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "SUCCESS", // "SUCCESS", "STREAMING", "ERROR", "GENERATING_IMAGE"
    val errorMessage: String? = null
)
