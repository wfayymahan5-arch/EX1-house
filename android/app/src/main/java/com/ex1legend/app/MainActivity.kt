package com.ex1legend.app

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.ValueCallback
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.graphics.Color
import android.view.Gravity
import android.content.Intent
import android.net.Uri

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var rootContainer: FrameLayout
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserRequestCode = 1024
    private val appUrl = BuildConfig.START_URL
    private val appHost = Uri.parse(appUrl).host

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 7, 15)
        window.navigationBarColor = Color.rgb(9, 7, 15)
        rootContainer = FrameLayout(this).apply { setBackgroundColor(Color.rgb(9, 7, 15)) }
        progress = ProgressBar(this).apply { isIndeterminate = true }
        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(9, 7, 15))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadsImagesAutomatically = true
            settings.allowFileAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    this@MainActivity.fileUploadCallback?.onReceiveValue(null)
                    this@MainActivity.fileUploadCallback = filePathCallback
                    val intent = try {
                        fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "image/*"
                            addCategory(Intent.CATEGORY_OPENABLE)
                        }
                    } catch (_: Exception) {
                        this@MainActivity.fileUploadCallback?.onReceiveValue(null)
                        this@MainActivity.fileUploadCallback = null
                        return false
                    }
                    return try {
                        startActivityForResult(intent, fileChooserRequestCode)
                        true
                    } catch (_: Exception) {
                        this@MainActivity.fileUploadCallback?.onReceiveValue(null)
                        this@MainActivity.fileUploadCallback = null
                        false
                    }
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    if (uri.scheme == "https" && uri.host == appHost) return false
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    return true
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    rootContainer.removeView(this@MainActivity.progress)
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    if (request?.isForMainFrame == true) rootContainer.removeView(this@MainActivity.progress)
                }
            }
        }
        rootContainer.addView(webView, FrameLayout.LayoutParams(-1, -1))
        rootContainer.addView(progress, FrameLayout.LayoutParams(64, 64, Gravity.CENTER))
        setContentView(rootContainer)
        if (savedInstanceState == null) webView.loadUrl(appUrl)
        else webView.restoreState(savedInstanceState)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == fileChooserRequestCode) {
            val callback = fileUploadCallback
            fileUploadCallback = null
            callback?.onReceiveValue(
                if (resultCode == RESULT_OK) WebChromeClient.FileChooserParams.parseResult(resultCode, data)
                else null
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::webView.isInitialized) webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        fileUploadCallback?.onReceiveValue(null)
        fileUploadCallback = null
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }
}
