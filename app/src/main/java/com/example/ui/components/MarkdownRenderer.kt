package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    fontSizeMultiplier: Float = 1.0f,
    isStreaming: Boolean = false
) {
    val cursorTransition = rememberInfiniteTransition(label = "cursorBlink")
    val cursorAlpha by cursorTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    SelectionContainer {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
            for ((index, block) in blocks.withIndex()) {
                val isLastBlock = index == blocks.lastIndex
                when (block) {
                    is MarkdownBlock.Heading -> {
                        val headingSize = when (block.level) {
                            1 -> (22 * fontSizeMultiplier).sp
                            2 -> (18 * fontSizeMultiplier).sp
                            else -> (16 * fontSizeMultiplier).sp
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MarkdownStyledText(
                                text = block.text,
                                fontSize = headingSize,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            if (isStreaming && isLastBlock) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(headingSize.value.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = cursorAlpha))
                                )
                            }
                        }
                    }
                    is MarkdownBlock.Paragraph -> {
                        Row(verticalAlignment = Alignment.Bottom) {
                            MarkdownStyledText(
                                text = block.text,
                                fontSize = (15 * fontSizeMultiplier).sp,
                                lineHeight = (22 * fontSizeMultiplier).sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = if (isStreaming && isLastBlock) Modifier.weight(1f, fill = false) else Modifier
                            )
                            if (isStreaming && isLastBlock) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Box(
                                    modifier = Modifier
                                        .padding(bottom = 3.dp)
                                        .width(2.5.dp)
                                        .height((16 * fontSizeMultiplier).dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = cursorAlpha))
                                )
                            }
                        }
                    }
                    is MarkdownBlock.CodeBlock -> {
                        CodeBlockCard(
                            code = block.code,
                            language = block.language,
                            fontSizeMultiplier = fontSizeMultiplier
                        )
                    }
                    is MarkdownBlock.BulletItem -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp, end = 10.dp)
                                    .size(6.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                            MarkdownStyledText(
                                text = block.text,
                                fontSize = (15 * fontSizeMultiplier).sp,
                                lineHeight = (22 * fontSizeMultiplier).sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isStreaming && isLastBlock) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .width(2.5.dp)
                                        .height((16 * fontSizeMultiplier).dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = cursorAlpha))
                                )
                            }
                        }
                    }
                    is MarkdownBlock.NumberedItem -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "${block.number}.",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = (14 * fontSizeMultiplier).sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(24.dp)
                            )
                            MarkdownStyledText(
                                text = block.text,
                                fontSize = (15 * fontSizeMultiplier).sp,
                                lineHeight = (22 * fontSizeMultiplier).sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isStreaming && isLastBlock) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .width(2.5.dp)
                                        .height((16 * fontSizeMultiplier).dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = cursorAlpha))
                                )
                            }
                        }
                    }
                    is MarkdownBlock.BlockQuote -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(24.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            MarkdownStyledText(
                                text = block.text,
                                fontStyle = FontStyle.Italic,
                                fontSize = (14 * fontSizeMultiplier).sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isStreaming && isLastBlock) {
                                Spacer(modifier = Modifier.width(3.dp))
                                Box(
                                    modifier = Modifier
                                        .width(2.5.dp)
                                        .height((14 * fontSizeMultiplier).dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = cursorAlpha))
                                )
                            }
                        }
                    }
                    is MarkdownBlock.Table -> {
                        MarkdownTableView(
                            headers = block.headers,
                            rows = block.rows,
                            fontSizeMultiplier = fontSizeMultiplier
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownStyledText(
    text: String,
    style: TextStyle = LocalTextStyle.current,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = 15.sp,
    lineHeight: TextUnit = 22.sp,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotatedString = remember(text, linkColor) {
        parseInlineStyles(text, linkColor = linkColor)
    }

    ClickableText(
        text = annotatedString,
        style = style.copy(
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight ?: style.fontWeight,
            fontStyle = fontStyle ?: style.fontStyle
        ),
        modifier = modifier,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    var url = annotation.item.trim()
                    if (url.startsWith("www.", ignoreCase = true)) {
                        url = "https://$url"
                    } else if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                        url = "https://$url"
                    }
                    try {
                        uriHandler.openUri(url)
                    } catch (e: Exception) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (ex: Exception) {
                            Toast.makeText(context, "Could not open link: $url", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        }
    )
}

@Composable
fun CodeBlockCard(
    code: String,
    language: String,
    fontSizeMultiplier: Float
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF0F172A),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column {
            // Header bar with language label and copy button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (language.isNotBlank()) language.uppercase() else "CODE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )

                val copyCodeAction = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Code", code))
                    copied = true
                    Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (copied) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF334155).copy(alpha = 0.6f))
                        .clickable { copyCodeAction() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("copy_code_button")
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copied) Color(0xFF10B981) else Color(0xFFCBD5E1),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (copied) "Copied!" else "Copy",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (copied) Color(0xFF10B981) else Color(0xFFCBD5E1)
                    )
                }
            }

            // Code Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(
                    text = highlightSyntax(code, language),
                    fontFamily = FontFamily.Monospace,
                    fontSize = (13 * fontSizeMultiplier).sp,
                    lineHeight = (18 * fontSizeMultiplier).sp,
                    color = Color(0xFFE2E8F0)
                )
            }
        }
    }
}

