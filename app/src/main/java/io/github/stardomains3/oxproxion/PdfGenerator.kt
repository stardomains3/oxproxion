package io.github.stardomains3.oxproxion

import android.app.Activity
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
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withClip
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tables.TableTheme
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class PdfGenerator(private val context: Context) {
    private data class MdBlock(val isTable: Boolean, val content: String)
    private val pageMargin = 22f
    private val bubblePadding = 20f
    private val bubbleCornerRadius = 30f
    private val bubbleSpacing = 20f
    private val iconSize = 36f  // Increase to 48f or 72f if you want larger (but still crisp) icons
    private val iconSpacing = 8f
    //private val markwon = Markwon.builder(context)
    //  .build()

    val prism4j = Prism4j(ExampleGrammarLocator())
    // val theme = Prism4jThemeDefault.create()
    val theme = Prism4jThemeDarkula.create()
    val syntaxHighlightPlugin = SyntaxHighlightPlugin.create(prism4j, theme)
    val customTheme = TableTheme.buildWithDefaults(context) // Or TableTheme.buildWithDefaults(context) if you want default values first
        .tableBorderColor(Color.LTGRAY)
        .tableBorderWidth(2)
        .tableCellPadding(1)

        .tableHeaderRowBackgroundColor("#121314".toColorInt())

        //.borderWidth(2f)
        .build()
    private val markwon = Markwon.builder(context)
        .usePlugin(HtmlPlugin.create())
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(syntaxHighlightPlugin)
        // .usePlugin(TablePlugin.create(context))
        .usePlugin(TablePlugin.create(customTheme))

        .usePlugin(TaskListPlugin.create(context))    // <-- Add Task List plugin
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                builder
                    .codeTextColor(Color.LTGRAY)
                    .codeBackgroundColor(Color.BLACK)
                    .codeBlockBackgroundColor(Color.BLACK)
                    //.blockQuoteColor(Color.BLACK)
                    .isLinkUnderlined(true)

            }
        })
        .build()
    fun generateMarkdownPdfWithImage(markdown: String, imageUri: String): String? {
        try {
            val processedMarkdown = processMarkdownLinks(markdown)
            val margin = 20f
            val pageWidth = 595
            val contentWidth = (pageWidth - (margin * 2)).toInt()

            // Split markdown into blocks for proper rendering
            val blocks = splitMarkdownIntoBlocks(processedMarkdown)
            val renderedTables = hashMapOf<Int, Bitmap>()

            // Pre-render table bitmaps (same as original)
            blocks.forEachIndexed { index, block ->
                if (block.isTable) {
                    val act = context as? Activity
                        ?: throw IllegalStateException("PdfGenerator requires an Activity context to render tables")
                    val bmp = renderMarkdownToBitmapAttachedBlocking(
                        act,
                        block.content,
                        contentWidth,
                        "#d0d0d0".toColorInt(),
                        26f
                    )
                    renderedTables[index] = bmp
                }
            }

            // Load the generated image from URI
            val imageBitmap = loadBitmapFromUri(context, imageUri)
            val imageHeight = imageBitmap?.let { (it.height.toFloat() / it.width.toFloat()) * contentWidth } ?: 0f

            // Calculate total height needed (image first, then text + spacing)
            var totalHeight = margin
            val textPaint = TextPaint().apply {
                color = "#d0d0d0".toColorInt()
                textSize = 26f
            }

            // Add image height first
            if (imageBitmap != null) {
                totalHeight += imageHeight
            }

            // Calculate text height and add spacing if both image and text exist
            var textHeight = 0f
            blocks.forEachIndexed { index, block ->
                if (block.isTable) {
                    textHeight += renderedTables[index]!!.height
                } else {
                    if (block.content.isNotBlank()) {
                        val spanned = markwon.toMarkdown(block.content)
                        val layout = StaticLayout.Builder.obtain(
                            spanned, 0, spanned.length, textPaint, contentWidth
                        )
                            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                            .setLineSpacing(0f, 1f)
                            .setIncludePad(false)
                            .build()
                        textHeight += layout.height
                    }
                }
                if (index < blocks.size - 1) textHeight += 8f // spacing between blocks
            }
            if (imageBitmap != null && textHeight > 0) {
                totalHeight += 20f // Padding between image and text
            }
            totalHeight += textHeight + margin

            // Create PDF with calculated height
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, totalHeight.toInt(), 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor("#121314".toColorInt())

            // Render content: Image first, then text
            var currentY = margin

            // Render the image (if present)
            if (imageBitmap != null) {
                val scaledBitmap = imageBitmap.scale(contentWidth, imageHeight.toInt())
                canvas.drawBitmap(scaledBitmap, margin, currentY, null)
                currentY += imageHeight
                if (textHeight > 0) {
                    currentY += 20f // Padding between image and text
                }
            }

            // Render text blocks
            blocks.forEachIndexed { index, block ->
                if (block.isTable) {
                    val bmp = renderedTables[index]!!
                    canvas.drawBitmap(bmp, margin, currentY, null)
                    currentY += bmp.height
                } else {
                    if (block.content.isNotBlank()) {
                        val spanned = markwon.toMarkdown(block.content)
                        val layout = StaticLayout.Builder.obtain(
                            spanned, 0, spanned.length, textPaint, contentWidth
                        )
                            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                            .setLineSpacing(0f, 1f)
                            .setIncludePad(false)
                            .build()
                        canvas.withTranslation(margin, currentY) {
                            layout.draw(this)
                        }
                        currentY += layout.height
                    }
                }
                if (index < blocks.size - 1) currentY += 8f // spacing between blocks
            }

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
            Log.e("PdfGenerator", "Error creating Markdown PDF with image", e)
            return null
        } catch (e: Exception) {
            Log.e("PdfGenerator", "Error rendering Markdown PDF with image", e)
            return null
        }
    }
    fun generateMarkdownPdf(markdown: String): String? {
        try {
            val processedMarkdown = processMarkdownLinks(markdown)
            val margin = 20f
            val pageWidth = 595
            val contentWidth = (pageWidth - (margin * 2)).toInt()

            // Split markdown into blocks for proper rendering
            val blocks = splitMarkdownIntoBlocks(processedMarkdown)
            val renderedTables = hashMapOf<Int, Bitmap>()

            // Pre-render table bitmaps
            blocks.forEachIndexed { index, block ->
                if (block.isTable) {
                    val act = context as? Activity
                        ?: throw IllegalStateException("PdfGenerator requires an Activity context to render tables")
                    val bmp = renderMarkdownToBitmapAttachedBlocking(
                        act,
                        block.content,
                        contentWidth,
                        "#d0d0d0".toColorInt(),
                        26f
                    )
                    renderedTables[index] = bmp
                }
            }

            // Calculate total height needed
            var totalHeight = margin
            val textPaint = TextPaint().apply {
                color = "#d0d0d0".toColorInt()
                textSize = 26f
            }

            blocks.forEachIndexed { index, block ->
                if (block.isTable) {
                    totalHeight += renderedTables[index]!!.height
                } else {
                    if (block.content.isNotBlank()) {
                        val spanned = markwon.toMarkdown(block.content)
                        val layout = StaticLayout.Builder.obtain(
                            spanned, 0, spanned.length, textPaint, contentWidth
                        )
                            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                            .setLineSpacing(0f, 1f)
                            .setIncludePad(false)
                            .build()
                        totalHeight += layout.height
                    }
                }
                if (index < blocks.size - 1) totalHeight += 8f // spacing between blocks
            }
            totalHeight += margin

            // Create PDF with calculated height
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, totalHeight.toInt(), 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor("#121314".toColorInt())

            // Render content
            var currentY = margin
            blocks.forEachIndexed { index, block ->
                if (block.isTable) {
                    val bmp = renderedTables[index]!!
                    canvas.drawBitmap(bmp, margin, currentY, null)
                    currentY += bmp.height
                } else {
                    if (block.content.isNotBlank()) {
                        val spanned = markwon.toMarkdown(block.content)
                        val layout = StaticLayout.Builder.obtain(
                            spanned, 0, spanned.length, textPaint, contentWidth
                        )
                            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                            .setLineSpacing(0f, 1f)
                            .setIncludePad(false)
                            .build()
                        canvas.withTranslation(margin, currentY) {
                            layout.draw(this)
                        }
                        currentY += layout.height
                    }
                }
                if (index < blocks.size - 1) currentY += 8f // spacing between blocks
            }

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
        } catch (e: Exception) {
            Log.e("PdfGenerator", "Error rendering Markdown PDF", e)
            return null
        }
    }





    fun generateStyledChatPdfWithImages(context: Context, messages: List<FlexibleMessage>, modelName: String): String? {
        return generatePdf(messages, modelName)
    }

    fun generateStyledChatPdf(context: Context, chatText: String, modelName: String): String? {
        val messages = parseChatTextToMessages(chatText)
        return generatePdf(messages, modelName)
    }

    fun generateStyledChatPdfWithGeneratedImages(
        context: Context,
        messages: List<FlexibleMessage>,
        modelName: String,
        generatedImages: Map<Int, String>
    ): String? {
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "chat_${System.currentTimeMillis()}.pdf"
        )
        val userIconDrawable: Drawable? = AppCompatResources.getDrawable(context, R.drawable.ic_person3)
        val aiIconDrawable: Drawable? = AppCompatResources.getDrawable(context, R.drawable.ic_tune3)

        // Calculate total height (similar to generateStyledChatPdfWithImages)
        var totalHeight = pageMargin
        val pageWidth = PageSize.A4.width().toFloat()
        val titlePaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 28f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleHeight = titlePaint.fontSpacing
        totalHeight += titleHeight + bubbleSpacing

        val messagesToRender = messages.filterNot { it.content is JsonPrimitive && it.content.content == "thinking..." }
        messagesToRender.forEachIndexed { index, message ->
            val isUser = message.role == "user"
            var textContent = ""
            var imageBitmap: Bitmap? = null

            // Extract text from content (same as original)
            if (message.content is JsonArray) {
                textContent = message.content.firstNotNullOfOrNull { item ->
                    (item as? JsonObject)?.takeIf { it["type"]?.jsonPrimitive?.content == "text" }?.get("text")?.jsonPrimitive?.content
                } ?: ""
            } else if (message.content is JsonPrimitive) {
                textContent = message.content.content
            }
            textContent = processMarkdownLinks(textContent)
            // Extract uploaded image (base64 from content)
            var uploadedImageBitmap: Bitmap? = null
            if (message.content is JsonArray) {
                val imageUrl = message.content.firstNotNullOfOrNull { item ->
                    (item as? JsonObject)?.takeIf { it["type"]?.jsonPrimitive?.content == "image_url" }?.get("image_url")?.jsonObject?.get("url")?.jsonPrimitive?.content
                }
                if (imageUrl != null) {
                    uploadedImageBitmap = decodeImage(imageUrl)  // Reuse existing decodeImage
                }
            }

            // Prioritize: Use generated image if available, else uploaded
            val finalImageBitmap = if (!isUser && generatedImages.containsKey(index)) {
                loadBitmapFromUri(context, generatedImages[index])
            } else {
                uploadedImageBitmap
            }


            totalHeight += calculateTotalMessageHeight(textContent, finalImageBitmap, pageWidth, if (isUser) userIconDrawable != null else aiIconDrawable != null)
            if (index < messagesToRender.size - 1) {
                totalHeight += bubbleSpacing
            }
        }
        totalHeight += pageMargin + bubbleSpacing

        try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(PageSize.A4.width(), totalHeight.toInt(), 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor("#000000".toColorInt())

            // Draw title
            val titleX = canvas.width / 2f
            var currentY = pageMargin + titlePaint.fontMetrics.top.let { -it }
            canvas.drawText(modelName, titleX, currentY, titlePaint)
            currentY += titlePaint.fontSpacing + bubbleSpacing

            messagesToRender.forEachIndexed { index, message ->
                val isUser = message.role == "user"
                var textContent = ""
                var imageBitmap: Bitmap? = null

                // Extract text (same as above)
                if (message.content is JsonArray) {
                    textContent = message.content.firstNotNullOfOrNull { item ->
                        (item as? JsonObject)?.takeIf { it["type"]?.jsonPrimitive?.content == "text" }?.get("text")?.jsonPrimitive?.content
                    } ?: ""
                } else if (message.content is JsonPrimitive) {
                    textContent = message.content.content
                }
                textContent = processMarkdownLinks(textContent)
// Extract uploaded image (base64 from content)
                var uploadedImageBitmap: Bitmap? = null
                if (message.content is JsonArray) {
                    val imageUrl = message.content.firstNotNullOfOrNull { item ->
                        (item as? JsonObject)?.takeIf { it["type"]?.jsonPrimitive?.content == "image_url" }?.get("image_url")?.jsonObject?.get("url")?.jsonPrimitive?.content
                    }
                    if (imageUrl != null) {
                        uploadedImageBitmap = decodeImage(imageUrl)  // Reuse existing decodeImage
                    }
                }

// Prioritize: Use generated image if available, else uploaded
                val finalImageBitmap = if (!isUser && generatedImages.containsKey(index)) {
                    loadBitmapFromUri(context, generatedImages[index])
                } else {
                    uploadedImageBitmap
                }



                // Draw the message (reuses existing drawMessage and drawBubble logic)
                val messageHeight = calculateTotalMessageHeight(textContent, finalImageBitmap, canvas.width.toFloat(), if (isUser) userIconDrawable != null else aiIconDrawable != null)
                drawMessage(canvas, textContent, finalImageBitmap, isUser, currentY, if (isUser) userIconDrawable else aiIconDrawable)
                currentY += messageHeight + bubbleSpacing
            }

            document.finishPage(page)
            FileOutputStream(file).use { document.writeTo(it) }
            document.close()
            return file.absolutePath
        } catch (e: IOException) {
            Log.e("PdfGenerator", "Error creating PDF with generated images", e)
            return null
        }
    }

    // Helper function to load bitmap from URI
    private fun loadBitmapFromUri(context: Context, uriString: String?): Bitmap? {
        if (uriString == null) return null
        return try {
            val uri = android.net.Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            Log.e("PdfGenerator", "Failed to load bitmap from URI: $uriString", e)
            null
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
        val userIconDrawable: Drawable? = AppCompatResources.getDrawable(context,R.drawable.ic_person3)
        val aiIconDrawable: Drawable? = AppCompatResources.getDrawable(context,R.drawable.ic_tune3)

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

        val messagesToRender = messages.filterNot { it.content is JsonPrimitive && it.content.content == "thinking..." }

        messagesToRender.forEachIndexed { index, message ->
            val isUser = message.role == "user"
            var textContent = ""
            var imageBitmap: Bitmap? = null
            if (message.content is JsonArray) {
                val contentArray = message.content
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
                textContent = message.content.content
            }
            textContent = processMarkdownLinks(textContent)
            totalHeight += calculateTotalMessageHeight(textContent, imageBitmap, pageWidth, if (isUser) userIconDrawable != null else aiIconDrawable != null)
            if (index < messagesToRender.size - 1) {
                totalHeight += bubbleSpacing // Add spacing between messages
            }
        }
        totalHeight += pageMargin + bubbleSpacing // Add bottom margin and a small buffer

        try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(PageSize.A4.width(), totalHeight.toInt(), 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            // Set background color
            canvas.drawColor("#000000".toColorInt())

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
                    val contentArray = message.content
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
                    textContent = message.content.content
                }
                textContent = processMarkdownLinks(textContent)

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

    private fun processMarkdownLinks(markdown: String): String {
        // Regex to match Markdown links: [text](url)
        // Handles optional spaces around text and url
        val pattern = Pattern.compile("\\[\\s*([^]]+?)\\s*]\\(\\s*([^)]+?)\\s*\\)")
        val matcher = pattern.matcher(markdown)
        val sb = StringBuffer()
        while (matcher.find()) {
            val text = matcher.group(1)
            val url = matcher.group(2)
            matcher.appendReplacement(sb, "$text ($url)")
        }
        matcher.appendTail(sb)
        return sb.toString()
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
        val textPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 26f
        }

        val availableWidth = pageWidth - (pageMargin * 2)
        val bubbleWidth = availableWidth * 0.96f
        val bubbleContentWidth = bubbleWidth - (bubblePadding * 2)

        var height = 0f
        val blocks = splitMarkdownIntoBlocks(text)

        blocks.forEachIndexed { idx, block ->
            if (block.isTable) {
                val act = context as? Activity
                    ?: throw IllegalStateException("PdfGenerator requires an Activity context to render tables")
                val bmp = renderMarkdownToBitmapAttachedBlocking(
                    act,
                    block.content,
                    bubbleContentWidth.toInt(),
                    Color.WHITE,
                    26f
                )
                height += bmp.height
            } else {
                if (block.content.isNotBlank()) {
                    val spanned = markwon.toMarkdown(block.content)
                    val layout = StaticLayout.Builder
                        .obtain(spanned, 0, spanned.length, textPaint, bubbleContentWidth.toInt())
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL) // keep left
                        .setIncludePad(false)
                        .build()
                    height += layout.height
                }
            }
            if (idx < blocks.size - 1) height += 8f // small gap between blocks
        }

        if (image != null) {
            val scaledWidth = minOf(image.width.toFloat(), bubbleContentWidth)
            val aspectRatio = image.height.toFloat() / image.width.toFloat()
            if (text.isNotBlank()) height += bubbleSpacing
            height += scaledWidth * aspectRatio
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
        val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isUser) "#36454F".toColorInt() else "#2C2C2C".toColorInt()
        }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f
        }

        val availableWidth = canvas.width - (pageMargin * 2)
        val bubbleWidth = availableWidth * 0.96f
        val bubbleContentWidth = bubbleWidth - (bubblePadding * 2)

        // Measure blocks again for final height
        val blocks = splitMarkdownIntoBlocks(text)

        // Pre-render table bitmaps so we know their height (cache if you want)
        val renderedTables = hashMapOf<Int, Bitmap>()
        blocks.forEachIndexed { index, block ->
            if (block.isTable) {
                val act = context as? Activity
                    ?: throw IllegalStateException("PdfGenerator requires an Activity context to render tables")
                val bmp = renderMarkdownToBitmapAttachedBlocking(
                    act,
                    block.content,
                    bubbleContentWidth.toInt(),
                    Color.WHITE,
                    26f
                )
                renderedTables[index] = bmp
            }
        }

        // Compute total bubble height
        var contentHeights = 0f
        blocks.forEachIndexed { index, block ->
            contentHeights += if (block.isTable) {
                renderedTables[index]!!.height.toFloat()
            } else {
                if (block.content.isNotBlank()) {
                    val spanned = markwon.toMarkdown(block.content)
                    val layout = StaticLayout.Builder
                        .obtain(spanned, 0, spanned.length, textPaint, bubbleContentWidth.toInt())
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setIncludePad(false)
                        .build()
                    layout.height.toFloat()
                } else 0f
            }
            if (index < blocks.size - 1) contentHeights += 8f
        }

        var imagePartHeight = 0f
        var scaledImageWidth = 0f
        if (image != null) {
            scaledImageWidth = minOf(image.width.toFloat(), bubbleContentWidth)
            imagePartHeight = scaledImageWidth * (image.height.toFloat() / image.width.toFloat())
        }

        val bubbleHeight = contentHeights + imagePartHeight + (bubblePadding * 2) +
                if (text.isNotBlank() && image != null) bubbleSpacing else 0f

        val bubbleLeft = if (isUser) canvas.width - pageMargin - bubbleWidth else pageMargin
        val bubbleRect = RectF(bubbleLeft, startY, bubbleLeft + bubbleWidth, startY + bubbleHeight)

        canvas.withClip(bubbleRect) {
            drawRoundRect(bubbleRect, bubbleCornerRadius, bubbleCornerRadius, bubblePaint)

            var y = startY + bubblePadding

            // Image first if any
            if (image != null) {
                val imageLeft = bubbleLeft + bubblePadding
                val dst = RectF(imageLeft, y, imageLeft + scaledImageWidth, y + imagePartHeight)
                drawBitmap(image, null, dst, null)
                y += imagePartHeight + if (blocks.isNotEmpty()) bubbleSpacing else 0f
            }

            // Draw blocks in order
            blocks.forEachIndexed { index, block ->
                if (block.isTable) {
                    val bmp = renderedTables[index]!!
                    drawBitmap(bmp, bubbleLeft + bubblePadding, y, null)
                    y += bmp.height
                } else {
                    if (block.content.isNotBlank()) {
                        val spanned = markwon.toMarkdown(block.content)
                        val layout = StaticLayout.Builder
                            .obtain(spanned, 0, spanned.length, textPaint, bubbleContentWidth.toInt())
                            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                            .setIncludePad(false)
                            .build()
                        withSave {
                            translate(bubbleLeft + bubblePadding, y)
                            layout.draw(this)
                        }
                        y += layout.height
                    }
                }
                if (index < blocks.size - 1) y += 8f
            }
        }
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
    private fun splitMarkdownIntoBlocks(md: String): List<MdBlock> {
        val lines = md.lines()
        val out = mutableListOf<MdBlock>()
        val buf = StringBuilder()

        fun flushText() {
            if (buf.isNotEmpty()) {
                out += MdBlock(false, buf.toString().trimEnd())
                buf.clear()
            }
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val isHeaderRow = line.contains('|') && line.count { it == '|' } >= 2
            val isSep = if (i + 1 < lines.size) isTableSeparatorLine(lines[i + 1]) else false

            if (isHeaderRow && isSep) {
                flushText()
                val table = StringBuilder()
                table.appendLine(line)
                table.appendLine(lines[i + 1])
                i += 2
                // collect table body (consecutive lines that look like rows)
                while (i < lines.size && looksLikeTableRow(lines[i])) {
                    table.appendLine(lines[i])
                    i++
                }
                out += MdBlock(true, table.toString().trimEnd())
                // do not i++ here because while loop advanced
                continue
            } else {
                buf.appendLine(line)
            }
            i++
        }
        flushText()
        return out
    }

    private fun isTableSeparatorLine(s: String): Boolean {
        // permissive: composed of pipes, colons, dashes and spaces and has at least 3 dashes
        if (s.isBlank()) return false
        if (!s.all { it == '|' || it == ':' || it == '-' || it.isWhitespace() }) return false
        return s.count { it == '-' } >= 3
    }

    private fun looksLikeTableRow(s: String): Boolean {
        // a row if it has at least two pipes
        return s.contains('|') && s.count { it == '|' } >= 2
    }

    private fun renderMarkdownToBitmapAttachedBlocking(
        activity: Activity,
        markdownTable: String,
        widthPx: Int,
        textColor: Int,
        textSizePx: Float
    ): Bitmap {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)

        val host = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            x = -widthPx.toFloat() - 100
            y = -10000f
        }

        val tv = AppCompatTextView(activity).apply {
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            // Keep these sharpness improvements:
            paint.isAntiAlias = true
            paint.isSubpixelText = true
            paint.isFilterBitmap = true
            paint.isDither = true
        }

        val latch = CountDownLatch(1)
        var bmp: Bitmap? = null

        activity.runOnUiThread {
            host.addView(tv)
            root.addView(host)
            markwon.setMarkdown(tv, "\n$markdownTable\n")

            val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
                private var passCount = 0
                private val maxPasses = 10

                override fun onGlobalLayout() {
                    val measuredHeight = host.measuredHeight
                    if (measuredHeight > 0 && passCount < maxPasses) {
                        passCount++
                        host.requestLayout()
                    } else {
                        if (host.viewTreeObserver.isAlive) {
                            host.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        }

                        val finalWidth = host.width.coerceAtLeast(1)
                        val finalHeight = host.height.coerceAtLeast(1)

                        // Create bitmap WITHOUT density scaling (removed that line)
                        bmp = createBitmap(finalWidth, finalHeight).apply {
                            setHasAlpha(true) // Keep transparency for cleaner edges
                        }

                        // val canvas = Canvas(bmp)
                        val canvas = Canvas(bmp).apply {
                            density = DisplayMetrics.DENSITY_XXHIGH
                        }
                        host.draw(canvas)

                        root.removeView(host)
                        latch.countDown()
                    }
                }
            }
            host.viewTreeObserver.addOnGlobalLayoutListener(listener)
        }

        latch.await(3, TimeUnit.SECONDS)
        return requireNotNull(bmp) { "Failed to render table bitmap, the operation timed out." }
    }

    object PageSize {
        val A4 = Rect(0, 0, 595, 842)
    }
}

