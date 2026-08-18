package com.example

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A) // Dark Slate
                ) {
                    PosWebViewScreen(
                        onWebViewCreated = { wv -> webView = wv }
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PosWebViewScreen(onWebViewCreated: (WebView) -> Unit) {
    var canGoBack by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = canGoBack) {
        webViewInstance?.goBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(Color(0xFF0F172A))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    setBackgroundColor(0xFF0F172A.toInt())

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        builtInZoomControls = false
                        displayZoomControls = false
                        setSupportZoom(false)
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    // Enable cookies
                    CookieManager.getInstance().setAcceptCookie(true)

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            canGoBack = view?.canGoBack() == true
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            return super.onConsoleMessage(consoleMessage)
                        }
                    }

                    // Handle Base64 Data URL and File Downloads (For PNG Voucher & JSON Backup)
                    setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                        try {
                            if (url.startsWith("data:")) {
                                handleDataUrlDownload(context, url, mimetype)
                            } else {
                                val request = DownloadManager.Request(Uri.parse(url)).apply {
                                    setMimeType(mimetype)
                                    addRequestHeader("User-Agent", userAgent)
                                    setDescription("Downloading StorePOS File...")
                                    setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype))
                                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    setDestinationInExternalPublicDir(
                                        Environment.DIRECTORY_DOWNLOADS,
                                        URLUtil.guessFileName(url, contentDisposition, mimetype)
                                    )
                                }
                                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                dm.enqueue(request)
                                Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Download handling: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    loadUrl("file:///android_asset/index.html")
                    webViewInstance = this
                    onWebViewCreated(this)
                }
            },
            update = { wv ->
                webViewInstance = wv
                canGoBack = wv.canGoBack()
            }
        )
    }
}

/**
 * Handles saving Base64 data URLs (like exported voucher PNG and JSON backup) to device storage
 */
private fun handleDataUrlDownload(context: Context, dataUrl: String, mimeType: String) {
    try {
        val commaIndex = dataUrl.indexOf(",")
        if (commaIndex == -1) return

        val header = dataUrl.substring(0, commaIndex)
        val base64Data = dataUrl.substring(commaIndex + 1)
        val isBase64 = header.contains(";base64")

        val bytes = if (isBase64) {
            Base64.decode(base64Data, Base64.DEFAULT)
        } else {
            Uri.decode(base64Data).toByteArray(Charsets.UTF_8)
        }

        val extension = when {
            header.contains("image/png") -> ".png"
            header.contains("application/json") -> ".json"
            header.contains("text/csv") -> ".csv"
            else -> ".bin"
        }

        val filename = "storepos_${System.currentTimeMillis()}$extension"
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val file = File(downloadsDir, filename)

        FileOutputStream(file).use { fos ->
            fos.write(bytes)
            fos.flush()
        }

        Toast.makeText(context, "Saved: $filename", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error saving file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
