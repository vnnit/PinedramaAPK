package com.vnnit.pinedramatv

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnDoneLogin: Button

    private val handler = Handler(Looper.getMainLooper())
    private var isFinished = false

    private val checkCookieRunnable = object : Runnable {
        override fun run() {
            if (isFinished || isFinishing) return
            val cm = CookieManager.getInstance()
            val c1 = cm.getCookie("https://www.tiktok.com") ?: ""
            val c2 = cm.getCookie("https://tiktok.com") ?: ""
            val c3 = cm.getCookie(webView.url ?: "") ?: ""
            val all = "$c1; $c2; $c3"

            if (all.contains("sessionid") || all.contains("sid_tt") || all.contains("uid_tt") || all.contains("passport_auth_status")) {
                cm.flush()
                completeLogin()
                return
            }
            handler.postDelayed(this, 1500)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        webView = findViewById(R.id.loginWebView)
        progressBar = findViewById(R.id.loginProgressBar)
        btnDoneLogin = findViewById(R.id.btnDoneLogin)

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

                val currentUrl = url ?: ""
                val cookies = cookieManager.getCookie(currentUrl) ?: ""
                if (cookies.contains("sessionid") || cookies.contains("sid_tt") || (currentUrl.contains("tiktok.com") && !currentUrl.contains("/login"))) {
                    completeLogin()
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val nextUrl = request?.url?.toString() ?: ""
                // bytedance://dispatch_message is TikTok's JS bridge signal confirming successful login!
                if (nextUrl.startsWith("bytedance://") || nextUrl.startsWith("snssdk") || nextUrl.startsWith("tiktok://")) {
                    completeLogin()
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
                if (failingUrl.startsWith("bytedance://")) {
                    completeLogin()
                    return
                }
                super.onReceivedError(view, request, error)
            }
        }

        btnDoneLogin.setOnClickListener {
            completeLogin()
        }

        webView.loadUrl("https://www.tiktok.com/login/qrcode")
        handler.postDelayed(checkCookieRunnable, 2000)
    }

    private fun completeLogin() {
        if (isFinished) return
        isFinished = true
        handler.removeCallbacks(checkCookieRunnable)
        CookieManager.getInstance().flush()

        val prefs = getSharedPreferences("pinedrama_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("tiktok_logged_in", true).apply()

        Toast.makeText(this, "🎉 Đăng nhập TikTok thành công!", Toast.LENGTH_LONG).show()
        setResult(RESULT_OK)
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            completeLogin()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkCookieRunnable)
        CookieManager.getInstance().flush()
        webView.destroy()
    }
}
