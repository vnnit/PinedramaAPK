package com.vnnit.pinedramatv

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var rootLayout: ViewGroup
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnDoneLogin: Button
    private lateinit var ivVirtualCursor: ImageView

    private lateinit var virtualMouseHelper: VirtualMouseHelper
    private var isLoginCompleted = false
    private val handler = Handler(Looper.getMainLooper())

    private val checkLoginRunnable = object : Runnable {
        override fun run() {
            if (isLoginCompleted || isFinishing) return

            val cookies = CookieManager.getInstance().getCookie("https://www.tiktok.com") ?: ""
            val currentUrl = webView.url ?: ""

            val hasSessionCookie = cookies.contains("sessionid=") ||
                    cookies.contains("sessionid_ss=") ||
                    cookies.contains("sid_guard=")

            val isHomeFeed = currentUrl.contains("tiktok.com") &&
                    !currentUrl.contains("login/qrcode") &&
                    !currentUrl.contains("/login") &&
                    !currentUrl.contains("about:blank")

            if (hasSessionCookie || isHomeFeed) {
                isLoginCompleted = true
                Toast.makeText(this@LoginActivity, "🎉 Phát hiện đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                handler.postDelayed({
                    completeLogin()
                }, 1000)
                return
            }

            handler.postDelayed(this, 1200)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        rootLayout = findViewById(R.id.loginRootLayout)
        webView = findViewById(R.id.loginWebView)
        progressBar = findViewById(R.id.loginProgressBar)
        btnDoneLogin = findViewById(R.id.btnDoneLogin)
        ivVirtualCursor = findViewById(R.id.ivVirtualCursor)

        virtualMouseHelper = VirtualMouseHelper(rootLayout, webView, ivVirtualCursor)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                cookieManager.flush()
                checkLoginRunnable.run()
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                checkLoginRunnable.run()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val nextUrl = request?.url?.toString() ?: ""
                // Ignore custom schemes so WebView doesn't crash with ERR_UNKNOWN_URL_SCHEME
                if (nextUrl.startsWith("bytedance://") || nextUrl.startsWith("snssdk") || nextUrl.startsWith("tiktok://")) {
                    return true
                }
                if (!nextUrl.startsWith("http://") && !nextUrl.startsWith("https://")) {
                    return true
                }
                view?.loadUrl(nextUrl)
                return true
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                val failingUrl = request?.url?.toString() ?: ""
                if (failingUrl.startsWith("bytedance://") || failingUrl.startsWith("snssdk") || failingUrl.startsWith("tiktok://")) {
                    return
                }
                super.onReceivedError(view, request, error)
            }
        }

        btnDoneLogin.setOnClickListener {
            completeLogin()
        }

        webView.loadUrl("https://www.tiktok.com/login/qrcode")
        handler.postDelayed(checkLoginRunnable, 2500)
    }

    private fun completeLogin() {
        if (isFinishing) return
        isLoginCompleted = true
        handler.removeCallbacksAndMessages(null)
        CookieManager.getInstance().flush()

        val prefs = getSharedPreferences("pinedrama_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("tiktok_logged_in", true).apply()

        Toast.makeText(this, "🎉 Đã lưu phiên đăng nhập TikTok!", Toast.LENGTH_LONG).show()
        setResult(RESULT_OK)
        finish()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // First priority: Virtual mouse cursor controls
        if (virtualMouseHelper.isEnabled && virtualMouseHelper.isMouseDpadKey(event.keyCode)) {
            if (virtualMouseHelper.handleKeyEvent(event)) {
                return true
            }
        }

        // Second priority: Remote BACK button completes & saves login immediately
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
            completeLogin()
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        CookieManager.getInstance().flush()
        webView.destroy()
    }
}

