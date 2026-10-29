package com.example.gavanhomes.ui.common

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat

private const val LinkTag = "url"

/** HTML from the website's rich-text editors (bold, italics, underline, strike, headings, lists, links) as a Compose [AnnotatedString]. */
fun htmlToAnnotated(html: String, linkColor: Color = Color(0xFF1E5FAE)): AnnotatedString {
    val spanned: Spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
    val text = spanned.toString().trimEnd()
    return buildAnnotatedString {
        append(text)
        val end = text.length
        for (span in spanned.getSpans(0, spanned.length, Any::class.java)) {
            val s = spanned.getSpanStart(span).coerceIn(0, end)
            val e = spanned.getSpanEnd(span).coerceIn(0, end)
            if (s >= e) continue
            when (span) {
                is StyleSpan -> addStyle(
                    SpanStyle(
                        fontWeight = if (span.style == Typeface.BOLD || span.style == Typeface.BOLD_ITALIC) FontWeight.Bold else null,
                        fontStyle = if (span.style == Typeface.ITALIC || span.style == Typeface.BOLD_ITALIC) FontStyle.Italic else null,
                    ), s, e,
                )
                is UnderlineSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), s, e)
                is StrikethroughSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), s, e)
                is RelativeSizeSpan -> addStyle(SpanStyle(fontSize = (14f * span.sizeChange).sp, fontWeight = if (span.sizeChange > 1.1f) FontWeight.Bold else null), s, e)
                is ForegroundColorSpan -> addStyle(SpanStyle(color = Color(span.foregroundColor)), s, e)
                is URLSpan -> { addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), s, e); addStringAnnotation(LinkTag, span.url, s, e) }
                is BulletSpan -> addStyle(SpanStyle(), s, e)
            }
        }
    }
}

/** Shows rich-text HTML formatted (bold, italics, lists, links); a tap on a link opens it. */
@Composable
fun HtmlText(html: String, modifier: Modifier = Modifier, color: Color = KitInk, fontSize: TextUnit = 14.sp, lineHeight: TextUnit = 21.sp, maxLines: Int = Int.MAX_VALUE) {
    val uri = LocalUriHandler.current
    val text = remember(html) { htmlToAnnotated(html) }
    val style = TextStyle(color = color, fontSize = fontSize, lineHeight = lineHeight)
    if (text.getStringAnnotations(LinkTag, 0, text.length).isEmpty()) Text(text, modifier, style = style, maxLines = maxLines)
    else ClickableText(text, modifier, style = style, maxLines = maxLines) { i -> text.getStringAnnotations(LinkTag, i, i).firstOrNull()?.let { runCatching { uri.openUri(it.item) } } }
}
