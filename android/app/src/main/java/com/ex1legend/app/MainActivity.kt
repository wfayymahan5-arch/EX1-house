package com.ex1legend.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private var ex1WebView: WebView? = null
    private var splashOverlay: View? = null
    private var appRoot: FrameLayout? = null
    private var pageReady = false
    private val splashStartedAt = SystemClock.elapsedRealtime()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var fileChooserCallback: android.webkit.ValueCallback<Array<Uri>>? = null

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
                    return uri.scheme != "https" || uri.host != EX1_HOST
                }
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    view.visibility = View.VISIBLE
                    pageReady = true
                    scheduleSplashExit()
                }
                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError
                ) {
                    super.onReceivedError(view, request, error)
                    if (request.isForMainFrame) {
                        pageReady = true
                        scheduleSplashExit()
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
        web.loadUrl(if (BuildConfig.IS_ADMIN) ADMIN_URL else PLAYER_URL)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val current = ex1WebView
                if (current?.canGoBack() == true) current.goBack() else finish()
            }
        })
    }

    private fun createSplashOverlay(): View {
        val background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(8, 6, 18), Color.rgb(25, 10, 48), Color.rgb(7, 9, 22))
        )
        val container = FrameLayout(this).apply {
            background = background
            isClickable = true
            isFocusable = true
        }

        val glow = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(95, 151, 65, 255), Color.TRANSPARENT)
            ).apply { cornerRadius = dp(220).toFloat() }
            alpha = 0.72f
        }
        val glowParams = FrameLayout.LayoutParams(dp(300), dp(300), Gravity.CENTER)
        container.addView(glow, glowParams)

        val content = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "EX1 LEGEND"
        }
        content.addView(logo, android.widget.LinearLayout.LayoutParams(dp(184), dp(184)))

        val title = TextView(this).apply {
            text = "EX1 LEGEND"
            textSize = 29f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            letterSpacing = 0.06f
            gravity = Gravity.CENTER
            setShadowLayer(dp(18).toFloat(), 0f, 0f, Color.rgb(155, 76, 245))
        }
        val titleParams = android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        titleParams.topMargin = dp(18)
        content.addView(title, titleParams)

        val tagline = TextView(this).apply {
            text = "PLAY  •  COMPETE  •  WIN"
            textSize = 10f
            setTextColor(Color.rgb(204, 176, 244))
            letterSpacing = 0.18f
            gravity = Gravity.CENTER
        }
        val taglineParams = android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        taglineParams.topMargin = dp(12)
        content.addView(tagline, taglineParams)

        container.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        return container
    }

    private fun startSplashAnimation(view: View) {
        val logo = (view as FrameLayout).getChildAt(1) as? android.widget.LinearLayout ?: return
        logo.alpha = 0f
        logo.scaleX = 0.82f
        logo.scaleY = 0.82f
        val enter = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.82f, 1f),
                ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.82f, 1f)
            )
            duration = 760
            interpolator = DecelerateInterpolator(1.7f)
        }
        enter.start()
        val pulseX = ObjectAnimator.ofFloat(logo, View.SCALE_X, 1f, 1.025f, 1f).apply {
            duration = 1450
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
        }
        val pulseY = ObjectAnimator.ofFloat(logo, View.SCALE_Y, 1f, 1.025f, 1f).apply {
            duration = 1450
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
        }
        enter.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                pulseX.start()
                pulseY.start()
            }
        })
    }

    private fun scheduleSplashExit() {
        if (!pageReady) return
        val remaining = (2300L - (SystemClock.elapsedRealtime() - splashStartedAt)).coerceAtLeast(0L)
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({ hideSplash() }, remaining)
    }

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
