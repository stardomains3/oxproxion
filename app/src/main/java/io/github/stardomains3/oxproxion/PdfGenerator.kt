package io.github.stardomains3.oxproxion

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import android.util.Log
import androidx.appcompat.content.res.AppCompatResources
import io.noties.markwon.Markwon
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PdfGenerator(private val context: Context) {

    private val pageMargin = 22f
    private val bubblePadding = 20f
    private val bubbleCornerRadius = 30f
    private val bubbleSpacing = 20f
    private val iconSize = 36f  // Increase to 48f or 72f if you want larger (but still crisp) icons
    private val iconSpacing = 8f
    private val markwon = Markwon.builder(context)
        .build()
    /* private val markwon = Markwon.builder(context)
     .usePlugin(HtmlPlugin.create())
     .usePlugin(object : AbstractMarkwonPlugin() {
         override fun configureTheme(builder: MarkwonTheme.Builder) {
             builder
                 .codeTextColor(Color.LTGRAY)
                 .codeBackgroundColor(Color.DKGRAY)
                 .codeBlockBackgroundColor(Color.DKGRAY)
                 .blockQuoteColor(Color.BLACK)
                 .isLinkUnderlined(true)
         }
     })
     .build()*/

    fun generateStyledChatPdfWithImages(context: Context, messages: List<FlexibleMessage>, modelName: String): String? {
        return generatePdf(messages, modelName)
    }

    fun generateStyledChatPdf(context: Context, chatText: String, modelName: String): String? {
        val messages = parseChatTextToMessages(chatText)
        return generatePdf(messages, modelName)
    }
    fun generateMarkdownPdf(markdown: String): String? {
        try {
            val styledText = markwon.toMarkdown(markdown)
            val margin = 20f
            val page_width = 595
            val paint = TextPaint().apply {
                color = Color.parseColor("#d0d0d0")
                textSize = 28f
            }
            val contentWidth = (page_width - (margin * 2)).toInt()
            val layout = StaticLayout.Builder.obtain(
                styledText, 0, styledText.length, paint, contentWidth
            )
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()
            val page_height = (layout.height + (margin * 2)).toInt()
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(page_width, page_height, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.parseColor("#121314"))
            canvas.save()
            canvas.translate(margin, margin)
            layout.draw(canvas)
            canvas.restore()
            document.finishPage(page)

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "${System.currentTimeMillis()}.pdf")
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri == null) {
                document.close()
                return null
            }
            context.contentResolver.openOutputStream(uri).use { outputStream ->
                if (outputStream != null) {
                    document.writeTo(outputStream)
                }
            }
            document.close()
            return uri.toString()
        } catch (e: IOException) {
            Log.e("PdfGenerator", "Error creating Markdown PDF", e)
            return null
        }
    }
    private fun parseChatTextToMessages(chatText: String): List<FlexibleMessage> {
        val messages = mutableListOf<FlexibleMessage>()
        val parts = chatText.split(Regex("(?=(User:|AI:))")).filter { it.isNotBlank() }
        for (part in parts) {
            val role = if (part.startsWith("User:")) "user" else "assistant"
            val content = part.substringAfter(":").trim()
            messages.add(FlexibleMessage(role, JsonPrimitive(content)))
        }
        return messages
    }

    private fun generatePdf(messages: List<FlexibleMessage>, modelName: String): String? {
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "chat_${System.currentTimeMillis()}.pdf"
        )
        //val userIconDrawable: Drawable? = context.getDrawable(R.drawable.ic_person)
        //val aiIconDrawable: Drawable? = context.getDrawable(R.drawable.ic_tune)
        val userIconDrawable: Drawable? = AppCompatResources.getDrawable(context,R.drawable.ic_person)
        val aiIconDrawable: Drawable? = AppCompatResources.getDrawable(context,R.drawable.ic_tune)

        // First, calculate the total height required for the PDF
        var totalHeight = pageMargin // Start with top margin
        val pageWidth = PageSize.A4.width().toFloat()

        // Add title height
        val titlePaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 28f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleHeight = titlePaint.fontSpacing
        totalHeight += titleHeight + bubbleSpacing

        val messagesToRender = messages.filterNot { it.content is JsonPrimitive && (it.content as JsonPrimitive).content == "thinking..." }

        messagesToRender.forEachIndexed { index, message ->
            val isUser = message.role == "user"
            var textContent = ""
            var imageBitmap: Bitmap? = null
            if (message.content is JsonArray) {
                val contentArray = message.content as JsonArray
                textContent = contentArray.firstNotNullOfOrNull { item ->
                    (item as? JsonObject)?.takeIf { it["type"]?.jsonPrimitive?.content == "text" }?.get("text")?.jsonPrimitive?.content
                } ?: ""
                val imageUrl = contentArray.firstNotNullOfOrNull { item ->
                    (item as? JsonObject)?.takeIf { it["type"]?.jsonPrimitive?.content == "image_url" }?.get("image_url")?.jsonObject?.get("url")?.jsonPrimitive?.content
                }
                if (imageUrl != null) {
                    imageBitmap = decodeImage(imageUrl)
                }
            } else if (message.content is JsonPrimitive) {
                textContent = (message.content as JsonPrimitive).content
            }
            totalHeight += calculateTotalMessageHeight(textContent, imageBitmap, pageWidth, if (isUser) userIconDrawable != null else aiIconDrawable != null)
            if (index < messagesToRender.size - 1) {
                totalHeight += bubbleSpacing // Add spacing between messages
            }
        }
        totalHeight += pageMargin + bubbleSpacing // Add bottom margin and a small buffer

        try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(PageSize.A4.width().toInt(), totalHeight.toInt(), 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            // Set background color
            canvas.drawColor(Color.parseColor("#000000"))

            // Draw title
            val titleX = canvas.width / 2f
            var currentY = pageMargin + titlePaint.fontMetrics.top.let { -it }
            canvas.drawText(modelName, titleX, currentY, titlePaint)
            currentY += titlePaint.fontSpacing + bubbleSpacing

            messagesToRender.forEach { message ->
                val isUser = message.role == "user"
                var textContent = ""
                var imageBitmap: Bitmap? = null

                // Extract text and image
                if (message.content is JsonArray) {
                    val contentArray = message.content as JsonArray
                    textContent = contentArray.firstNotNullOfOrNull { item ->
                        (item as? JsonObject)?.takeIf { it["type"]?.jsonPrimitive?.content == "text" }?.get("text")?.jsonPrimitive?.content
                    } ?: ""
                    val imageUrl = contentArray.firstNotNullOfOrNull { item ->
                        (item as? JsonObject)?.takeIf { it["type"]?.jsonPrimitive?.content == "image_url" }?.get("image_url")?.jsonObject?.get("url")?.jsonPrimitive?.content
                    }
                    if (imageUrl != null) {
                        imageBitmap = decodeImage(imageUrl)
                    }
                } else if (message.content is JsonPrimitive) {
                    textContent = (message.content as JsonPrimitive).content
                }

                // Draw the icon and bubble
                val messageHeight = calculateTotalMessageHeight(textContent, imageBitmap, canvas.width.toFloat(), if (isUser) userIconDrawable != null else aiIconDrawable != null)
                drawMessage(canvas, textContent, imageBitmap, isUser, currentY, if (isUser) userIconDrawable else aiIconDrawable)
                currentY += messageHeight + bubbleSpacing
            }

            document.finishPage(page)
            FileOutputStream(file).use {
                document.writeTo(it)
            }
            document.close()
            return file.absolutePath

        } catch (e: IOException) {
            Log.e("PdfGenerator", "Error creating PDF", e)
            return null
        }
    }

    private fun calculateTotalMessageHeight(text: String, image: Bitmap?, pageWidth: Float, hasIcon: Boolean): Float {
        var totalHeight = 0f
        if (hasIcon) {
            totalHeight += iconSize + iconSpacing
        }
        totalHeight += calculateBubbleHeight(text, image, pageWidth)
        return totalHeight
    }

    private fun calculateBubbleHeight(text: String, image: Bitmap?, pageWidth: Float): Float {
        var height = 0f
        val textPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 26f
        }

        val availableWidth = pageWidth - (pageMargin * 2)
        val bubbleWidth = availableWidth * 0.96f
        val bubbleContentWidth = bubbleWidth - (bubblePadding * 2)

        if (text.isNotBlank()) {
            // Parse markdown for height calculation
            val spannedText: Spanned = markwon.toMarkdown(text)
            val staticLayout = StaticLayout.Builder.obtain(spannedText, 0, spannedText.length, textPaint, bubbleContentWidth.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
            height += staticLayout.height
        }
        if (image != null) {
            val aspectRatio = image.height.toFloat() / image.width.toFloat()
            val scaledWidth = if (image.width > bubbleContentWidth) bubbleContentWidth else image.width.toFloat()
            height += scaledWidth * aspectRatio
            if (text.isNotBlank()) height += bubbleSpacing // spacing between text and image
        }

        return height + (bubblePadding * 2)
    }

    private fun drawMessage(canvas: Canvas, text: String, image: Bitmap?, isUser: Boolean, startY: Float, iconDrawable: Drawable?) {
        var currentY = startY

        // Draw icon (as vector)
        iconDrawable?.let {
            val iconX = if (isUser) canvas.width - pageMargin - iconSize else pageMargin
            it.setBounds(iconX.toInt(), currentY.toInt(), (iconX + iconSize).toInt(), (currentY + iconSize).toInt())
            // Optional: it.setTint(Color.parseColor("#e3e3e3")) // If you need to override the color
            it.draw(canvas)
            currentY += iconSize + iconSpacing
        }

        // Draw bubble
        drawBubble(canvas, text, image, isUser, currentY)
    }

    private fun drawBubble(canvas: Canvas, text: String, image: Bitmap?, isUser: Boolean, startY: Float) {
        val bubblePaint = Paint().apply {
            color = if (isUser) Color.parseColor("#36454F") else Color.parseColor("#2C2C2C")
            isAntiAlias = true
        }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f
        }

        val availableWidth = canvas.width - (pageMargin * 2)
        val bubbleWidth = availableWidth * 0.96f // Bubbles take up 96% of available width
        val bubbleContentWidth = bubbleWidth - (bubblePadding * 2)

        var textLayout: StaticLayout? = null
        if (text.isNotBlank()) {
            // Parse markdown text to Spanned
            val spannedText: Spanned = markwon.toMarkdown(text)

            textLayout = StaticLayout.Builder.obtain(spannedText, 0, spannedText.length, textPaint, bubbleContentWidth.toInt())
                .setAlignment(if (isUser) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL)
                .build()
        }

        var imagePartHeight = 0f
        var scaledWidth = 0f
        if (image != null) {
            val aspectRatio = image.height.toFloat() / image.width.toFloat()
            scaledWidth = if (image.width > bubbleContentWidth) bubbleContentWidth else image.width.toFloat()
            imagePartHeight = scaledWidth * aspectRatio
        }

        val textPartHeight = textLayout?.height?.toFloat() ?: 0f
        val bubbleHeight = textPartHeight + imagePartHeight + (bubblePadding * 2) + if (text.isNotBlank() && image != null) bubbleSpacing else 0f

        val bubbleLeft = if (isUser) canvas.width - pageMargin - bubbleWidth else pageMargin
        val bubbleRect = RectF(bubbleLeft, startY, bubbleLeft + bubbleWidth, startY + bubbleHeight)

        // Save canvas state and clip to bubble bounds to prevent overflow
        canvas.save()
        canvas.clipRect(bubbleRect)

        // Draw bubble background
        canvas.drawRoundRect(bubbleRect, bubbleCornerRadius, bubbleCornerRadius, bubblePaint)

        var currentContentY = startY + bubblePadding

        // Draw image
        if (image != null) {
            val imageLeft = bubbleLeft + bubblePadding
            val dstRect = RectF(imageLeft, currentContentY, imageLeft + scaledWidth, currentContentY + imagePartHeight)
            canvas.drawBitmap(image, null, dstRect, null)
            currentContentY += imagePartHeight + if (text.isNotBlank()) bubbleSpacing else 0f
        }

        // Draw text with markdown formatting
        if (textLayout != null) {
            canvas.save()
            val textX = bubbleLeft + bubblePadding
            canvas.translate(textX, currentContentY)
            textLayout.draw(canvas)
            canvas.restore()
        }

        // Restore canvas state
        canvas.restore()
    }

    private fun decodeImage(imageUrl: String): Bitmap? {
        return try {
            val base64Data = imageUrl.substringAfter("base64,")
            val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e("PdfGenerator", "Failed to decode image for PDF", e)
            null
        }
    }

    // A simple PageSize class to hold dimensions, similar to iText's
    object PageSize {
        val A4 = Rect(0, 0, 595, 842)
    }
}
