package com.ex1legend.app

import android.graphics.Canvas
import android.graphics.Paint
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
                    val readyCheck = if (BuildConfig.IS_ADMIN) {
                        "(document.readyState === 'complete' && !!document.getElementById('authView') && !!document.getElementById('appView') && !!window.supabase && !!window.EX1_CONFIG)"
                    } else {
                        "(document.readyState === 'complete' && !!document.querySelector('.page.active'))"
                    }
                    view.evaluateJavascript(readyCheck) { result ->
                        if (isFinishing || isDestroyed || mainFrameLoadFailed) return@evaluateJavascript
                        if (result != "true") {
                            loadInProgress = false
                            view.visibility = View.INVISIBLE
                            pageReady = false
                            showStartupMessage("صفحهٔ اصلی EX1 آماده نشد. لطفاً کمی بعد دوباره تلاش کن.")
                            return@evaluateJavascript
                        }
                        loadInProgress = false
                        startupMessageView?.let { appRoot?.removeView(it) }
                        startupMessageView = null
                        startupMessageText = null
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
        ex1WebView?.loadUrl(if (BuildConfig.IS_ADMIN) ADMIN_URL else PLAYER_URL)
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
        return FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(7, 5, 17))
            addView(SplashBackdropView(), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))

            val content = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(24), dp(24), dp(24))
                alpha = 0f
                scaleX = 0.96f
                scaleY = 0.96f
            }

            val eyebrow = TextView(this@MainActivity).apply {
                text = "EX1  //  TACTICAL GAMING"
                textSize = 10f
                letterSpacing = 0.22f
                setTextColor(Color.rgb(69, 211, 225))
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            val logo = TextView(this@MainActivity).apply {
                text = "EX1 LEGEND"
                textSize = 36f
                letterSpacing = 0.015f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                setShadowLayer(dp(16).toFloat(), 0f, 0f, Color.rgb(113, 67, 190))
            }
            val divider = View(this@MainActivity).apply {
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.TRANSPARENT, Color.rgb(139, 92, 246), Color.rgb(34, 211, 238), Color.TRANSPARENT)
                )
            }
            val tagline = TextView(this@MainActivity).apply {
                text = "وارد میدان شو، افسانه شو"
                textSize = 15f
                setTextColor(Color.rgb(220, 211, 239))
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setPadding(0, dp(15), 0, dp(24))
            }
            val footer = TextView(this@MainActivity).apply {
                text = "PLAYER NETWORK  /  EST. 2026"
                textSize = 9f
                letterSpacing = 0.18f
                setTextColor(Color.rgb(126, 116, 153))
                gravity = Gravity.CENTER
            }

            content.addView(eyebrow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) })
            content.addView(logo, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            content.addView(divider, LinearLayout.LayoutParams(dp(164), dp(2)).apply {
                topMargin = dp(17)
            })
            content.addView(tagline, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            content.addView(footer, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(content, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            ))
        }
    }

    private inner class SplashBackdropView : View(this@MainActivity) {
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(18, 139, 92, 246)
            strokeWidth = dp(1).toFloat()
        }
        private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 34, 211, 238)
            strokeWidth = dp(2).toFloat()
        }
        private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(65, 167, 139, 250)
            style = Paint.Style.STROKE
            strokeWidth = dp(1).toFloat()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.rgb(7, 5, 17))
            val step = dp(44).toFloat()
            var x = 0f
            while (x < width) {
                canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
                x += step
            }
            var y = 0f
            while (y < height) {
                canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
                y += step
            }

            val inset = dp(24).toFloat()
            val corner = dp(34).toFloat()
            val right = width - inset
            val bottom = height - inset
            canvas.drawLine(inset, inset, inset + corner, inset, accentPaint)
            canvas.drawLine(inset, inset, inset, inset + corner, accentPaint)
            canvas.drawLine(right - corner, inset, right, inset, accentPaint)
            canvas.drawLine(right, inset, right, inset + corner, accentPaint)
            canvas.drawLine(inset, bottom - corner, inset, bottom, accentPaint)
            canvas.drawLine(inset, bottom, inset + corner, bottom, accentPaint)
            canvas.drawLine(right - corner, bottom, right, bottom, accentPaint)
            canvas.drawLine(right, bottom - corner, right, bottom, accentPaint)
            canvas.drawRoundRect(
                inset + dp(8), inset + dp(8), right - dp(8), bottom - dp(8),
                dp(12).toFloat(), dp(12).toFloat(), framePaint
            )
        }
    }

    private fun startSplashAnimation(view: View) {
        val content = (view as? FrameLayout)?.getChildAt(1)
        content?.animate()?.alpha(1f)?.scaleX(1f)?.scaleY(1f)?.setDuration(620L)?.start()
        mainHandler.postDelayed({
            splashAnimationFinished = true
            tryLoadWhenOnline()
            scheduleSplashExit()
        }, 2100L)
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
