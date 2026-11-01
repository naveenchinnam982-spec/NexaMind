package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.entity.MessageEntity
import com.example.data.local.entity.SettingsEntity
import com.example.ui.components.ChatInputArea
import com.example.ui.components.EditMessageDialog
import com.example.ui.components.GeneratedImageCard
import com.example.ui.components.MarkdownContent
import com.example.ui.components.SettingsDialog
import com.example.ui.components.SidebarDrawerContent
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val activeConversationId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val activeConversation by viewModel.activeConversation.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val isImageMode by viewModel.isImageMode.collectAsStateWithLifecycle()
    val selectedImageUri by viewModel.selectedImageUri.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val inputDraft by viewModel.inputDraft.collectAsStateWithLifecycle()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var editingUserMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var showModelMenu by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Auto scroll to bottom when new messages arrive or while streaming
    val lastMessageText = messages.lastOrNull()?.text ?: ""
    val lastMessageId = messages.lastOrNull()?.id ?: ""
    LaunchedEffect(messages.size, lastMessageId, lastMessageText) {
        if (messages.isNotEmpty()) {
            // LazyColumn has: item (index 0), items(messages) (indices 1..messages.size), item (index messages.size + 1)
            val targetIndex = messages.size // index of last message item
            if (isGenerating) {
                listState.scrollToItem(targetIndex)
            } else {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    val fontSizeMultiplier = when (settings.fontSizeScale) {
        "small" -> 0.9f
        "large" -> 1.15f
        "extra_large" -> 1.3f
        else -> 1.0f
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SidebarDrawerContent(
                conversations = conversations,
                activeConversationId = activeConversationId,
                searchQuery = searchQuery,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onSelectConversation = { id ->
                    viewModel.selectConversation(id)
                    scope.launch { drawerState.close() }
                },
                onNewChat = {
                    viewModel.startNewChat()
                    scope.launch { drawerState.close() }
                },
                onRenameConversation = viewModel::renameConversation,
                onTogglePin = viewModel::togglePin,
                onDeleteConversation = viewModel::deleteConversation,
                onClearAllConversations = viewModel::clearAllConversations,
                onOpenSettings = {
                    showSettingsDialog = true
                    scope.launch { drawerState.close() }
                },
                onToggleTheme = {
                    val nextTheme = if (settings.themeMode == "dark") "light" else "dark"
                    viewModel.updateSettings(settings.copy(themeMode = nextTheme))
                },
                isDarkTheme = settings.themeMode == "dark",
                onExportConversation = { id, format -> viewModel.exportActiveChat(format) },
                onAutoSummarizeTitle = viewModel::autoSummarizeTitle
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = activeConversation?.title ?: "AI Chat Assistant",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // Active Model Tag
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { showModelMenu = true }
                                    .padding(vertical = 1.dp)
                            ) {
                                val modelLabel = when (activeConversation?.modelUsed ?: settings.defaultModel) {
                                    "gemini-2.5-flash", "gemini-flash-latest" -> "Gemini 2.5 Flash"
                                    "gemini-2.5-flash-lite" -> "Gemini 2.5 Flash Lite"
                                    "gemini-2.0-flash" -> "Gemini 2.0 Flash"
                                    "gemini-3.1-flash-lite-preview" -> "Gemini 3.1 Flash Lite"
                                    "gemini-3.1-pro-preview" -> "Gemini 3.1 Pro"
                                    "gemini-2.5-flash-image" -> "Gemini Flash Image"
                                    else -> "Gemini 2.5 Flash"
                                }
                                Text(
                                    text = "$modelLabel ▾",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )

                                DropdownMenu(
                                    expanded = showModelMenu,
                                    onDismissRequest = { showModelMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Gemini 2.5 Flash (Fast & Reliable • Recommended)") },
                                        onClick = {
                                            showModelMenu = false
                                            viewModel.updateActiveConversationModel("gemini-2.5-flash")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Gemini 2.5 Flash Lite (Ultra Fast • Max Quota)") },
                                        onClick = {
                                            showModelMenu = false
                                            viewModel.updateActiveConversationModel("gemini-2.5-flash-lite")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Gemini 3.5 Flash (General Intelligence • Default)") },
                                        onClick = {
                                            showModelMenu = false
                                            viewModel.updateActiveConversationModel("gemini-3.5-flash")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Gemini 2.5 Flash (Fast & Balanced)") },
                                        onClick = {
                                            showModelMenu = false
                                            viewModel.updateActiveConversationModel("gemini-2.5-flash")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Gemini 3.1 Flash Lite (Ultra Fast • Max Quota)") },
                                        onClick = {
                                            showModelMenu = false
                                            viewModel.updateActiveConversationModel("gemini-3.1-flash-lite-preview")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Gemini 3.1 Pro (Reasoning & STEM)") },
                                        onClick = {
                                            showModelMenu = false
                                            viewModel.updateActiveConversationModel("gemini-3.1-pro-preview")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Gemini 2.5 Flash Image (Artist)") },
                                        onClick = {
                                            showModelMenu = false
                                            viewModel.updateActiveConversationModel("gemini-2.5-flash-image")
                                        }
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("open_drawer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Sidebar"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.startNewChat() },
                            modifier = Modifier.testTag("new_chat_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Chat"
                            )
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val exportText = viewModel.exportActiveChat("markdown")
                                    if (exportText.isNotBlank()) {
                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cb.setPrimaryClip(ClipData.newPlainText("Chat Export (Markdown)", exportText))
                                        
                                        // Also launch native system share dialog for Markdown sharing
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/markdown"
                                            putExtra(Intent.EXTRA_SUBJECT, activeConversation?.title ?: "Chat Export")
                                            putExtra(Intent.EXTRA_TEXT, exportText)
                                        }
                                        val chooser = Intent.createChooser(shareIntent, "Save or Share Chat (Markdown)")
                                        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        try {
                                            context.startActivity(chooser)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Chat copied to clipboard as Markdown", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "No messages to export", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.testTag("export_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Export Chat as Markdown"
                            )
                        }
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.testTag("settings_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                ChatInputArea(
                    inputText = inputDraft,
                    onInputTextChange = viewModel::onInputDraftChange,
                    onSendMessage = { viewModel.sendMessage() },
                    onStopGenerating = { viewModel.stopGenerating() },
                    isGenerating = isGenerating,
                    isImageMode = isImageMode,
                    onToggleImageMode = viewModel::toggleImageMode,
                    attachedImageUri = selectedImageUri,
                    onImageSelected = viewModel::setSelectedImageUri,
                    fontSizeMultiplier = fontSizeMultiplier,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                )
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (messages.isEmpty()) {
                    // Empty Chat Greeting & Starters
                    WelcomeEmptyState(
                        onSelectPrompt = { prompt, isImage ->
                            if (isImage) {
                                viewModel.setImageMode(true)
                            }
                            viewModel.sendMessage(prompt)
                        },
                        fontSizeMultiplier = fontSizeMultiplier
                    )
                } else {
                    // Messages LazyColumn
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }

                        items(messages, key = { it.id }) { message ->
                            val isLastBotMessage = message.role == "assistant" && message.id == messages.lastOrNull { it.role == "assistant" }?.id

                            if (message.role == "user") {
                                UserMessageBubble(
                                    message = message,
                                    onEdit = { editingUserMessage = message },
                                    fontSizeMultiplier = fontSizeMultiplier
                                )
                            } else {
                                AssistantMessageBubble(
                                    message = message,
                                    isLastBotMessage = isLastBotMessage,
                                    isGenerating = isGenerating && message.status == "STREAMING",
                                    onRegenerate = { viewModel.regenerate(message.id) },
                                    onRegenerateWithModel = { modelKey ->
                                        viewModel.updateActiveConversationModel(modelKey)
                                        viewModel.regenerate(message.id, modelKey)
                                    },
                                    onOpenSettings = { showSettingsDialog = true },
                                    onFeedback = { feedback -> viewModel.setFeedback(message.id, feedback) },
                                    onSelectSuggestion = { suggestion -> viewModel.sendMessage(suggestion) },
                                    onRefineImagePrompt = { prompt ->
                                        viewModel.sendMessage(prompt)
                                    },
                                    fontSizeMultiplier = fontSizeMultiplier
                                )
                            }
                        }

                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }
                }
            }
        }
    }

    // Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            currentSettings = settings,
            onDismiss = { showSettingsDialog = false },
            onSaveSettings = { updated ->
                viewModel.updateSettings(updated)
            }
        )
    }

    // Edit Message Dialog
    editingUserMessage?.let { userMsg ->
        EditMessageDialog(
            originalText = userMsg.text,
            onDismiss = { editingUserMessage = null },
            onConfirm = { newText ->
                viewModel.editAndRegenerate(userMsg.id, newText)
                editingUserMessage = null
            }
        )
    }
}

@Composable
fun UserMessageBubble(
    message: MessageEntity,
    onEdit: () -> Unit,
    fontSizeMultiplier: Float
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .testTag("user_message_bubble")
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Attached image or document if any
                if (!message.imageUrl.isNullOrBlank()) {
                    val fileUriStr = message.imageUrl
                    val isPdf = fileUriStr.contains(".pdf", ignoreCase = true) || fileUriStr.contains("pdf", ignoreCase = true)
                    val isTextDoc = fileUriStr.contains(".txt", ignoreCase = true) || fileUriStr.contains(".csv", ignoreCase = true)
                    if (isPdf || isTextDoc) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isPdf) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = if (isPdf) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isPdf) "Document (PDF)" else "Text Document",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Processed by AI Assistant",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        AsyncImage(
                            model = message.imageUrl,
                            contentDescription = "Attached image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .padding(bottom = 8.dp)
                        )
                    }
                }

                Text(
                    text = message.text,
                    fontSize = (15 * fontSizeMultiplier).sp,
                    lineHeight = (22 * fontSizeMultiplier).sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Action icons for user message (Edit, Copy)
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp, end = 4.dp)
        ) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit message",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
            IconButton(
                onClick = {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cb.setPrimaryClip(ClipData.newPlainText("Message", message.text))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy message",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantMessageBubble(
    message: MessageEntity,
    isLastBotMessage: Boolean,
    isGenerating: Boolean,
    onRegenerate: () -> Unit,
    onRegenerateWithModel: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onFeedback: (Int) -> Unit,
    onSelectSuggestion: (String) -> Unit,
    onRefineImagePrompt: (String) -> Unit,
    fontSizeMultiplier: Float
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2000)
            copied = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalAlignment = Alignment.Top
    ) {
        // AI Glowing Avatar
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            Color(0xFF38BDF8)
                        )
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "AI Avatar",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Error State Card
            if (message.status == "ERROR") {
                val errorText = message.errorMessage ?: message.text
                val isRateLimit = errorText.contains("429") || errorText.contains("Rate limit", ignoreCase = true) || errorText.contains("quota", ignoreCase = true) || errorText.contains("RESOURCE_EXHAUSTED", ignoreCase = true)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRateLimit) "Rate Limit / Quota Exceeded" else "Generation Error",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isRateLimit) {
                                "The free Gemini API request quota was reached. You can retry with Flash Lite (higher limit), wait 30 seconds, or add your custom key in Settings."
                            } else {
                                errorText
                            },
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isRateLimit) {
                                Button(
                                    onClick = { onRegenerateWithModel("gemini-2.5-flash-lite") },
                                    modifier = Modifier.testTag("retry_flash_lite_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Try with Flash Lite", fontSize = 11.5.sp)
                                }
                            }
                            OutlinedButton(
                                onClick = onRegenerate,
                                modifier = Modifier.testTag("retry_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry", fontSize = 11.5.sp)
                            }
                            TextButton(
                                onClick = onOpenSettings,
                                modifier = Modifier.testTag("open_settings_error_button")
                            ) {
                                Text("Settings / Key", fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            } else if (message.isImageGeneration) {
                // Generated Image Card
                GeneratedImageCard(
                    imagePath = message.imageUrl,
                    isLoading = message.status == "GENERATING_IMAGE",
                    prompt = message.promptRefinement,
                    onRegenerate = onRegenerate,
                    onRefinePrompt = onRefineImagePrompt
                )
            } else {
                // Text Markdown Response
                val isCurrentlyStreaming = message.status == "STREAMING" && isGenerating && isLastBotMessage
                if (message.text.isBlank() && isGenerating) {
                    // Animated Thinking / Generating indicator
                    TypingThinkingIndicator()
                } else {
                    MarkdownContent(
                        markdown = message.text,
                        fontSizeMultiplier = fontSizeMultiplier,
                        isStreaming = isCurrentlyStreaming
                    )
                }
            }

            // Action Buttons Bar under response
            if (message.status == "SUCCESS" || (message.status == "STREAMING" && !isGenerating)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Copy button
                    IconButton(
                        onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("AI Response", message.text))
                            copied = true
                            Toast.makeText(context, "Copied response", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy response",
                            tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Like Feedback
                    IconButton(
                        onClick = { onFeedback(if (message.feedback == 1) 0 else 1) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (message.feedback == 1) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Good response",
                            tint = if (message.feedback == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Dislike Feedback
                    IconButton(
                        onClick = { onFeedback(if (message.feedback == -1) 0 else -1) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (message.feedback == -1) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = "Poor response",
                            tint = if (message.feedback == -1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Regenerate (for latest message or any message)
                    if (isLastBotMessage) {
                        OutlinedButton(
                            onClick = onRegenerate,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Regenerate", fontSize = 11.sp)
                        }
                    }
                }

                // Suggested Follow-up chips
                val suggestions = message.suggestedPrompts?.split("|")?.filter { it.isNotBlank() }
                if (!suggestions.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        suggestions.forEach { suggestion ->
                            SuggestionChip(
                                onClick = { onSelectSuggestion(suggestion) },
                                label = { Text(suggestion, fontSize = 12.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TypingThinkingIndicator() {
    val transition = rememberInfiniteTransition(label = "thinkingPulse")
    val dot1 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dot1"
    )
    val dot2 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(450, delayMillis = 150, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dot2"
    )
    val dot3 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(450, delayMillis = 300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dot3"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "Gemini is typing",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(2.dp))
            Box(modifier = Modifier.size(6.dp).scale(dot1).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Box(modifier = Modifier.size(6.dp).scale(dot2).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Box(modifier = Modifier.size(6.dp).scale(dot3).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WelcomeEmptyState(
    onSelectPrompt: (String, Boolean) -> Unit,
    fontSizeMultiplier: Float
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Logo & Glow
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            Color(0xFF38BDF8)
                        )
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "What can I help with today?",
            fontSize = (20 * fontSizeMultiplier).sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Analyze documents, answer complex questions, code, and explore ideas.",
            fontSize = (13 * fontSizeMultiplier).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Starter Action Cards
        val starterCards = listOf(
            StarterPrompt(
                title = "📄 Analyze Document or PDF",
                desc = "Extract key insights, summaries, or answer questions",
                prompt = "How can you help me summarize and extract key data from uploaded PDF documents and images?",
                isImage = false
            ),
            StarterPrompt(
                title = "📍 Local & Travel Guide",
                desc = "Explore locations, schedules, and recommendations",
                prompt = "What are the top cultural landmarks and must-try cuisines in Kyoto, Japan?",
                isImage = false
            ),
            StarterPrompt(
                title = "💻 Code & Technical Reasoning",
                desc = "Write, debug, and optimize algorithms & architectures",
                prompt = "Explain how coroutine Dispatchers work in Kotlin and compare Dispatchers.IO vs Dispatchers.Default",
                isImage = false
            ),
            StarterPrompt(
                title = "🎨 Generate Artwork",
                desc = "Create visuals with Gemini image generation",
                prompt = "Generate an image of a cybernetic dragon perched on a neon skyscraper in cyberpunk style",
                isImage = true
            )
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            starterCards.forEach { card ->
                Card(
                    onClick = { onSelectPrompt(card.prompt, card.isImage) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = card.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = card.desc,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

data class StarterPrompt(
    val title: String,
    val desc: String,
    val prompt: String,
    val isImage: Boolean
)
