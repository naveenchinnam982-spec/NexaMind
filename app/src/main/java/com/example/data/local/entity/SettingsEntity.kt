package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class SettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val themeMode: String = "dark", // "dark", "light", "system"
    val responseLength: String = "balanced", // "concise", "balanced", "detailed"
    val customInstructions: String = "",
    val fontSizeScale: String = "medium", // "small", "medium", "large", "extra_large"
    val defaultModel: String = "gemini-2.5-flash",
    val customApiKey: String = "",
    val autoFallbackOnRateLimit: Boolean = true
)