@Composable
fun MarkdownTableView(
    headers: List<String>,
    rows: List<List<String>>,
    fontSizeMultiplier: Float
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .horizontalScroll(rememberScrollState())
    ) {
        Column {
            // Header Row
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                headers.forEach { header ->
                    Text(
                        text = header.trim(),
                        fontWeight = FontWeight.Bold,
                        fontSize = (13 * fontSizeMultiplier).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .width(140.dp)
                            .padding(4.dp)
                    )
                }
            }

            // Data Rows
            rows.forEachIndexed { index, row ->
                val bg = if (index % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer
                Row(
                    modifier = Modifier
                        .background(bg)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    row.forEach { cell ->
                        MarkdownStyledText(
                            text = cell.trim(),
                            fontSize = (13 * fontSizeMultiplier).sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .width(140.dp)
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

// --- Parsers and Syntax Highlighter ---

sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class CodeBlock(val code: String, val language: String) : MarkdownBlock()
    data class BulletItem(val text: String) : MarkdownBlock()
    data class NumberedItem(val number: Int, val text: String) : MarkdownBlock()
    data class BlockQuote(val text: String) : MarkdownBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
}

fun parseMarkdownBlocks(content: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = content.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // Code block starts
        if (line.trimStart().startsWith("```")) {
            val language = line.trimStart().removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(codeLines.joinToString("\n"), language))
            i++
            continue
        }

        // Table
        if (line.trim().startsWith("|") && line.trim().endsWith("|") && i + 1 < lines.size && lines[i + 1].contains("---")) {
            val headerCells = line.split("|").filter { it.isNotBlank() }
            i += 2 // skip header and delimiter row
            val tableRows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                val rowCells = lines[i].split("|").filter { it.isNotBlank() }
                tableRows.add(rowCells)
                i++
            }
            blocks.add(MarkdownBlock.Table(headerCells, tableRows))
            continue
        }

        // Heading
        if (line.startsWith("#")) {
            val level = line.takeWhile { it == '#' }.length
            val text = line.drop(level).trim()
            blocks.add(MarkdownBlock.Heading(level, text))
            i++
            continue
        }

        // Blockquote
        if (line.trimStart().startsWith(">")) {
            val text = line.trimStart().removePrefix(">").trim()
            blocks.add(MarkdownBlock.BlockQuote(text))
            i++
            continue
        }

        // Bullet Item
        if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) {
            val text = line.trimStart().drop(2).trim()
            blocks.add(MarkdownBlock.BulletItem(text))
            i++
            continue
        }

        // Numbered Item
        val numberedMatch = Regex("^(\\d+)\\.\\s+(.*)").find(line.trimStart())
        if (numberedMatch != null) {
            val num = numberedMatch.groupValues[1].toIntOrNull() ?: 1
            val text = numberedMatch.groupValues[2]
            blocks.add(MarkdownBlock.NumberedItem(num, text))
            i++
            continue
        }

        // Plain paragraph / text
        if (line.isNotBlank()) {
            val paraLines = mutableListOf(line)
            while (i + 1 < lines.size &&
                lines[i + 1].isNotBlank() &&
                !lines[i + 1].trimStart().startsWith("#") &&
                !lines[i + 1].trimStart().startsWith("```") &&
                !lines[i + 1].trimStart().startsWith("- ") &&
                !lines[i + 1].trimStart().startsWith("* ") &&
                !lines[i + 1].trimStart().startsWith(">") &&
                !Regex("^(\\d+)\\.\\s+").containsMatchIn(lines[i + 1].trimStart())
            ) {
                i++
                paraLines.add(lines[i])
            }
            blocks.add(MarkdownBlock.Paragraph(paraLines.joinToString(" ")))
        }

        i++
    }

    return blocks
}

fun parseInlineStyles(text: String, linkColor: Color = Color(0xFF38BDF8)): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val length = text.length

        while (cursor < length) {
            // Inline code `code`
            if (cursor < length && text[cursor] == '`') {
                val end = text.indexOf('`', cursor + 1)
                if (end != -1) {
                    val codeContent = text.substring(cursor + 1, end)
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            background = Color(0xFF334155).copy(alpha = 0.4f),
                            color = Color(0xFF38BDF8)
                        )
                    )
                    append(" $codeContent ")
                    pop()
                    cursor = end + 1
                    continue
                }
            }

            // Bold **text** or __text__
            if (cursor + 1 < length && (text.substring(cursor, cursor + 2) == "**" || text.substring(cursor, cursor + 2) == "__")) {
                val marker = text.substring(cursor, cursor + 2)
                val end = text.indexOf(marker, cursor + 2)
                if (end != -1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(cursor + 2, end))
                    pop()
                    cursor = end + 2
                    continue
                }
            }

            // Italic *text* or _text_
            if (cursor < length && (text[cursor] == '*' || text[cursor] == '_')) {
                val marker = text[cursor]
                val end = text.indexOf(marker, cursor + 1)
                if (end != -1 && end > cursor + 1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(cursor + 1, end))
                    pop()
                    cursor = end + 1
                    continue
                }
            }

            // Strikethrough ~~text~~
            if (cursor + 1 < length && text.substring(cursor, cursor + 2) == "~~") {
                val end = text.indexOf("~~", cursor + 2)
                if (end != -1) {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(text.substring(cursor + 2, end))
                    pop()
                    cursor = end + 2
                    continue
                }
            }

            // Markdown Link [text](url)
            if (cursor < length && text[cursor] == '[') {
                val endText = text.indexOf(']', cursor + 1)
                if (endText != -1 && endText + 1 < length && text[endText + 1] == '(') {
                    val endUrl = text.indexOf(')', endText + 2)
                    if (endUrl != -1) {
                        val linkText = text.substring(cursor + 1, endText)
                        val url = text.substring(endText + 2, endUrl).trim()
                        
                        val startIdx = length
                        pushStringAnnotation(tag = "URL", annotation = url)
                        pushStyle(
                            SpanStyle(
                                color = linkColor,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                        append(linkText)
                        pop()
                        pop()
                        cursor = endUrl + 1
                        continue
                    }
                }
            }

            // Raw URL Detection (https://, http://, www.)
            val remaining = text.substring(cursor)
            val isHttps = remaining.startsWith("https://", ignoreCase = true)
            val isHttp = remaining.startsWith("http://", ignoreCase = true)
            val isWww = remaining.startsWith("www.", ignoreCase = true)

            if (isHttps || isHttp || isWww) {
                // Find end of URL (whitespace or punctuation at end of word)
                var urlEnd = cursor
                while (urlEnd < length && !text[urlEnd].isWhitespace() && text[urlEnd] !in listOf(')', ']', '>', '"', '\'')) {
                    urlEnd++
                }
                var rawUrl = text.substring(cursor, urlEnd)
                // Trim trailing punctuation like . , : ;
                while (rawUrl.isNotEmpty() && rawUrl.last() in listOf('.', ',', ':', ';', '!', '?')) {
                    rawUrl = rawUrl.dropLast(1)
                    urlEnd--
                }

                if (rawUrl.isNotBlank()) {
                    val targetUrl = if (rawUrl.startsWith("www.", ignoreCase = true)) "https://$rawUrl" else rawUrl
                    pushStringAnnotation(tag = "URL", annotation = targetUrl)
                    pushStyle(
                        SpanStyle(
                            color = linkColor,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                    append(rawUrl)
                    pop()
                    pop()
                    cursor = urlEnd
                    continue
                }
            }

            append(text[cursor])
            cursor++
        }
    }
}

fun highlightSyntax(code: String, language: String): AnnotatedString {
    val keywords = setOf(
        "val", "var", "fun", "class", "interface", "object", "return", "if", "else", "when",
        "for", "while", "import", "package", "override", "private", "public", "protected",
        "const", "let", "function", "async", "await", "def", "from", "import", "export",
        "default", "null", "true", "false", "this", "new", "type", "struct", "void"
    )

    return buildAnnotatedString {
        val lines = code.lines()
        lines.forEachIndexed { index, line ->
            var i = 0
            while (i < line.length) {
                // Comments
                if (i + 1 < line.length && line.substring(i, i + 2) == "//") {
                    pushStyle(SpanStyle(color = Color(0xFF64748B), fontStyle = FontStyle.Italic))
                    append(line.substring(i))
                    pop()
                    break
                }
                // Strings "..."
                if (line[i] == '"' || line[i] == '\'') {
                    val quote = line[i]
                    val end = line.indexOf(quote, i + 1)
                    if (end != -1) {
                        pushStyle(SpanStyle(color = Color(0xFFA5D6A7)))
                        append(line.substring(i, end + 1))
                        pop()
                        i = end + 1
                        continue
                    }
                }
                // Numbers
                if (line[i].isDigit()) {
                    var end = i
                    while (end < line.length && (line[end].isDigit() || line[end] == '.')) {
                        end++
                    }
                    pushStyle(SpanStyle(color = Color(0xFFFFB74D)))
                    append(line.substring(i, end))
                    pop()
                    i = end
                    continue
                }
                // Words (Keywords or identifiers)
                if (line[i].isLetter() || line[i] == '_') {
                    var end = i
                    while (end < line.length && (line[end].isLetterOrDigit() || line[end] == '_')) {
                        end++
                    }
                    val word = line.substring(i, end)
                    if (word in keywords) {
                        pushStyle(SpanStyle(color = Color(0xFFF472B6), fontWeight = FontWeight.SemiBold))
                        append(word)
                        pop()
                    } else {
                        append(word)
                    }
                    i = end
                    continue
                }

                append(line[i])
                i++
            }
            if (index < lines.size - 1) append("\n")
        }
    }
}
