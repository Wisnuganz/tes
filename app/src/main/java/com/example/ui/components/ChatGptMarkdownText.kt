package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AiEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletItem(val text: String) : MarkdownBlock
    data class NumberedItem(val number: String, val text: String) : MarkdownBlock
    data class QuoteCallout(val text: String) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data object Divider : MarkdownBlock
}

@Composable
fun ChatGptMarkdownText(
    markdown: String,
    textColor: Color,
    accentColor: Color = AiEmerald,
    secondaryColor: Color = textColor.copy(alpha = 0.75f),
    codeBackgroundColor: Color = Color(0xFF0D1117),
    inlineCodeBackgroundColor: Color = accentColor.copy(alpha = 0.16f),
    calloutBackgroundColor: Color = accentColor.copy(alpha = 0.12f),
    fontSize: TextUnit = 13.5.sp,
    lineHeight: TextUnit = 20.sp,
    modifier: Modifier = Modifier
) {
    val cleaned = remember(markdown) { normalizeReadableMathSymbols(markdown) }
    val blocks = remember(cleaned) { parseMarkdownBlocks(cleaned) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val headingSize = when (block.level) {
                        1 -> 15.5.sp
                        2 -> 14.5.sp
                        else -> 13.8.sp
                    }
                    Text(
                        text = parseInlineMarkdown(
                            text = block.text,
                            baseColor = textColor,
                            accentColor = accentColor,
                            inlineCodeBg = inlineCodeBackgroundColor
                        ),
                        fontSize = headingSize,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        lineHeight = (headingSize.value + 5f).sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                is MarkdownBlock.QuoteCallout -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = calloutBackgroundColor,
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .background(accentColor)
                            )
                            Text(
                                text = parseInlineMarkdown(
                                    text = block.text,
                                    baseColor = textColor,
                                    accentColor = accentColor,
                                    inlineCodeBg = inlineCodeBackgroundColor
                                ),
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                color = textColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                is MarkdownBlock.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "•",
                            color = accentColor,
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            lineHeight = lineHeight
                        )
                        Text(
                            text = parseInlineMarkdown(
                                text = block.text,
                                baseColor = textColor,
                                accentColor = accentColor,
                                inlineCodeBg = inlineCodeBackgroundColor
                            ),
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.NumberedItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 2.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            text = "${block.number}.",
                            color = accentColor,
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            lineHeight = lineHeight
                        )
                        Text(
                            text = parseInlineMarkdown(
                                text = block.text,
                                baseColor = textColor,
                                accentColor = accentColor,
                                inlineCodeBg = inlineCodeBackgroundColor
                            ),
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.CodeBlock -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = codeBackgroundColor,
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            if (block.language.isNotBlank()) {
                                Text(
                                    text = block.language.uppercase(),
                                    color = secondaryColor,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = block.code,
                                color = Color(0xFFE6EDF3),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            )
                        }
                    }
                }

                MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        color = textColor.copy(alpha = 0.14f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(
                            text = block.text,
                            baseColor = textColor,
                            accentColor = accentColor,
                            inlineCodeBg = inlineCodeBackgroundColor
                        ),
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        color = textColor
                    )
                }
            }
        }
    }
}

