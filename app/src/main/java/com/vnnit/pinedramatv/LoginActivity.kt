package com.vnnit.pinedramatv

import android.annotation.SuppressLint
import android.os.Bundle
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
    }

    private fun completeLogin() {
        CookieManager.getInstance().flush()

        val prefs = getSharedPreferences("pinedrama_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("tiktok_logged_in", true).apply()

        Toast.makeText(this, "🎉 Đã lưu phiên đăng nhập TikTok!", Toast.LENGTH_LONG).show()
        setResult(RESULT_OK)
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            completeLogin()
            return true
        }
        if (virtualMouseHelper.handleKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        CookieManager.getInstance().flush()
        webView.destroy()
    }
}
