package com.example.data.remote

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit

class GeminiApiClient(private val context: Context) {

    companion object {
        private const val TAG = "GeminiApiClient"
        private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1/chat/completions"
        private const val POLLINATIONS_TEXT_URL = "https://text.pollinations.ai/openai/chat/completions"
        private const val POLLINATIONS_DIRECT_URL = "https://text.pollinations.ai/"

        const val DEFAULT_SYSTEM_PROMPT = """You are a professional, general-purpose AI assistant capable of handling any type of user input: plain questions, document analysis (PDF/image/text), directions and location-based queries, time-sensitive and future-dated queries (e.g. upcoming movies, events, releases, schedules, news), and casual conversation.

GROUNDING & REAL-TIME SEARCH:
- You have access to Google Search grounding. Use it whenever a question depends on real-time, location-specific, or future-dated information — do not rely on your own memory for these.

FOR DIRECTIONS / LOCATION / ROUTE QUERIES:
- Use search grounding to find the actual route between the two locations.
- Give clear, step-by-step directions (major roads, turns, approximate distance and time).
- If the destination is a small village or place with limited map data, say so, and still provide the closest known route to the nearest town or major road, plus a maps link as a backup — don't just hand back a link with no directions.

FOR "UPCOMING" OR FUTURE-DATED QUERIES (movies, events, releases, schedules):
- Current reference date: August 2026.
- Use search grounding to look up current, real information.
- List actual titles/dates found, not guesses.
- If grounded search returns nothing solid for a specific future date, say clearly: "I couldn't find confirmed information on this yet — here's what's been announced so far," rather than staying silent or refusing outright.

FOR UPLOADED FILES (PDF, image, document):
1. First confirm what you received (e.g. "This is a [document type / pages / image] containing...").
2. Extract and summarize the relevant structure (questions, sections, tables, key data) before answering anything based on it.
3. If the file is unreadable, blurry, or empty, say so directly and ask for a re-upload — do not guess its contents.

GENERAL RULE:
- Never respond with silence or a bare link when the user asks a direct question. Always give your best real, grounded answer, and only add a disclaimer if the information is uncertain or unconfirmed — the disclaimer supplements the answer, it doesn't replace it.
- Never fabricate facts, dates, names, or figures.
- Match answer depth to the complexity of the question: be concise by default, expanding into detail and step-by-step working when requested or when the complexity demands it.
- Re-read your draft answer against the user's prompt before outputting to ensure completeness and accuracy.
- Tone: Professional, structured, clear, and direct."""

        // Default backup key (empty by default; configured via Settings or BuildConfig)
        const val BACKUP_API_KEY = ""
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class ProcessedAttachment(
        val mimeType: String,
        val base64Data: String?,
        val extractedText: String?,
        val fileName: String,
        val isImage: Boolean,
        val isPdf: Boolean,
        val isText: Boolean
    )

    /**
     * Resolves the best available API key from user settings, BuildConfig, or default backup.
     */
    fun resolveApiKey(customApiKey: String?): String {
        val trimmedCustom = customApiKey?.trim()
        if (!trimmedCustom.isNullOrBlank() && trimmedCustom != "MY_GEMINI_API_KEY") {
            return trimmedCustom
        }
        val buildConfigKey = try {
            BuildConfig.GEMINI_API_KEY.trim()
        } catch (e: Exception) {
            ""
        }
        if (buildConfigKey.isNotBlank() && buildConfigKey != "MY_GEMINI_API_KEY") {
            return buildConfigKey
        }
        return BACKUP_API_KEY
    }

    /**
     * Detects if the prompt is asking for image generation rather than text/exam answering.
     */
    fun isImageGenerationIntent(prompt: String, hasAttachment: Boolean = false): Boolean {
        if (hasAttachment) return false // Never treat attachments as image generation requests

        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return false

        val textRequestPatterns = listOf(
            Regex(".*\\b(prompt|prompts|prompting|caption|description|script|ideas?|keywords?|negative prompt|instructions?|steps?|text|exam|question|paper|answer|solve)\\b.*", RegexOption.IGNORE_CASE),
            Regex("^(write|give|suggest|brainstorm|explain|help me write|create a prompt|generate a prompt|how to|solve|answer)\\b.*", RegexOption.IGNORE_CASE)
        )
        if (textRequestPatterns.any { it.containsMatchIn(trimmed) }) {
            return false
        }

        val imagePatterns = listOf(
            Regex("^(generate|create|draw|paint|make|render|illustrate|produce|show)\\s+(me\\s+)?(an?\\s+)?(image|picture|photo|illustration|drawing|artwork|painting|sketch|wallpaper|portrait|graphic)\\b.*", RegexOption.IGNORE_CASE),
            Regex("^(draw|paint|illustrate|sketch)\\s+.*", RegexOption.IGNORE_CASE),
            Regex(".*\\b(in 4k|photorealistic|hyperrealistic|cinematic lighting|unreal engine|digital art|oil painting)\\b.*", RegexOption.IGNORE_CASE)
        )
        return imagePatterns.any { it.containsMatchIn(trimmed) }
    }

    /**
     * Normalizes any model name to modern supported models.
     */
    fun normalizeModelName(modelName: String?): String {
        return when (modelName?.trim()) {
            null, "", "gemini-flash-latest" -> "gemini-2.5-flash"
            "gemini-lite", "flash-lite" -> "gemini-2.5-flash-lite"
            "gemini-pro" -> "gemini-3.1-pro-preview"
            else -> modelName.trim()
        }
    }

    private fun getFallbackModels(primaryModel: String): List<String> {
        val allSafeModels = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash",
            "gemini-3.5-flash",
            "gemini-3.1-flash-lite-preview"
        )
        return allSafeModels.filter { it != primaryModel }
    }

