package io.github.stardomains3.oxproxion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AlignmentSpan
import android.text.style.ReplacementSpan
import android.widget.TextView
import androidx.annotation.ColorInt
import io.noties.markwon.Markwon

import ru.noties.jlatexmath.JLatexMathDrawable


import kotlin.math.roundToInt
import androidx.core.graphics.withTranslation

object LatexPreprocessor {
    private val bareMathEnvironments = setOf(
        "matrix",
        "pmatrix",
        "bmatrix",
        "Bmatrix",
        "vmatrix",
        "Vmatrix",
        "smallmatrix",
        "array",
        "aligned",
        "gathered",
        "cases",
        "split"
    )

    private const val TOKEN_START = '\uE000'
    private const val TOKEN_END = '\uE001'

    data class Style(
        val textSizePx: Float,
        @ColorInt val textColor: Int,
        val maxWidthPx: Int,
        val density: Float
    )

    data class Prepared(
        val markdown: String,
        internal val items: List<Item>
    )

    data class Item(
        val token: String,
        val latex: String,
        val raw: String,
        val block: Boolean
    )

    private data class Fence(
        val char: Char,
        val length: Int
    )

    fun styleFor(textView: TextView): Style {
        val resources = textView.resources
        val density = resources.displayMetrics.density

        val currentAvailable =
            textView.width - textView.paddingLeft - textView.paddingRight

        // During the first bind, TextView.width is commonly still zero.
        val fallbackWidth =
            resources.displayMetrics.widthPixels - (48f * density).toInt()

        return Style(
            textSizePx = textView.textSize,
            textColor = textView.currentTextColor,
            maxWidthPx = (if (currentAvailable > 0) currentAvailable else fallbackWidth)
                .coerceAtLeast(1),
            density = density
        )
    }

    fun render(
        markwon: Markwon,
        source: String,
        textView: TextView
    ): Spanned {
        val prepared = prepare(source)
        val parsed = markwon.toMarkdown(prepared.markdown)
        return decorate(parsed, prepared, styleFor(textView))
    }

    /**
     * Replaces math with private-use placeholders before Markwon parses the
     * Markdown. Code fences and inline code are copied without modification.
     */
    fun prepare(source: String): Prepared {
        if (source.isEmpty() ||
            (!source.contains('$') &&
                    !source.contains("\\(") &&
                    !source.contains("\\[") &&
                    !source.contains("\\begin{"))
        ) {
            return Prepared(source, emptyList())
        }


        val out = StringBuilder(source.length)
        val items = ArrayList<Item>()

        var i = 0
        while (i < source.length) {

            // Fenced code block: ``` or ~~~
            if (isLineStart(source, i)) {
                val fence = fenceAt(source, i)
                if (fence != null) {
                    val end = findFenceEnd(source, i, fence)
                    out.append(source, i, end)
                    i = end
                    continue
                }
            }

            when (source[i]) {
                '`' -> {
                    // Inline code: copy through the matching backtick run.
                    val runLength = runLength(source, i, '`')
                    val close = findBacktickRun(
                        source = source,
                        from = i + runLength,
                        requiredLength = runLength
                    )

                    if (close < 0) {
                        out.append(source, i, source.length)
                        i = source.length
                    } else {
                        val end = close + runLength
                        out.append(source, i, end)
                        i = end
                    }
                }

                '\\' -> {
                    // NEW: Handle bare \begin{matrix} ... \end{matrix}, etc.
                    if (source.startsWith("\\begin{", i)) {
                        val environmentEnd = source.indexOf('}', i + 7)

                        if (environmentEnd > i + 7) {
                            val environment =
                                source.substring(i + 7, environmentEnd)

                            if (environment in bareMathEnvironments) {
                                val endMarker =
                                    "\\end{" + environment + "}"

                                val endStart = source.indexOf(
                                    endMarker,
                                    environmentEnd + 1
                                )

                                if (endStart >= 0) {
                                    val end = endStart + endMarker.length
                                    val raw = source.substring(i, end)

                                    appendMath(
                                        out = out,
                                        items = items,
                                        latex = raw,
                                        raw = raw,
                                        block = true
                                    )

                                    i = end
                                    continue
                                }
                            }
                        }
                    }
                    if (i + 1 < source.length &&
                        (source[i + 1] == '(' || source[i + 1] == '[')
                    ) {
                        val block = source[i + 1] == '['
                        val closing = if (block) "\\]" else "\\)"
                        val close = findUnescaped(source, i + 2, closing)

                        if (close >= 0) {
                            val latex = source.substring(i + 2, close)
                            if (latex.isNotBlank()) {
                                val raw = source.substring(i, close + 2)
                                appendMath(out, items, latex, raw, block)
                                i = close + 2
                                continue
                            }
                        }
                    }

                    // Copy escaped characters verbatim. In particular, \$ must
                    // not subsequently be interpreted as inline math.
                    out.append(source[i])
                    if (i + 1 < source.length) {
                        out.append(source[i + 1])
                        i += 2
                    } else {
                        i++
                    }
                }

                '$' -> {
                    if (i + 1 < source.length && source[i + 1] == '$') {
                        val close = findDoubleDollarEnd(source, i + 2)

                        if (close > i + 2) {
                            val latex = source.substring(i + 2, close)
                            if (latex.isNotBlank()) {
                                val raw = source.substring(i, close + 2)
                                appendMath(
                                    out = out,
                                    items = items,
                                    latex = latex,
                                    raw = raw,
                                    block = true
                                )
                                i = close + 2
                                continue
                            }
                        }
                    } else {
                        val close = findInlineDollarEnd(source, i + 1)

                        if (close > i + 1) {
                            val latex = source.substring(i + 1, close)

                            val valid =
                                latex.isNotBlank() &&
                                        !latex.first().isWhitespace() &&
                                        !latex.last().isWhitespace() &&
                                        // Helps avoid treating currency as math.
                                        (close + 1 >= source.length ||
                                                !source[close + 1].isDigit())

                            if (valid) {
                                val raw = source.substring(i, close + 1)
                                appendMath(
                                    out = out,
                                    items = items,
                                    latex = latex,
                                    raw = raw,
                                    block = false
                                )
                                i = close + 1
                                continue
                            }
                        }
                    }

                    out.append('$')
                    i++
                }

                else -> {
                    out.append(source[i])
                    i++
                }
            }
        }

        return Prepared(out.toString(), items)
    }

