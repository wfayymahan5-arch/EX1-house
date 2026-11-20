package com.ex1legend.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private var ex1WebView: WebView? = null
    private var splashOverlay: View? = null
    private var appRoot: FrameLayout? = null
    private var splashAnimationFinished = false
    private var pageReady = false
    private var loadInProgress = false
    private var mainFrameLoadFailed = false
    private var networkCallbackRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val connectivityManager by lazy { getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager }
    private var fileChooserCallback: android.webkit.ValueCallback<Array<Uri>>? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            mainHandler.postDelayed({ tryLoadWhenOnline() }, 400)
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                mainHandler.post { tryLoadWhenOnline() }
            }
        }

        override fun onLost(network: Network) {
            mainHandler.post { showOfflineMessageIfNeeded() }
        }
    }

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileChooserCallback?.onReceiveValue(if (uri == null) null else arrayOf(uri))
        fileChooserCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 7, 20)
        window.navigationBarColor = Color.rgb(9, 7, 20)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(9, 7, 20))
        }
        appRoot = root

        val web = WebView(this).apply {
            setBackgroundColor(Color.rgb(9, 7, 20))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (uri.scheme != "https") return true
                    if (uri.host != EX1_HOST) {
                        if (request.isForMainFrame) {
                            mainHandler.post {
                                showStartupMessage("دسترسی به آدرس نسخهٔ آزمایشی EX1 ممکن نیست.")
                            }
                        }
                        return true
                    }
                    return false
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    val loadedUri = Uri.parse(url)
                    val expectedPath = if (BuildConfig.IS_ADMIN) "/admin.html" else "/app.html"
                    if (loadedUri.host != EX1_HOST || loadedUri.path != expectedPath || mainFrameLoadFailed) {
                        loadInProgress = false
                        view.visibility = View.INVISIBLE
                        pageReady = false
                        showStartupMessage("صفحهٔ اصلی EX1 بارگذاری نشد. نسخهٔ آزمایشی را بررسی می‌کنیم.")
                        return
                    }
                    if (!hasValidatedInternet()) {
                        view.stopLoading()
                        view.visibility = View.INVISIBLE
                        pageReady = false
                        showOfflineMessageIfNeeded()
                        return
                    }
                    view.evaluateJavascript(
                        "(document.readyState === 'complete' && !!document.querySelector('.page.active'))"
                    ) { result ->
                        if (isFinishing || isDestroyed || mainFrameLoadFailed) return@evaluateJavascript
                        if (result != "true") {
                            loadInProgress = false
                            view.visibility = View.INVISIBLE
                            pageReady = false
                            showStartupMessage("صفحهٔ اصلی EX1 آماده نشد. لطفاً کمی بعد دوباره تلاش کن.")
                            return@evaluateJavascript
                        }
                        loadInProgress = false
                        view.visibility = View.VISIBLE
                        pageReady = true
                        scheduleSplashExit()
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: android.webkit.WebResourceResponse
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                        mainFrameLoadFailed = true
                        loadInProgress = false
                        pageReady = false
                        view.stopLoading()
                        view.visibility = View.INVISIBLE
                        showStartupMessage(
                            if (errorResponse.statusCode == 401 || errorResponse.statusCode == 403) {
                                "دسترسی به نسخهٔ آزمایشی EX1 مجاز نیست."
                            } else {
                                "بارگذاری برنامه ناموفق بود. لطفاً کمی بعد دوباره تلاش کن."
                            }
                        )
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError
                ) {
                    super.onReceivedError(view, request, error)
                    if (request.isForMainFrame) {
                        mainFrameLoadFailed = true
                        loadInProgress = false
                        pageReady = false
                        view.visibility = View.INVISIBLE
                        showStartupMessage("بارگذاری برنامه ناموفق بود. اتصال یا آدرس نسخه را بررسی می‌کنیم.")
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView,
                    filePathCallback: android.webkit.ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams
                ): Boolean {
                    this@MainActivity.fileChooserCallback?.onReceiveValue(null)
                    this@MainActivity.fileChooserCallback = filePathCallback
                    imagePicker.launch("image/*")
                    return true
                }
            }
            visibility = View.INVISIBLE
        }

        ex1WebView = web
        root.addView(web, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val splash = createSplashOverlay()
        splashOverlay = splash
        root.addView(splash, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        setContentView(root)
        startSplashAnimation(splash)
        registerNetworkCallback()
        tryLoadWhenOnline()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val current = ex1WebView
                if (current?.canGoBack() == true) current.goBack() else finish()
            }
        })
    }

    private fun hasValidatedInternet(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            networkCallbackRegistered = true
        } catch (_: Exception) {
            showOfflineMessageIfNeeded()
        }
    }

    private fun tryLoadWhenOnline() {
        if (isFinishing || isDestroyed) return
        if (!hasValidatedInternet()) {
            showOfflineMessageIfNeeded()
            return
        }
        if (pageReady || loadInProgress) return
        loadInProgress = true
        mainFrameLoadFailed = false
        ex1WebView?.visibility = View.INVISIBLE
        ex1WebView?.loadUrl(if (BuildConfig.IS_ADMIN) ADMIN_URL else PLAYER_URL)
    }

    private fun showOfflineMessageIfNeeded() {
        if (pageReady || isFinishing || isDestroyed) return
        loadInProgress = false
        ex1WebView?.stopLoading()
        ex1WebView?.visibility = View.INVISIBLE
    }

    private fun showStartupMessage(message: String) {
        if (isFinishing || isDestroyed) return
        loadInProgress = false
        pageReady = false
        ex1WebView?.visibility = View.INVISIBLE
    }

    private fun createSplashOverlay(): View {
        return FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(7, 5, 17))
            isClickable = true
            isFocusable = true
            addView(SplashFramesView(), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
    }

    private inner class SplashFramesView : View(this@MainActivity) {
        private val bitmap: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.ex1_splash_strip)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val source = Rect()
        private val destination = RectF()
        private val frameWidth = bitmap.width / 7
        private val frameHeight = bitmap.height
        private val frameDurationMs = 450L
        private val startedAt = SystemClock.elapsedRealtime()
        private var completionSent = false

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
            val frame = (elapsed / frameDurationMs).toInt().coerceAtMost(6)
            source.set(frame * frameWidth, 0, (frame + 1) * frameWidth, frameHeight)

            val scale = minOf(width.toFloat() / frameWidth, height.toFloat() / frameHeight) * 0.88f
            val drawWidth = frameWidth * scale
            val drawHeight = frameHeight * scale
            val left = (width - drawWidth) / 2f
            val top = (height - drawHeight) / 2f
            destination.set(left, top, left + drawWidth, top + drawHeight)
            canvas.drawBitmap(bitmap, source, destination, paint)

            if (elapsed < frameDurationMs * 7) {
                postInvalidateDelayed(16L)
            } else if (!completionSent) {
                completionSent = true
                post {
                    splashAnimationFinished = true
                    scheduleSplashExit()
                }
            }
        }
    }

    private fun startSplashAnimation(view: View) {
        view.invalidate()
    }

    private fun scheduleSplashExit() {
        if (!pageReady || !splashAnimationFinished) return
        mainHandler.removeCallbacks(splashExitRunnable)
        mainHandler.postDelayed(splashExitRunnable, 120L)
    }

    private val splashExitRunnable = Runnable { hideSplash() }

    private fun hideSplash() {
        val overlay = splashOverlay ?: return
        overlay.animate().alpha(0f).setDuration(360).withEndAction {
            appRoot?.removeView(overlay)
            splashOverlay = null
        }.start()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        if (networkCallbackRegistered) {
            try { connectivityManager.unregisterNetworkCallback(networkCallback) } catch (_: Exception) { }
            networkCallbackRegistered = false
        }
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        ex1WebView?.apply {
            stopLoading()
            webChromeClient = null
            webViewClient = WebViewClient()
            destroy()
        }
        ex1WebView = null
        splashOverlay = null
        appRoot = null
        super.onDestroy()
    }

    companion object {
        private val EX1_BASE_URL = BuildConfig.EX1_BASE_URL.trimEnd('/')
        private val EX1_HOST = Uri.parse(EX1_BASE_URL).host
        private val PLAYER_URL = "$EX1_BASE_URL/app.html"
        private val ADMIN_URL = "$EX1_BASE_URL/admin.html"
    }
}
