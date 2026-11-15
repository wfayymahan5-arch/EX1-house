package com.ex1legend.app

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.graphics.Color
import android.view.Gravity
import android.content.Intent
import android.net.Uri

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private val appUrl = BuildConfig.START_URL
    private val appHost = Uri.parse(appUrl).host

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 7, 15)
        window.navigationBarColor = Color.rgb(9, 7, 15)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(9, 7, 15)) }
        progress = ProgressBar(this).apply { isIndeterminate = true }
        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(9, 7, 15))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadsImagesAutomatically = true
            settings.allowFileAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return false
                    if (uri.scheme == "https" && uri.host == appHost) return false
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    return true
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    progress.setVisibility(View.GONE)
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    if (request?.isForMainFrame == true) progress.setVisibility(View.GONE)
                }
            }
        }
        root.addView(webView, FrameLayout.LayoutParams(-1, -1))
        root.addView(progress, FrameLayout.LayoutParams(64, 64, Gravity.CENTER))
        setContentView(root)
        if (savedInstanceState == null) webView.loadUrl(appUrl)
        else webView.restoreState(savedInstanceState)
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
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }
}