    /**
     * Inspects and processes any attached URI (Image, PDF, or Text Document).
     */
    fun processAttachment(uri: Uri): ProcessedAttachment? {
        return try {
            val contentResolver = context.contentResolver
            var rawMimeType = contentResolver.getType(uri) ?: ""
            var fileName = "attachment"

            try {
                val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            fileName = it.getString(nameIndex) ?: "attachment"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to query display name for uri $uri", e)
            }

            val lowerName = fileName.lowercase()
            if (rawMimeType.isBlank() || rawMimeType == "application/octet-stream") {
                rawMimeType = when {
                    lowerName.endsWith(".pdf") -> "application/pdf"
                    lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") -> "image/jpeg"
                    lowerName.endsWith(".png") -> "image/png"
                    lowerName.endsWith(".webp") -> "image/webp"
                    lowerName.endsWith(".txt") || lowerName.endsWith(".md") || lowerName.endsWith(".csv") -> "text/plain"
                    else -> "image/jpeg"
                }
            }

            val isPdf = rawMimeType == "application/pdf" || lowerName.endsWith(".pdf")
            val isImage = rawMimeType.startsWith("image/") || lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".webp")
            val isText = rawMimeType.startsWith("text/") || lowerName.endsWith(".txt") || lowerName.endsWith(".csv") || lowerName.endsWith(".md") || lowerName.endsWith(".json")

            if (isPdf) {
                // Read PDF bytes and encode base64
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val bytes = inputStream?.use { it.readBytes() } ?: return null
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val extractedText = extractBasicTextFromPdf(bytes)
                ProcessedAttachment(
                    mimeType = "application/pdf",
                    base64Data = base64,
                    extractedText = extractedText,
                    fileName = fileName,
                    isImage = false,
                    isPdf = true,
                    isText = false
                )
            } else if (isImage) {
                // Decode bitmap with high fidelity for OCR, scaling down only if extreme
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val originalBitmap = inputStream?.use { BitmapFactory.decodeStream(it) } ?: return null
                
                // Max dimension 2600 to preserve small question text while keeping payload fast
                val maxDim = 2600
                val width = originalBitmap.width
                val height = originalBitmap.height
                val scaledBitmap = if (width > maxDim || height > maxDim) {
                    val ratio = Math.min(maxDim.toFloat() / width, maxDim.toFloat() / height)
                    Bitmap.createScaledBitmap(
                        originalBitmap,
                        (width * ratio).toInt(),
                        (height * ratio).toInt(),
                        true
                    )
                } else {
                    originalBitmap
                }

                val baos = ByteArrayOutputStream()
                val imageMime = if (rawMimeType.contains("png", ignoreCase = true)) {
                    scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                    "image/png"
                } else {
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 92, baos)
                    "image/jpeg"
                }

                val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                ProcessedAttachment(
                    mimeType = imageMime,
                    base64Data = base64,
                    extractedText = null,
                    fileName = fileName,
                    isImage = true,
                    isPdf = false,
                    isText = false
                )
            } else {
                // Text file reading
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val text = inputStream?.use { String(it.readBytes(), StandardCharsets.UTF_8) } ?: ""
                val base64 = Base64.encodeToString(text.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                ProcessedAttachment(
                    mimeType = "text/plain",
                    base64Data = base64,
                    extractedText = text,
                    fileName = fileName,
                    isImage = false,
                    isPdf = false,
                    isText = true
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing attachment $uri", e)
            null
        }
    }

    /**
     * Extracts readable text strings from raw PDF bytes for fallback models.
     */
    private fun extractBasicTextFromPdf(bytes: ByteArray): String? {
        return try {
            val content = String(bytes, StandardCharsets.ISO_8859_1)
            val extracted = StringBuilder()
            val textRegex = Regex("\\((.*?)\\)\\s*T[jJ]")
            val matches = textRegex.findAll(content)
            for (m in matches) {
                val group = m.groupValues[1]
                if (group.isNotBlank()) {
                    extracted.append(group).append(" ")
                }
            }
            if (extracted.length > 50) extracted.toString().trim() else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Streams chat responses with full multimodal support (Image, PDF, Text) and multi-tier fallback.
     */
    fun streamChatResponse(
        conversationHistory: List<Pair<String, String>>,
        currentPrompt: String,
        attachedImageUri: Uri? = null,
        modelName: String = "gemini-2.5-flash",
        systemPrompt: String = "",
        responseLength: String = "balanced",
        customApiKey: String? = null,
        enableAutoFallback: Boolean = true
    ): Flow<String> = flow {
        val effectiveApiKey = resolveApiKey(customApiKey)
        val isOpenRouterKey = effectiveApiKey.startsWith("sk-or-") || effectiveApiKey.startsWith("sk-")
        var streamedAnyOutput = false

        // Process attachment if provided
        val attachment: ProcessedAttachment? = if (attachedImageUri != null) {
            processAttachment(attachedImageUri)
        } else {
            null
        }

        // Adjust prompt if user sent an attachment with a short/empty message
        val effectiveUserPrompt = when {
            currentPrompt.isBlank() && attachment != null -> {
                "Please inspect this uploaded file, confirm what you received, extract and summarize its structure, and answer or provide insights on the contents."
            }
            attachment != null && (currentPrompt.equals("answer this", ignoreCase = true) || currentPrompt.equals("summarize this", ignoreCase = true) || currentPrompt.equals("analyze this", ignoreCase = true)) -> {
                "$currentPrompt. Please confirm what you received, summarize the structure or questions, and provide clear, direct answers."
            }
            else -> currentPrompt
        }

        // Effective system prompt: prioritize professional general-purpose assistant rules
        val effectiveSysPrompt = buildString {
            if (systemPrompt.isNotBlank()) {
                append(systemPrompt)
            } else {
                append(DEFAULT_SYSTEM_PROMPT)
            }
            append("\n\n")
            when (responseLength) {
                "concise" -> append("Style: Be concise by default; expand into detail only if necessary or requested.")
                "detailed" -> append("Style: Provide thorough, step-by-step explanations, complete working, and structured detailed answers.")
                else -> append("Style: Provide clear, balanced, and properly structured Markdown formatting (headings, bullet points, numbered steps) where helpful.")
            }
        }

        // 1. If OpenRouter Key is configured, try OpenRouter Multimodal
        if (isOpenRouterKey) {
            try {
                streamOpenRouterMultimodal(
                    apiKey = effectiveApiKey,
                    history = conversationHistory,
                    prompt = effectiveUserPrompt,
                    attachment = attachment,
                    systemPrompt = effectiveSysPrompt
                ).collect { chunk ->
                    streamedAnyOutput = true
                    emit(chunk)
                }
                return@flow
            } catch (e: Exception) {
                Log.w(TAG, "OpenRouter streaming failed, proceeding to Gemini/Backup", e)
            }
        }

        // 2. Try Google Gemini API (Multimodal: Gemini 2.5 Flash, Gemini 2.0 Flash, Gemini 2.5 Flash Lite)
        if (!isOpenRouterKey && effectiveApiKey.isNotBlank()) {
            val primaryModel = normalizeModelName(modelName)
            val modelsToTry = if (enableAutoFallback) {
                listOf(primaryModel) + getFallbackModels(primaryModel)
            } else {
                listOf(primaryModel)
            }

            for (currentModel in modelsToTry) {
                try {
                    val rootJson = JSONObject()

                    // System Instruction
                    val sysInstructionObj = JSONObject()
                    val sysParts = JSONArray()
                    sysParts.put(JSONObject().put("text", effectiveSysPrompt))
                    sysInstructionObj.put("parts", sysParts)
                    rootJson.put("systemInstruction", sysInstructionObj)

                    // Multiturn History
                    val validHistory = mutableListOf<Pair<String, String>>()
                    for ((role, text) in conversationHistory) {
                        val cleanRole = if (role == "assistant" || role == "model") "model" else "user"
                        val cleanText = text.trim()
                        if (cleanText.isNotBlank()) {
                            if (validHistory.isEmpty()) {
                                if (cleanRole == "user") validHistory.add(Pair(cleanRole, cleanText))
                            } else {
                                val lastRole = validHistory.last().first
                                if (lastRole != cleanRole) {
                                    validHistory.add(Pair(cleanRole, cleanText))
                                } else {
                                    val prev = validHistory.removeAt(validHistory.size - 1)
                                    validHistory.add(Pair(cleanRole, "${prev.second}\n\n$cleanText"))
                                }
                            }
                        }
                    }

                    if (validHistory.isNotEmpty() && validHistory.last().first == "user") {
                        validHistory.removeAt(validHistory.size - 1)
                    }

                    val trimmedHistory = validHistory.takeLast(10)
                    val contentsArray = JSONArray()

                    for ((role, text) in trimmedHistory) {
                        val contentObj = JSONObject()
                        contentObj.put("role", role)
                        val partsArray = JSONArray()
                        partsArray.put(JSONObject().put("text", text))
                        contentObj.put("parts", partsArray)
                        contentsArray.put(contentObj)
                    }

                    // Current user turn with Multimodal Attachment Part (PDF, Image, or Text)
                    val currentTurn = JSONObject()
                    currentTurn.put("role", "user")
                    val currentParts = JSONArray()

                    if (attachment != null && attachment.base64Data != null) {
                        if (attachment.isPdf) {
                            // Gemini native PDF inlineData
                            val inlineData = JSONObject()
                            inlineData.put("mimeType", "application/pdf")
                            inlineData.put("data", attachment.base64Data)
                            currentParts.put(JSONObject().put("inlineData", inlineData))
                        } else if (attachment.isImage) {
                            // Gemini native Image inlineData
                            val inlineData = JSONObject()
                            inlineData.put("mimeType", attachment.mimeType)
                            inlineData.put("data", attachment.base64Data)
                            currentParts.put(JSONObject().put("inlineData", inlineData))
                        } else if (attachment.isText && attachment.extractedText != null) {
                            currentParts.put(JSONObject().put("text", "[Uploaded Document: ${attachment.fileName}]\n\n${attachment.extractedText}\n\n"))
                        }
                    }

                    currentParts.put(JSONObject().put("text", effectiveUserPrompt))
                    currentTurn.put("parts", currentParts)
                    contentsArray.put(currentTurn)

                    rootJson.put("contents", contentsArray)

                    // Tools: Google Search grounding
                    val toolsArray = JSONArray()
                    val searchTool = JSONObject()
                    searchTool.put("google_search", JSONObject())
                    toolsArray.put(searchTool)
                    rootJson.put("tools", toolsArray)

                    // Generation Config: set 8192 tokens so complete responses never cut off
                    val genConfig = JSONObject()
                    genConfig.put("temperature", 0.4)
                    genConfig.put("maxOutputTokens", 8192)
                    rootJson.put("generationConfig", genConfig)

                    val url = "$GEMINI_BASE_URL/models/$currentModel:streamGenerateContent?alt=sse&key=$effectiveApiKey"
                    val requestBody = rootJson.toString().toRequestBody("application/json".toMediaType())
                    val request = Request.Builder()
                        .url(url)
                        .post(requestBody)
                        .addHeader("Content-Type", "application/json")
                        .build()

                    val response = httpClient.newCall(request).execute()

                    if (!response.isSuccessful) {
                        response.close()
                        continue
                    }

                    val source = response.body?.source() ?: continue
                    var receivedChunk = false

                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (line.startsWith("data: ")) {
                            val jsonStr = line.removePrefix("data: ").trim()
                            if (jsonStr.isNotBlank() && jsonStr != "[DONE]") {
                                try {
                                    val chunkObj = JSONObject(jsonStr)
                                    val candidates = chunkObj.optJSONArray("candidates")
                                    if (candidates != null && candidates.length() > 0) {
                                        val candidate = candidates.getJSONObject(0)
                                        val content = candidate.optJSONObject("content")
                                        val parts = content?.optJSONArray("parts")
                                        if (parts != null) {
                                            for (p in 0 until parts.length()) {
                                                val part = parts.getJSONObject(p)
                                                val text = part.optString("text", "")
                                                if (text.isNotEmpty()) {
                                                    emit(text)
                                                    receivedChunk = true
                                                    streamedAnyOutput = true
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Error parsing Gemini SSE chunk", e)
                                }
                            }
                        }
                    }
                    response.close()

                    if (receivedChunk) {
                        return@flow
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Gemini attempt with $currentModel failed", e)
                }
            }
        }

        // 3. OpenRouter Backup using the backup key
        try {
            streamOpenRouterMultimodal(
                apiKey = BACKUP_API_KEY,
                history = conversationHistory,
                prompt = effectiveUserPrompt,
                attachment = attachment,
                systemPrompt = effectiveSysPrompt
            ).collect { chunk ->
                streamedAnyOutput = true
                emit(chunk)
            }
            if (streamedAnyOutput) return@flow
        } catch (e: Exception) {
            Log.w(TAG, "Backup OpenRouter failed", e)
        }

        // 4. Universal High-Reliability AI Fallback (Pollinations Text API)
        try {
            val augmentedPrompt = buildString {
                if (attachment?.extractedText != null) {
                    append("[Uploaded File Content (${attachment.fileName})]:\n\n")
                    append(attachment.extractedText)
                    append("\n\n")
                }
                append(effectiveUserPrompt)
            }

            streamPollinationsResponse(
                history = conversationHistory,
                prompt = augmentedPrompt,
                systemPrompt = effectiveSysPrompt
            ).collect { chunk ->
                streamedAnyOutput = true
                emit(chunk)
            }
            if (streamedAnyOutput) return@flow
        } catch (e: Exception) {
            Log.w(TAG, "Pollinations streaming failed, trying direct text call", e)
        }

        // 5. Final Direct Fallback
        val directResult = fetchDirectTextFallback(effectiveUserPrompt, effectiveSysPrompt)
        if (directResult.isNotBlank()) {
            emit(directResult)
        } else {
            if (attachment != null) {
                emit("I was unable to process the uploaded file completely. Please verify your internet connection, or re-upload a clear image, PDF, or text file.")
            } else {
                emit("I could not generate a response. Please check your connection or try rephrasing your request.")
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Streams via OpenRouter supporting multimodal images and extracted document texts.
     */
    private fun streamOpenRouterMultimodal(
        apiKey: String,
        history: List<Pair<String, String>>,
        prompt: String,
        attachment: ProcessedAttachment?,
        systemPrompt: String
    ): Flow<String> = flow {
        val modelsToTry = listOf(
            "google/gemini-2.5-flash",
            "meta-llama/llama-3.3-70b-instruct:free",
            "deepseek/deepseek-chat",
            "qwen/qwen-2.5-72b-instruct",
            "mistralai/mistral-7b-instruct:free"
        )

        var success = false
        var lastErr: Exception? = null

        for (model in modelsToTry) {
            try {
                val rootJson = JSONObject()
                rootJson.put("model", model)
                rootJson.put("stream", true)
                rootJson.put("max_tokens", 4096)

                val messagesArray = JSONArray()
                if (systemPrompt.isNotBlank()) {
                    messagesArray.put(JSONObject().put("role", "system").put("content", systemPrompt))
                }

                val trimmedHistory = history.takeLast(8)
                for ((role, text) in trimmedHistory) {
                    val cleanRole = if (role == "assistant" || role == "model") "assistant" else "user"
                    messagesArray.put(JSONObject().put("role", cleanRole).put("content", text))
                }

                val userObj = JSONObject()
                userObj.put("role", "user")

                if (attachment != null && attachment.isImage && attachment.base64Data != null) {
                    // Multimodal content array
                    val contentParts = JSONArray()
                    val textPart = JSONObject()
                    textPart.put("type", "text")
                    textPart.put("text", prompt)
                    contentParts.put(textPart)

                    val imagePart = JSONObject()
                    imagePart.put("type", "image_url")
                    val imgUrlObj = JSONObject()
                    imgUrlObj.put("url", "data:${attachment.mimeType};base64,${attachment.base64Data}")
                    imagePart.put("image_url", imgUrlObj)
                    contentParts.put(imagePart)

                    userObj.put("content", contentParts)
                } else {
                    val fullPrompt = if (attachment?.extractedText != null) {
                        "[Attached Document Text]:\n${attachment.extractedText}\n\n[Instruction]: $prompt"
                    } else {
                        prompt
                    }
                    userObj.put("content", fullPrompt)
                }

                messagesArray.put(userObj)
                rootJson.put("messages", messagesArray)

                val request = Request.Builder()
                    .url(OPENROUTER_BASE_URL)
                    .post(rootJson.toString().toRequestBody("application/json".toMediaType()))
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("HTTP-Referer", "https://ai.studio")
                    .addHeader("X-Title", "AI Studio Android")
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    continue
                }

                val source = response.body?.source() ?: continue
                var receivedAny = false

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val jsonStr = line.removePrefix("data: ").trim()
                        if (jsonStr.isNotBlank() && jsonStr != "[DONE]") {
                            try {
                                val chunkObj = JSONObject(jsonStr)
                                val choices = chunkObj.optJSONArray("choices")
                                if (choices != null && choices.length() > 0) {
                                    val choice = choices.getJSONObject(0)
                                    val delta = choice.optJSONObject("delta")
                                    val content = delta?.optString("content", "")
                                    if (!content.isNullOrEmpty()) {
                                        emit(content)
                                        receivedAny = true
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
                response.close()

                if (receivedAny) {
                    success = true
                    break
                }
            } catch (e: Exception) {
                lastErr = e
            }
        }

        if (!success) {
            throw lastErr ?: Exception("OpenRouter stream failed across all models")
        }
    }

    /**
     * Streams via Pollinations AI OpenAI-compatible endpoint.
     */
    private fun streamPollinationsResponse(
        history: List<Pair<String, String>>,
        prompt: String,
        systemPrompt: String
    ): Flow<String> = flow {
        val rootJson = JSONObject()
        rootJson.put("model", "openai")
        rootJson.put("stream", true)

        val messagesArray = JSONArray()
        if (systemPrompt.isNotBlank()) {
            messagesArray.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }

        val trimmedHistory = history.takeLast(8)
        for ((role, text) in trimmedHistory) {
            val cleanRole = if (role == "assistant" || role == "model") "assistant" else "user"
            messagesArray.put(JSONObject().put("role", cleanRole).put("content", text))
        }
        messagesArray.put(JSONObject().put("role", "user").put("content", prompt))
        rootJson.put("messages", messagesArray)

        val request = Request.Builder()
            .url(POLLINATIONS_TEXT_URL)
            .post(rootJson.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw Exception("Pollinations returned ${response.code}")
        }

        val source = response.body?.source() ?: throw Exception("Empty body")
        var receivedAny = false

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.startsWith("data: ")) {
                val jsonStr = line.removePrefix("data: ").trim()
                if (jsonStr.isNotBlank() && jsonStr != "[DONE]") {
                    try {
                        val chunkObj = JSONObject(jsonStr)
                        val choices = chunkObj.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val choice = choices.getJSONObject(0)
                            val delta = choice.optJSONObject("delta")
                            val content = delta?.optString("content", "")
                            if (!content.isNullOrEmpty()) {
                                emit(content)
                                receivedAny = true
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        response.close()

        if (!receivedAny) {
            throw Exception("No stream content received from Pollinations")
        }
    }

    private fun fetchDirectTextFallback(prompt: String, systemPrompt: String): String {
        try {
            val full = "$systemPrompt\n\nUser Question: $prompt"
            val encodedPrompt = URLEncoder.encode(full.take(1000), StandardCharsets.UTF_8.toString())
            val url = "$POLLINATIONS_DIRECT_URL$encodedPrompt"
            val req = Request.Builder().url(url).build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val text = resp.body?.string()?.trim() ?: ""
                resp.close()
                if (text.isNotBlank()) return text
            }
            resp.close()
        } catch (e: Exception) {
            Log.w(TAG, "Direct fallback error", e)
        }
        return ""
    }

    /**
     * Generates a 2-4 word short title for a chat from the first user prompt.
     */
    suspend fun generateShortTitle(prompt: String, customApiKey: String? = null): String = withContext(Dispatchers.IO) {
        val clean = prompt.take(150).trim()
        val apiKey = resolveApiKey(customApiKey)

        if (apiKey.startsWith("sk-")) {
            try {
                val rootJson = JSONObject()
                rootJson.put("model", "meta-llama/llama-3.1-8b-instruct:free")
                val msgs = JSONArray()
                msgs.put(JSONObject().put("role", "system").put("content", "Return only a 2 to 4 word title for this topic. No quotes."))
                msgs.put(JSONObject().put("role", "user").put("content", clean))
                rootJson.put("messages", msgs)

                val req = Request.Builder()
                    .url(OPENROUTER_BASE_URL)
                    .post(rootJson.toString().toRequestBody("application/json".toMediaType()))
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()

                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    resp.close()
                    val json = JSONObject(body)
                    val title = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.trim()
                    if (!title.isNullOrBlank()) return@withContext title
                }
                resp.close()
            } catch (_: Exception) {}
        }

        try {
            val encoded = URLEncoder.encode("Create a 2 to 4 word concise title for this prompt: $clean. Output ONLY the short title.", StandardCharsets.UTF_8.toString())
            val req = Request.Builder().url("$POLLINATIONS_DIRECT_URL$encoded").build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val title = resp.body?.string()?.trim() ?: ""
                resp.close()
                if (title.isNotBlank() && title.length < 50) return@withContext title
            }
            resp.close()
        } catch (_: Exception) {}

        return@withContext ""
    }

    /**
     * Generates an image and returns (localFilePath, optionalCompanionText).
     */
    suspend fun generateImage(
        prompt: String,
        customApiKey: String? = null
    ): Pair<String, String?> = withContext(Dispatchers.IO) {
        try {
            val encodedPrompt = URLEncoder.encode(prompt.trim(), StandardCharsets.UTF_8.toString())
            val imageUrl = "https://image.pollinations.ai/prompt/$encodedPrompt?width=1024&height=1024&nologo=true&enhance=true"

            val req = Request.Builder().url(imageUrl).build()
            val resp = httpClient.newCall(req).execute()

            if (resp.isSuccessful) {
                val bytes = resp.body?.bytes()
                resp.close()
                if (bytes != null && bytes.isNotEmpty()) {
                    val savedPath = saveImageLocally(bytes)
                    return@withContext Pair(savedPath, "Generated high-definition artwork for: \"$prompt\"")
                }
            } else {
                resp.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pollinations image generation failed, trying fallback", e)
        }

        val apiKey = resolveApiKey(customApiKey)
        if (!apiKey.startsWith("sk-")) {
            try {
                val rootJson = JSONObject()
                val instances = JSONArray()
                instances.put(JSONObject().put("prompt", prompt))
                rootJson.put("instances", instances)
                val parameters = JSONObject()
                parameters.put("sampleCount", 1)
                parameters.put("aspectRatio", "1:1")
                rootJson.put("parameters", parameters)

                val url = "$GEMINI_BASE_URL/models/imagen-3.0-generate-002:predict?key=$apiKey"
                val req = Request.Builder()
                    .url(url)
                    .post(rootJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    resp.close()
                    val json = JSONObject(body)
                    val predictions = json.optJSONArray("predictions")
                    if (predictions != null && predictions.length() > 0) {
                        val base64Bytes = predictions.getJSONObject(0).optString("bytesBase64Encoded")
                        if (base64Bytes.isNotEmpty()) {
                            val decoded = Base64.decode(base64Bytes, Base64.DEFAULT)
                            val savedPath = saveImageLocally(decoded)
                            return@withContext Pair(savedPath, "Generated with Imagen 3 for: \"$prompt\"")
                        }
                    }
                } else {
                    resp.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Imagen API generation failed", e)
            }
        }

        throw Exception("Image generation failed. Please check network connection and try again.")
    }

    private fun saveImageLocally(bytes: ByteArray): String {
        val dir = File(context.filesDir, "generated_images")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, "img_${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { out ->
            out.write(bytes)
            out.flush()
        }
        return file.absolutePath
    }

    /**
     * Generates 3 contextual follow-up chips based on user prompt and response.
     */
    fun generateLocalFollowUpSuggestions(prompt: String, response: String): List<String> {
        val lowerPrompt = prompt.lowercase()
        val lowerResp = response.lowercase()
        return when {
            lowerPrompt.contains("pdf") || lowerPrompt.contains("document") || lowerPrompt.contains("file") || lowerResp.contains("pdf") || lowerResp.contains("document") -> {
                listOf("Summarize the key takeaways", "Extract action items / data", "Explain section by section")
            }
            lowerPrompt.contains("where") || lowerPrompt.contains("location") || lowerPrompt.contains("near me") || lowerPrompt.contains("city") || lowerPrompt.contains("place") -> {
                listOf("Provide directions / best route", "What are the operating hours?", "Suggest alternatives nearby")
            }
            lowerPrompt.contains("movie") || lowerPrompt.contains("news") || lowerPrompt.contains("schedule") || lowerPrompt.contains("event") || lowerPrompt.contains("current") -> {
                listOf("What are the reviews & ratings?", "Provide exact release/event dates", "Where can I watch/find this?")
            }
            lowerPrompt.contains("code") || lowerPrompt.contains("function") || lowerPrompt.contains("kotlin") || lowerPrompt.contains("python") || lowerPrompt.contains("bug") -> {
                listOf("Explain this step-by-step", "Write unit tests for this", "Optimize performance & memory")
            }
            lowerPrompt.contains("image") || lowerPrompt.contains("art") || lowerPrompt.contains("draw") -> {
                listOf("Make it cinematic lighting", "Change color palette", "Generate in watercolor style")
            }
            lowerPrompt.contains("explain") || lowerPrompt.contains("what is") || lowerPrompt.contains("how to") -> {
                listOf("Give a real-world example", "Explain in simple terms", "What are the common pitfalls?")
            }
            else -> {
                listOf("Tell me more about this", "Summarize key points", "What are the practical next steps?")
            }
        }
    }
}
