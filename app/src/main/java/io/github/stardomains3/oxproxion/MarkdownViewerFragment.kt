package io.github.stardomains3.oxproxion

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

class MarkdownViewerFragment : Fragment() {
    companion object {
        private const val ARG_MARKDOWN = "markdown"
        private const val ARG_FONT_NAME = "font_name" // New Argument
        private const val ARG_MODEL_NAME = "model_name"
        private lateinit var prefs: SharedPreferencesHelper
        private var currentFontSize = 100
        // Update newInstance to accept the font name string
        fun newInstance(markdown: String, fontName: String, modelName: String): MarkdownViewerFragment {
            return MarkdownViewerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MARKDOWN, markdown)
                    putString(ARG_FONT_NAME, fontName)
                    putString(ARG_MODEL_NAME, modelName)
                }
            }
        }
    }

    private var webView: WebView? = null
    private var currentHtml: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_markdown_viewer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = SharedPreferencesHelper(requireContext())
        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)

        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        toolbar.inflateMenu(R.menu.menu_markdown_viewer)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_decrease_font -> {
                    decreaseFont()
                    true
                }
                R.id.action_increase_font -> {
                    increaseFont()
                    true
                }
                R.id.action_print -> {
                    createWebPrintJob(webView)
                    true
                }
                else -> false
            }
        }

        webView = view.findViewById(R.id.webview_markdown)
        webView?.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = false
                setSupportZoom(true)
                textZoom = 100
                builtInZoomControls = true
                displayZoomControls = false
                defaultTextEncodingName = "UTF-8"
                //allowContentAccess = false – Blocks access to content providers.
                //cacheMode = WebSettings.LOAD_NO_CACHE – Forces no caching for local content (optional, but secure).
                setGeolocationEnabled(false)// – Disables location access.
                mediaPlaybackRequiresUserGesture = true //– Requires user interaction for media (if added later).
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }

            addJavascriptInterface(WebAppInterface(requireContext()), "Android")

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:")) {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                            return true
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    return false
                }
            }

            setBackgroundColor(Color.BLACK)

            val markdown = arguments?.getString(ARG_MARKDOWN, "") ?: return@apply
            val fontName = arguments?.getString(ARG_FONT_NAME, "system_default") ?: "system_default"
            val modelName = arguments?.getString(ARG_MODEL_NAME, "AI") ?: "AI"
            toolbar.title = modelName
            // Pass the font name to the renderer
            currentHtml = MarkdownRenderer.toHtml(markdown, fontName)

            loadDataWithBaseURL("file:///android_asset/", currentHtml, "text/html", "UTF-8", null)
            currentFontSize = prefs.getFontSize()
            settings.textZoom = currentFontSize
        }
    }

    // ... (Rest of your Save/Print functions remain the same) ...

    private fun saveHtmlToDownloads() {
        if (currentHtml.isEmpty()) return
        val filename = "chat-${System.currentTimeMillis()}.html"
        val context = requireContext()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/html")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw Exception("MediaStore insert failed")
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(currentHtml.toByteArray())
                } ?: throw Exception("Cannot open output stream")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "✅ Saved to Downloads: $filename", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "❌ Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private fun increaseFont() {
        currentFontSize = minOf(200, currentFontSize + 5)  // Increase by 5% (max 200%)
        webView?.settings?.textZoom = currentFontSize
        prefs.saveFontSize(currentFontSize)
    }

    private fun decreaseFont() {
        currentFontSize = maxOf(50, currentFontSize - 5)  // Decrease by 5% (min 50%)
        webView?.settings?.textZoom = currentFontSize
        prefs.saveFontSize(currentFontSize)
    }
    private fun createWebPrintJob(webView: WebView?) {
        if (webView == null) return
        val printManager = requireContext().getSystemService(Context.PRINT_SERVICE) as? PrintManager
        val printAdapter = webView.createPrintDocumentAdapter("Markdown_Document")
        val jobName = getString(R.string.app_name) + " Document"
        printManager?.print(jobName, printAdapter, PrintAttributes.Builder().build())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webView?.apply {
            stopLoading()
            removeAllViews()
            destroy()
        }
        webView = null
    }

    class WebAppInterface(private val context: Context) {
        @JavascriptInterface
        fun copyToClipboard(text: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied Code", text)
            clipboard.setPrimaryClip(clip)
        }
    }
}
