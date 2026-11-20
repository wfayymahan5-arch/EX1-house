package com.ex1legend.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.view.Gravity
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private var ex1WebView: WebView? = null
    private var splashOverlay: View? = null
    private var startupMessageView: View? = null
    private var startupMessageText: TextView? = null
    private var appRoot: FrameLayout? = null
    private var splashAnimationFinished = false
    private var pageReady = false
    private var loadInProgress = false
    private var mainFrameLoadFailed = false
    private var usingBundledFallback = false
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
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
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
                    if (loadedUri.host != EX1_HOST || mainFrameLoadFailed) {
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
                    view.evaluateJavascript("window.EX1_NATIVE_APP=true;window.EX1_UPDATE_CHANNEL='" + BuildConfig.EX1_UPDATE_CHANNEL + "';", null)
                    val readyCheck = if (BuildConfig.IS_ADMIN) {
                        "(document.readyState === 'complete' && !!document.getElementById('authView') && !!document.getElementById('appView') && window.EX1_ADMIN_READY === true)"
                    } else {
                        "(document.readyState === 'complete' && !!document.querySelector('.page.active'))"
                    }
                    verifyPageReady(view, readyCheck)
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: android.webkit.WebResourceResponse
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                        if (!usingBundledFallback) loadBundledFallback("نسخهٔ آنلاین در دسترس نیست؛ نسخهٔ پشتیبان اجرا می‌شود.")
                        else {
                            mainFrameLoadFailed = true; loadInProgress = false; pageReady = false
                            view.stopLoading(); view.visibility = View.INVISIBLE
                            showStartupMessage("نسخهٔ آنلاین و نسخهٔ پشتیبان بارگذاری نشدند.")
                        }
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError
                ) {
                    super.onReceivedError(view, request, error)
                    if (request.isForMainFrame) {
                        if (!usingBundledFallback) loadBundledFallback("اتصال به نسخهٔ آنلاین برقرار نشد؛ نسخهٔ پشتیبان اجرا می‌شود.")
                        else {
                            mainFrameLoadFailed = true; loadInProgress = false; pageReady = false
                            view.visibility = View.INVISIBLE
                            showStartupMessage("نسخهٔ آنلاین و نسخهٔ پشتیبان در دسترس نیستند.")
                        }
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

        web.addJavascriptInterface(object {
            @JavascriptInterface
            fun reloadApp() {
                mainHandler.post {
                    pageReady = false
                    loadInProgress = false
                    mainFrameLoadFailed = false
                    tryLoadWhenOnline()
                }
            }
        }, "Ex1Native")

        ex1WebView = web
        root.addView(web, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        if (BuildConfig.IS_ADMIN) {
            // The intro animation belongs to the player edition only.
            splashAnimationFinished = true
        } else {
            val splash = createSplashOverlay()
            splashOverlay = splash
            root.addView(splash, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            startSplashAnimation(splash)
        }
        setContentView(root)
        registerNetworkCallback()
        tryLoadWhenOnline()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val current = ex1WebView
                if (current?.canGoBack() == true) current.goBack() else finish()
            }
        })
    }

    private fun verifyPageReady(view: WebView, readyCheck: String, startedAt: Long = SystemClock.elapsedRealtime()) {
        if (isFinishing || isDestroyed || mainFrameLoadFailed) return
        view.evaluateJavascript(readyCheck) { result ->
            if (isFinishing || isDestroyed || mainFrameLoadFailed) return@evaluateJavascript
            if (result == "true") {
                loadInProgress = false
                startupMessageView?.let { appRoot?.removeView(it) }
                startupMessageView = null
                startupMessageText = null
                view.visibility = View.VISIBLE
                pageReady = true
                scheduleSplashExit()
            } else if (SystemClock.elapsedRealtime() - startedAt < 25000L) {
                mainHandler.postDelayed({ verifyPageReady(view, readyCheck, startedAt) }, 250L)
            } else {
                loadInProgress = false
                view.visibility = View.INVISIBLE
                pageReady = false
                showStartupMessage("پنل مدیریت هنوز آماده نشده است. اتصال اینترنت و بارگذاری سرویس را بررسی کن.")
            }
        }
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
        // Load the page behind the lightweight native intro so it is ready when the intro ends.
        if (!hasValidatedInternet()) {
            showOfflineMessageIfNeeded()
            return
        }
        if (pageReady || loadInProgress) return
        loadInProgress = true
        mainFrameLoadFailed = false
        ex1WebView?.visibility = View.INVISIBLE
        val pageUrl = if (BuildConfig.IS_ADMIN) ADMIN_URL else PLAYER_URL
        usingBundledFallback = false
        ex1WebView?.loadUrl(pageUrl)
    }

    private fun loadBundledFallback(message: String) {
        if (isFinishing || isDestroyed || usingBundledFallback) return
        usingBundledFallback = true
        mainFrameLoadFailed = false
        loadInProgress = true
        pageReady = false
        val pageUrl = if (BuildConfig.IS_ADMIN) ADMIN_URL else PLAYER_URL
        val assetName = if (BuildConfig.IS_ADMIN) "ex1-admin.html" else "ex1-player.html"
        try {
            val html = assets.open(assetName).bufferedReader(Charsets.UTF_8).use { it.readText() }
                .replaceFirst("<head>", "<head><script>window.EX1_NATIVE_APP=true;window.EX1_UPDATE_CHANNEL='" + BuildConfig.EX1_UPDATE_CHANNEL + "';</script><style id=\"ex1-native-performance\">*,*::before,*::after{animation:none!important;transition:none!important;scroll-behavior:auto!important;backdrop-filter:none!important;-webkit-backdrop-filter:none!important}body::before,body::after{content:none!important;display:none!important}</style>")
                .replace("retry.onclick=()=>location.reload();", "retry.onclick=()=>window.Ex1Native?window.Ex1Native.reloadApp():location.reload();")
            ex1WebView?.loadDataWithBaseURL(pageUrl, html, "text/html", "UTF-8", null)
            showStartupMessage(message)
        } catch (error: Exception) {
            loadInProgress = false; mainFrameLoadFailed = true
            showStartupMessage("نسخهٔ آنلاین و فایل پشتیبان در دسترس نیستند.")
        }
    }

    private fun showOfflineMessageIfNeeded() {
        if (pageReady || isFinishing || isDestroyed) return
        loadInProgress = false
        ex1WebView?.stopLoading()
        ex1WebView?.visibility = View.INVISIBLE
        showStartupMessage("برای بازشدن EX1 LEGEND به اتصال اینترنت پایدار نیاز است.")
    }

    private fun showStartupMessage(message: String) {
        if (isFinishing || isDestroyed) return
        loadInProgress = false
        pageReady = false
        ex1WebView?.visibility = View.INVISIBLE
        val root = appRoot ?: return
        if (startupMessageView == null) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(28), dp(28), dp(28), dp(28))
                setBackgroundColor(Color.rgb(11, 9, 22))
                isClickable = true
                isFocusable = true
            }
            val title = TextView(this).apply {
                text = "EX1 LEGEND"
                textSize = 25f
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            val detail = TextView(this).apply {
                textSize = 14f
                setTextColor(Color.rgb(194, 184, 215))
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, dp(18))
            }
            val retry = Button(this).apply {
                text = "تلاش دوباره"
                setOnClickListener {
                    startupMessageView?.let { root.removeView(it) }
                    startupMessageView = null
                    startupMessageText = null
                    mainFrameLoadFailed = false
                    tryLoadWhenOnline()
                }
            }
            box.addView(title, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            box.addView(detail, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            box.addView(retry, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            startupMessageView = box
            startupMessageText = detail
            root.addView(box, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        startupMessageText?.text = message
    }

    private fun createSplashOverlay(): View {
        val frame = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(7, 7, 14))
        }
        val title = TextView(this).apply {
            text = "EX1 LEGEND"
            textSize = 34f
            letterSpacing = 0.10f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setShadowLayer(dp(10).toFloat(), 0f, 0f, Color.rgb(116, 70, 190))
            background = GradientDrawable().apply {
                setColor(Color.rgb(15, 13, 26))
                setStroke(dp(1), Color.rgb(104, 75, 145))
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        frame.addView(title, FrameLayout.LayoutParams(dp(292), dp(112), Gravity.CENTER))
        return frame
    }

    private fun startSplashAnimation(view: View) {
        // Static title only: no preview frames, image scaling, or splash animation.
        splashAnimationFinished = true
        tryLoadWhenOnline()
        scheduleSplashExit()
    }

    private fun scheduleSplashExit() {
        if (!pageReady || !splashAnimationFinished) return
        mainHandler.removeCallbacks(splashExitRunnable)
        mainHandler.postDelayed(splashExitRunnable, 120L)
    }

    private val splashExitRunnable = Runnable { hideSplash() }

    private fun hideSplash() {
        val overlay = splashOverlay ?: return
        appRoot?.removeView(overlay)
        splashOverlay = null
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
        private val PLAYER_URL = "$EX1_BASE_URL/app.html?ex1_native=1&ex1_channel=" + BuildConfig.EX1_UPDATE_CHANNEL
        private val ADMIN_URL = "$EX1_BASE_URL/admin.html?ex1_native=1&ex1_channel=" + BuildConfig.EX1_UPDATE_CHANNEL
    }
}