internal fun normalizeReadableMathSymbols(raw: String): String {
    var out = raw.replace("\r\n", "\n")
    // Convert common LaTeX markers into clean readable unicode/text
    out = out.replace("$$", "")
    out = out.replace(Regex("\\\\frac\\{([^{}]+)\\}\\{([^{}]+)\\}"), "($1/$2)")
    out = out.replace(Regex("\\\\sqrt\\{([^{}]+)\\}"), "√($1)")
    out = out.replace("\\times", "×")
    out = out.replace("\\cdot", "·")
    out = out.replace("\\div", "÷")
    out = out.replace("\\pm", "±")
    out = out.replace("\\leq", "≤")
    out = out.replace("\\le", "≤")
    out = out.replace("\\geq", "≥")
    out = out.replace("\\ge", "≥")
    out = out.replace("\\neq", "≠")
    out = out.replace("\\approx", "≈")
    out = out.replace("\\rightarrow", "→")
    out = out.replace("\\Rightarrow", "⇒")
    out = out.replace("\\degree", "°")
    out = out.replace("^{\\circ}", "°")
    out = out.replace("^2", "²")
    out = out.replace("^3", "³")
    // Remove stray single-dollar math wrappers around short expressions like $x = 5$
    out = out.replace(Regex("\\$([^$\\n]{1,60})\\$"), "$1")
    return out
}

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val lines = text.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraphBuffer = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraphBuffer.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(paragraphBuffer.joinToString("\n")))
            paragraphBuffer.clear()
        }
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            flushParagraph()
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            i++
            continue
        }

        if (trimmed.isEmpty()) {
            flushParagraph()
            i++
            continue
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            flushParagraph()
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }

        val headingMatch = Regex("^(#{1,4})\\s+(.+)$").find(trimmed)
        if (headingMatch != null) {
            flushParagraph()
            blocks.add(
                MarkdownBlock.Heading(
                    level = headingMatch.groupValues[1].length,
                    text = headingMatch.groupValues[2].trim()
                )
            )
            i++
            continue
        }

        if (trimmed.startsWith(">")) {
            flushParagraph()
            val quoteLines = mutableListOf(trimmed.removePrefix(">").trim())
            while (i + 1 < lines.size && lines[i + 1].trim().startsWith(">")) {
                i++
                quoteLines.add(lines[i].trim().removePrefix(">").trim())
            }
            blocks.add(MarkdownBlock.QuoteCallout(quoteLines.joinToString("\n")))
            i++
            continue
        }

        val bulletMatch = Regex("^[-*•]\\s+(.+)$").find(trimmed)
        if (bulletMatch != null) {
            flushParagraph()
            blocks.add(MarkdownBlock.BulletItem(bulletMatch.groupValues[1]))
            i++
            continue
        }

        val numberedMatch = Regex("^(\\d{1,3})[.)]\\s+(.+)$").find(trimmed)
        if (numberedMatch != null) {
            flushParagraph()
            blocks.add(
                MarkdownBlock.NumberedItem(
                    number = numberedMatch.groupValues[1],
                    text = numberedMatch.groupValues[2]
                )
            )
            i++
            continue
        }

        paragraphBuffer.add(line.trimEnd())
        i++
    }
    flushParagraph()
    return blocks
}

internal fun parseInlineMarkdown(
    text: String,
    baseColor: Color,
    accentColor: Color,
    inlineCodeBg: Color
): AnnotatedString {
    return buildAnnotatedString {
        var idx = 0
        while (idx < text.length) {
            // Bold: **text** or __text__
            if (text.startsWith("**", idx) || text.startsWith("__", idx)) {
                val marker = text.substring(idx, idx + 2)
                val endIdx = text.indexOf(marker, idx + 2)
                if (endIdx > idx + 2) {
                    val inner = text.substring(idx + 2, endIdx)
                    val boldColor = if (
                        inner.startsWith("Jawaban", ignoreCase = true) ||
                        inner.startsWith("Kesimpulan", ignoreCase = true)
                    ) {
                        accentColor
                    } else {
                        baseColor
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) {
                        append(inner)
                    }
                    idx = endIdx + 2
                    continue
                }
            }

            // Inline code: `code`
            if (text[idx] == '`') {
                val endIdx = text.indexOf('`', idx + 1)
                if (endIdx > idx + 1) {
                    val inner = text.substring(idx + 1, endIdx)
                    withStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            background = inlineCodeBg,
                            color = accentColor
                        )
                    ) {
                        append(" $inner ")
                    }
                    idx = endIdx + 1
                    continue
                }
            }

            // Italic: *text* (only when matching closing * exists on same line without leading space)
            if (text[idx] == '*' && idx + 1 < text.length && text[idx + 1] != ' ' && text[idx + 1] != '*') {
                val endIdx = text.indexOf('*', idx + 1)
                if (endIdx > idx + 1 && text.substring(idx + 1, endIdx).contains('\n').not()) {
                    val inner = text.substring(idx + 1, endIdx)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(inner)
                    }
                    idx = endIdx + 1
                    continue
                }
            }

            append(text[idx])
            idx++
        }
    }
}