    private fun decorate(
        rendered: Spanned,
        prepared: Prepared,
        style: Style
    ): Spanned {
        if (prepared.items.isEmpty()) return rendered

        val builder = SpannableStringBuilder(rendered)

        for (item in prepared.items) {
            val start = builder.indexOf(item.token)
            if (start < 0) continue

            val end = start + item.token.length

            try {
                val padding = if (item.block) {
                    (4f * style.density).roundToInt()
                } else {
                    0
                }

                val drawable = JLatexMathDrawable.builder(
                    normalizeLatex(item.latex.trim())
                )

                    .textSize(
                        style.textSizePx * if (item.block) 1.08f else 1f
                    )
                    // This is the important color fix.
                    .color(style.textColor)
                    // Do not put a translucent black rectangle behind the math.
                    .background(Color.TRANSPARENT)
                    .padding(padding)
                    .build()

                builder.setSpan(
                    LatexSpan(drawable, style.maxWidthPx),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                if (item.block) {
                    builder.setSpan(
                        AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            } catch (_: Exception) {
                // A malformed expression should not blank or crash the message.
                builder.replace(start, end, item.raw)
            }
        }

        return builder
    }

    private fun appendMath(
        out: StringBuilder,
        items: MutableList<Item>,
        latex: String,
        raw: String,
        block: Boolean
    ) {
        val token = buildString {
            append(TOKEN_START)
            append(items.size.toString(36))
            append(TOKEN_END)
        }


        items += Item(
            token = token,
            latex = latex,
            raw = raw,
            block = block
        )

        if (block) {
            if (out.isNotEmpty() && out.last() != '\n') {
                out.append('\n')
            }
            out.append(token)
            out.append('\n')
        } else {
            out.append(token)
        }
    }

    private class LatexSpan(
        private val drawable: Drawable,
        maxWidthPx: Int
    ) : ReplacementSpan() {

        private val naturalWidth =
            drawable.intrinsicWidth.coerceAtLeast(1)

        private val naturalHeight =
            drawable.intrinsicHeight.coerceAtLeast(1)

        private val scale =
            if (maxWidthPx > 0 && naturalWidth > maxWidthPx) {
                maxWidthPx.toFloat() / naturalWidth.toFloat()
            } else {
                1f
            }

        private val scaledWidth =
            (naturalWidth * scale).roundToInt().coerceAtLeast(1)

        private val scaledHeight =
            (naturalHeight * scale).roundToInt().coerceAtLeast(1)

        init {
            drawable.setBounds(0, 0, naturalWidth, naturalHeight)
        }

        override fun getSize(
            paint: Paint,
            text: CharSequence?,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            if (fm != null) {
                val descent = paint.descent().coerceAtLeast(0f).roundToInt()
                fm.ascent = -scaledHeight + descent
                fm.descent = descent
                fm.top = fm.ascent
                fm.bottom = fm.descent
            }
            return scaledWidth
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            val descent = paint.descent().coerceAtLeast(0f)

            canvas.withTranslation(
                x,
                y - scaledHeight + descent
            ) {
                scale(scale, scale)
                drawable.draw(this)
            }
        }
    }

    private fun isLineStart(source: String, index: Int): Boolean =
        index == 0 || source[index - 1] == '\n'

    private fun runLength(source: String, start: Int, char: Char): Int {
        var i = start
        while (i < source.length && source[i] == char) i++
        return i - start
    }

    private fun fenceAt(source: String, lineStart: Int): Fence? {
        var p = lineStart
        var spaces = 0

        while (p < source.length && source[p] == ' ' && spaces < 4) {
            p++
            spaces++
        }

        if (spaces > 3 || p >= source.length) return null

        val char = source[p]
        if (char != '`' && char != '~') return null

        val length = runLength(source, p, char)
        return if (length >= 3) Fence(char, length) else null
    }

    private fun findFenceEnd(
        source: String,
        openingStart: Int,
        openingFence: Fence
    ): Int {
        val openingNewline = source.indexOf('\n', openingStart)
        if (openingNewline < 0) return source.length

        var lineStart = openingNewline + 1

        while (lineStart < source.length) {
            val newline = source.indexOf('\n', lineStart)
            val lineEnd = if (newline < 0) source.length else newline

            if (isFenceClose(
                    source,
                    lineStart,
                    lineEnd,
                    openingFence
                )
            ) {
                return if (newline < 0) lineEnd else lineEnd + 1
            }

            if (newline < 0) break
            lineStart = newline + 1
        }

        return source.length
    }

    private fun isFenceClose(
        source: String,
        lineStart: Int,
        lineEnd: Int,
        openingFence: Fence
    ): Boolean {
        var p = lineStart
        var spaces = 0

        while (p < lineEnd && source[p] == ' ' && spaces < 4) {
            p++
            spaces++
        }

        if (spaces > 3 || p >= lineEnd) return false
        if (source[p] != openingFence.char) return false

        val length = runLength(source, p, openingFence.char)
        if (length < openingFence.length) return false

        p += length
        while (p < lineEnd &&
            (source[p] == ' ' || source[p] == '\t')
        ) {
            p++
        }

        return p == lineEnd
    }

    private fun findBacktickRun(
        source: String,
        from: Int,
        requiredLength: Int
    ): Int {
        var i = from

        while (i < source.length) {
            if (source[i] == '`') {
                val length = runLength(source, i, '`')
                if (length == requiredLength) return i
                i += length
            } else {
                i++
            }
        }

        return -1
    }

    private fun findUnescaped(
        source: String,
        from: Int,
        needle: String
    ): Int {
        var i = from

        while (i <= source.length - needle.length) {
            if (source.startsWith(needle, i) &&
                !isEscaped(source, i)
            ) {
                return i
            }

            i += if (source[i] == '\\') 2 else 1
        }

        return -1
    }

    private fun findDoubleDollarEnd(source: String, from: Int): Int {
        var i = from

        while (i + 1 < source.length) {
            if (source[i] == '$' &&
                source[i + 1] == '$' &&
                !isEscaped(source, i)
            ) {
                return i
            }

            i += if (source[i] == '\\') 2 else 1
        }

        return -1
    }

    private fun findInlineDollarEnd(source: String, from: Int): Int {
        var i = from

        while (i < source.length) {
            when (source[i]) {
                '\n' -> return -1

                '\\' -> i += 2

                '$' -> {
                    return if (i + 1 < source.length &&
                        source[i + 1] == '$'
                    ) {
                        -1
                    } else {
                        i
                    }
                }

                else -> i++
            }
        }

        return -1
    }
    private fun normalizeLatex(source: String): String {
        return source
            // JLaTeXMath does not support esint's closed-surface integral.
            // Fallback: retain the surface integral, but not the closed-loop marker.
            .replace("\\oiint", "\\iint")
            .replace(
                "\\begin{pmatrix}",
                "\\left(\\begin{matrix}"
            )
            .replace(
                "\\end{pmatrix}",
                "\\end{matrix}\\right)"
            )
            .replace(
                "\\begin{bmatrix}",
                "\\left[\\begin{matrix}"
            )
            .replace(
                "\\end{bmatrix}",
                "\\end{matrix}\\right]"
            )
    }

    private fun isEscaped(source: String, index: Int): Boolean {
        var count = 0
        var i = index - 1

        while (i >= 0 && source[i] == '\\') {
            count++
            i--
        }

        return count % 2 == 1
    }
}
