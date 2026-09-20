package com.vnnit.pinedramatv

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {

    private lateinit var rootLayout: ViewGroup
    private lateinit var playerView: PlayerView
    private lateinit var fallbackWebView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvOverlayNotice: TextView
    private lateinit var ivPlayerCursor: ImageView

    private var exoPlayer: ExoPlayer? = null
    private var videoUrl: String = ""
    private var currentResizeModeIndex = 0
    private val resizeModes = listOf(
        Pair(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Vừa màn hình (FIT)"),
        Pair(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Thu phóng lấp đầy (ZOOM)"),
        Pair(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Kéo giãn toàn màn hình (STRETCH)")
    )

    private lateinit var virtualMouseHelper: VirtualMouseHelper
    private var isMouseMode = false

    private val handler = Handler(Looper.getMainLooper())
    private val hideNoticeRunnable = Runnable {
        tvOverlayNotice.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        rootLayout = findViewById(R.id.playerRootLayout)
        playerView = findViewById(R.id.playerView)
        fallbackWebView = findViewById(R.id.fallbackWebView)
        progressBar = findViewById(R.id.progressBar)
        tvOverlayNotice = findViewById(R.id.tvOverlayNotice)
        ivPlayerCursor = findViewById(R.id.ivPlayerCursor)

        virtualMouseHelper = VirtualMouseHelper(rootLayout, fallbackWebView, ivPlayerCursor).apply {
            setMouseEnabled(false)
        }

        videoUrl = intent.getStringExtra("EXTRA_URL") ?: ""
        if (videoUrl.isBlank()) {
            Toast.makeText(this, "Không có đường link video!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializePlayer()
        loadContent(videoUrl)
    }

    private fun initializePlayer() {
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            playerView.player = this
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> progressBar.visibility = View.VISIBLE
                        Player.STATE_READY -> progressBar.visibility = View.GONE
                        Player.STATE_ENDED -> progressBar.visibility = View.GONE
                        Player.STATE_IDLE -> Unit
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    progressBar.visibility = View.GONE
                    switchToWebView(videoUrl)
                }
            })
        }
    }

    private fun loadContent(url: String) {
        val lower = url.lowercase()

        if (lower.contains("tiktok.com") || lower.contains("pinedrama")) {
            switchToWebView(url)
        } else if (isDirectStreamUrl(url)) {
            playWithExoPlayer(url)
        } else {
            setupWebViewSniffer(url)
        }
    }

    private fun isDirectStreamUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".m3u8") || lower.endsWith(".webm") ||
                lower.endsWith(".m4v") || lower.contains(".m3u8?") || lower.contains(".mp4?")
    }

    private fun playWithExoPlayer(streamUrl: String) {
        runOnUiThread {
            fallbackWebView.visibility = View.GONE
            playerView.visibility = View.VISIBLE
            val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
            exoPlayer?.playWhenReady = true
            showNotice("Đang phát: ${resizeModes[currentResizeModeIndex].second}")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewSniffer(targetUrl: String) {
        var videoStreamFound = false

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(fallbackWebView, true)

        fallbackWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        fallbackWebView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = view
                resultMsg?.sendToTarget()
                return true
            }
        }

        fallbackWebView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val reqUrl = request?.url?.toString() ?: ""
                val lower = reqUrl.lowercase()
                if (!videoStreamFound && (lower.contains(".m3u8") || lower.contains(".mp4"))) {
                    if (!lower.contains("googlesyndication") && !lower.contains("doubleclick") && !lower.contains("logo")) {
                        videoStreamFound = true
                        handler.post {
                            playWithExoPlayer(reqUrl)
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                cookieManager.flush()
                handler.postDelayed({
                    if (!videoStreamFound && !isFinishing) {
                        progressBar.visibility = View.GONE
                        switchToWebView(url ?: targetUrl)
                    }
                }, 3000)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val next = request?.url?.toString() ?: ""
                if (next.startsWith("bytedance://") || next.startsWith("snssdk") || next.startsWith("tiktok://")) {
                    return true
                }
                if (!next.startsWith("http://") && !next.startsWith("https://")) {
                    return true
                }
                view?.loadUrl(next)
                return true
            }
        }

        fallbackWebView.loadUrl(targetUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun switchToWebView(url: String) {
        playerView.visibility = View.GONE
        fallbackWebView.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(fallbackWebView, true)

        fallbackWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        fallbackWebView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = view
                resultMsg?.sendToTarget()
                return true
            }
        }

        fallbackWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                super.onPageFinished(view, finishedUrl)
                progressBar.visibility = View.GONE
                cookieManager.flush()
                showNotice("💡 Bấm phím Menu để bật chuột ảo, Lên/Xuống chuyển tập")

                val js = """
                    (function() {
                        const style = document.createElement('style');
                        style.innerHTML = `
                            header, [class*="download-bar"], [class*="banner"], [class*="login-bar"] {
                                display: none !important;
                            }
                            body { background: #000 !important; }
                        `;
                        document.head.appendChild(style);

                        const tryPlay = () => {
                            const v = document.querySelector('video');
                            if (v && v.paused) {
                                v.muted = false;
                                v.play().catch(() => {
                                    v.muted = true;
                                    v.play();
                                });
                            }
                        };
                        tryPlay();
                        setTimeout(tryPlay, 1000);
                        setTimeout(tryPlay, 2500);
                    })();
                """.trimIndent()
                view?.evaluateJavascript(js, null)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val next = request?.url?.toString() ?: ""
                if (next.startsWith("bytedance://") || next.startsWith("snssdk") || next.startsWith("tiktok://")) {
                    return true
                }
                if (!next.startsWith("http://") && !next.startsWith("https://")) {
                    return true
                }
                view?.loadUrl(next)
                return true
            }
        }

        fallbackWebView.loadUrl(url)
    }

    private fun showNotice(text: String) {
        tvOverlayNotice.text = text
        tvOverlayNotice.visibility = View.VISIBLE
        handler.removeCallbacks(hideNoticeRunnable)
        handler.postDelayed(hideNoticeRunnable, 3000)
    }

    private fun toggleAspectRatio() {
        currentResizeModeIndex = (currentResizeModeIndex + 1) % resizeModes.size
        val (mode, label) = resizeModes[currentResizeModeIndex]
        playerView.resizeMode = mode
        showNotice("Tỉ lệ hình ảnh: $label")
    }

    private fun toggleMouseMode() {
        isMouseMode = !isMouseMode
        virtualMouseHelper.setMouseEnabled(isMouseMode)
        showNotice(if (isMouseMode) "🖱️ Đã BẬT chuột ảo (Di chuyển bằng D-pad, bấm OK để click)" else "🎮 Đã TẮT chuột ảo (Chế độ điều khiển Media)")
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode

        // Toggle Virtual Mouse on Menu key or Info key or Settings key
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                toggleMouseMode()
            }
            return true
        }

        if (fallbackWebView.visibility == View.VISIBLE) {
            // When mouse mode is active in WebView, handle D-pad for cursor
            if (isMouseMode && virtualMouseHelper.isMouseDpadKey(keyCode)) {
                if (virtualMouseHelper.handleKeyEvent(event)) {
                    return true
                }
            }

            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        fallbackWebView.evaluateJavascript(
                            "const v = document.querySelector('video'); if (v) { if (v.paused) v.play(); else v.pause(); }",
                            null
                        )
                        showNotice("Tạm dừng / Phát tiếp")
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        fallbackWebView.evaluateJavascript(
                            "const v = document.querySelector('video'); if (v) v.currentTime = Math.max(0, v.currentTime - 10);",
                            null
                        )
                        showNotice("Tua lại: -10s")
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        fallbackWebView.evaluateJavascript(
                            "const v = document.querySelector('video'); if (v) v.currentTime = Math.min(v.duration || 9999, v.currentTime + 10);",
                            null
                        )
                        showNotice("Tua tới: +10s")
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        fallbackWebView.evaluateJavascript("""
                            const nextBtn = document.querySelector('[data-e2e="arrow-right"]') || 
                                             document.querySelector('button[aria-label*="Next"]') || 
                                             document.querySelector('[class*="next"]');
                            if (nextBtn) { nextBtn.click(); }
                            else { window.dispatchEvent(new KeyboardEvent('keydown', {'key': 'ArrowDown', 'keyCode': 40, 'bubbles': true})); }
                        """.trimIndent(), null)
                        showNotice("Tập tiếp theo ▶")
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        fallbackWebView.evaluateJavascript("""
                            const prevBtn = document.querySelector('[data-e2e="arrow-left"]') || 
                                             document.querySelector('button[aria-label*="Previous"]') || 
                                             document.querySelector('[class*="prev"]');
                            if (prevBtn) { prevBtn.click(); }
                            else { window.dispatchEvent(new KeyboardEvent('keydown', {'key': 'ArrowUp', 'keyCode': 38, 'bubbles': true})); }
                        """.trimIndent(), null)
                        showNotice("Tập trước ◀")
                        return true
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        if (fallbackWebView.canGoBack()) {
                            fallbackWebView.goBack()
                            return true
                        }
                        finish()
                        return true
                    }
                }
            }
        } else {
            // ExoPlayer controls
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        exoPlayer?.let {
                            if (it.isPlaying) {
                                it.pause()
                                showNotice("Tạm dừng")
                            } else {
                                it.play()
                                showNotice("Tiếp tục phát")
                            }
                            return true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        exoPlayer?.let {
                            val newPos = (it.currentPosition - 10000).coerceAtLeast(0)
                            it.seekTo(newPos)
                            showNotice("Tua lại: -10s")
                            return true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        exoPlayer?.let {
                            val newPos = (it.currentPosition + 10000).coerceAtMost(it.duration)
                            it.seekTo(newPos)
                            showNotice("Tua tới: +10s")
                            return true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        toggleAspectRatio()
                        return true
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        finish()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        exoPlayer?.release()
        exoPlayer = null
        fallbackWebView.destroy()
    }
}
