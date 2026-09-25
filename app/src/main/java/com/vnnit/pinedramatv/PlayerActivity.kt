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

        TikTokCookieHelper.ensureCookiesSeeded(this)

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
                return false
            }
        }

        fallbackWebView.loadUrl(targetUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun switchToWebView(url: String) {
        playerView.visibility = View.GONE
        fallbackWebView.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE

        TikTokCookieHelper.ensureCookiesSeeded(this)

        fallbackWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

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
                showNotice("📺 Chế độ 7/3 (70% Video + 30% Chọn tập) | Tự động chuyển tập")

                val js = """
                    (function() {
                        window.__setSplitLayout = function(is7030) {
                            let style = document.getElementById('tv-custom-style');
                            if (!style) {
                                style = document.createElement('style');
                                style.id = 'tv-custom-style';
                                document.head.appendChild(style);
                            }

                            const killToastsAndAds = `
                                [class*="inapp-notif"], [class*="Notice"], [class*="notice"], [class*="notif"],
                                [class*="toast"], [class*="Toast"], ol[class*="css-"], li[class*="inapp-notif"],
                                [class*="banner"], [class*="download-bar"], [class*="login-bar"],
                                header, nav, [class*="DivSideNav"], [class*="action-bar"], [class*="ActionBar"] {
                                    display: none !important;
                                    visibility: hidden !important;
                                    opacity: 0 !important;
                                    pointer-events: none !important;
                                    height: 0 !important;
                                    overflow: hidden !important;
                                }
                                *, *::before, *::after {
                                    backdrop-filter: none !important;
                                    filter: none !important;
                                    box-shadow: none !important;
                                    animation: none !important;
                                }
                                [class*="DivBlurBackground"], [class*="blur"], [class*="Blur"] {
                                    display: none !important;
                                }
                                body, html {
                                    background: #000 !important;
                                    overflow: hidden !important;
                                    margin: 0 !important;
                                    padding: 0 !important;
                                    width: 100vw !important;
                                    height: 100vh !important;
                                }
                            `;

                            if (is7030) {
                                style.innerHTML = killToastsAndAds + `
                                    /* 70% VIDEO ON LEFT */
                                    [class*="DivLeftContainer"], [class*="DivVideoDetailContainer"], [class*="DivColumnListContainer"], 
                                    [class*="ArticleItemContainer"], [class*="DivContentFlexLayout"], [class*="SectionMediaCardContainer"], 
                                    [class*="BasePlayerContainer"], [class*="DivVideoContainer"], [class*="video-container"], 
                                    [class*="DivVideoWrapper"], [class*="DivPlayerContainer"], .xgplayer-container, [class*="xgplayer-container"] {
                                        position: fixed !important;
                                        top: 0 !important;
                                        left: 0 !important;
                                        width: 70vw !important;
                                        max-width: 70vw !important;
                                        height: 100vh !important;
                                        max-height: 100vh !important;
                                        z-index: 9999 !important;
                                        background: #000 !important;
                                        display: flex !important;
                                        align-items: center !important;
                                        justify-content: center !important;
                                        margin: 0 !important;
                                        padding: 0 !important;
                                    }
                                    video {
                                        position: fixed !important;
                                        top: 0 !important;
                                        left: 0 !important;
                                        width: 70vw !important;
                                        max-width: 70vw !important;
                                        height: 100vh !important;
                                        max-height: 100vh !important;
                                        object-fit: contain !important;
                                        background: #000 !important;
                                        z-index: 99999 !important;
                                        transform: translateZ(0) !important;
                                    }
                                    /* 30% EPISODE LIST ON RIGHT */
                                    [class*="RightPanelContainer"], [class*="DivShortDramaDetailRoot"] {
                                        display: block !important;
                                        position: fixed !important;
                                        top: 0 !important;
                                        right: 0 !important;
                                        width: 30vw !important;
                                        max-width: 30vw !important;
                                        height: 100vh !important;
                                        max-height: 100vh !important;
                                        z-index: 999999 !important;
                                        background: #141414 !important;
                                        border-left: 1px solid #282828 !important;
                                        overflow-y: auto !important;
                                        padding: 16px !important;
                                        box-sizing: border-box !important;
                                    }
                                    [class*="DivEpisodeGrid"] {
                                        display: grid !important;
                                        grid-template-columns: repeat(4, 1fr) !important;
                                        gap: 8px !important;
                                    }
                                    [class*="ButtonEpisode"] {
                                        font-size: 18px !important;
                                        font-weight: bold !important;
                                        height: 48px !important;
                                        border-radius: 8px !important;
                                    }
                                `;
                            } else {
                                style.innerHTML = killToastsAndAds + `
                                    /* 100% FULLSCREEN VIDEO */
                                    [class*="RightPanelContainer"], [class*="DivShortDramaDetailRoot"] {
                                        display: none !important;
                                    }
                                    [class*="DivLeftContainer"], [class*="DivVideoDetailContainer"], [class*="DivColumnListContainer"], 
                                    [class*="ArticleItemContainer"], [class*="DivContentFlexLayout"], [class*="SectionMediaCardContainer"], 
                                    [class*="BasePlayerContainer"], [class*="DivVideoContainer"], [class*="video-container"], 
                                    [class*="DivVideoWrapper"], [class*="DivPlayerContainer"], .xgplayer-container, [class*="xgplayer-container"] {
                                        position: fixed !important;
                                        top: 0 !important;
                                        left: 0 !important;
                                        width: 100vw !important;
                                        max-width: 100vw !important;
                                        height: 100vh !important;
                                        max-height: 100vh !important;
                                        z-index: 9999 !important;
                                        background: #000 !important;
                                        display: flex !important;
                                        align-items: center !important;
                                        justify-content: center !important;
                                    }
                                    video {
                                        position: fixed !important;
                                        top: 0 !important;
                                        left: 0 !important;
                                        width: 100vw !important;
                                        max-width: 100vw !important;
                                        height: 100vh !important;
                                        max-height: 100vh !important;
                                        object-fit: contain !important;
                                        background: #000 !important;
                                        z-index: 99999 !important;
                                        transform: translateZ(0) !important;
                                    }
                                `;
                            }
                        };

                        window.__playNextEpisode = function() {
                            const nextBtn = document.querySelector('[data-e2e="arrow-right"]') || 
                                            document.querySelector('button[aria-label*="Next"]') || 
                                            document.querySelector('[class*="arrow-right"]') ||
                                            document.querySelector('[class*="next"]');
                            if (nextBtn) {
                                nextBtn.click();
                                return;
                            }
                            const epButtons = Array.from(document.querySelectorAll('[class*="ButtonEpisode"]'));
                            if (epButtons.length > 0) {
                                let currentIdx = epButtons.findIndex(b => {
                                    return b.querySelector('svg, span[class*="icon"]') !== null ||
                                           b.className.includes('active') ||
                                           b.className.includes('selected') ||
                                           window.getComputedStyle(b).backgroundColor.includes('234');
                                });
                                if (currentIdx !== -1 && currentIdx + 1 < epButtons.length) {
                                    epButtons[currentIdx + 1].click();
                                    return;
                                }
                            }
                            window.dispatchEvent(new KeyboardEvent('keydown', {'key': 'ArrowDown', 'keyCode': 40, 'bubbles': true}));
                        };

                        window.__playPrevEpisode = function() {
                            const prevBtn = document.querySelector('[data-e2e="arrow-left"]') || 
                                            document.querySelector('button[aria-label*="Previous"]') || 
                                            document.querySelector('[class*="arrow-left"]') ||
                                            document.querySelector('[class*="prev"]');
                            if (prevBtn) {
                                prevBtn.click();
                                return;
                            }
                            const epButtons = Array.from(document.querySelectorAll('[class*="ButtonEpisode"]'));
                            if (epButtons.length > 0) {
                                let currentIdx = epButtons.findIndex(b => {
                                    return b.querySelector('svg, span[class*="icon"]') !== null ||
                                           b.className.includes('active') ||
                                           b.className.includes('selected') ||
                                           window.getComputedStyle(b).backgroundColor.includes('234');
                                });
                                if (currentIdx > 0) {
                                    epButtons[currentIdx - 1].click();
                                    return;
                                }
                            }
                            window.dispatchEvent(new KeyboardEvent('keydown', {'key': 'ArrowUp', 'keyCode': 38, 'bubbles': true}));
                        };

                        window.__killLagAndAutoplay = function() {
                            // 1. Remove all notification toasts and popups immediately
                            document.querySelectorAll('[class*="inapp-notif"], ol[class*="css-"], [class*="Notice"], li[class*="inapp-notif"]').forEach(el => el.remove());

                            // 2. Remove duplicate background video streams
                            const videos = document.querySelectorAll('video');
                            if (videos.length > 1) {
                                for (let i = 1; i < videos.length; i++) {
                                    try {
                                        videos[i].pause();
                                        videos[i].src = '';
                                        videos[i].parentElement?.removeChild(videos[i]);
                                    } catch(e) {}
                                }
                            }

                            // 3. Play main video and attach auto-next listener
                            const mainVideo = document.querySelector('video');
                            if (mainVideo) {
                                if (mainVideo.paused) {
                                    mainVideo.muted = false;
                                    mainVideo.play().catch(() => {
                                        mainVideo.muted = true;
                                        mainVideo.play();
                                    });
                                }

                                if (!mainVideo.__autoNextBound) {
                                    mainVideo.__autoNextBound = true;
                                    mainVideo.addEventListener('ended', function() {
                                        console.log("Video ended, auto-next episode...");
                                        window.__playNextEpisode();
                                    });
                                    mainVideo.addEventListener('timeupdate', function() {
                                        if (mainVideo.duration && mainVideo.currentTime > 0 && (mainVideo.duration - mainVideo.currentTime <= 0.35)) {
                                            setTimeout(function() {
                                                if (mainVideo.ended || mainVideo.paused) {
                                                    window.__playNextEpisode();
                                                }
                                            }, 500);
                                        }
                                    });
                                }
                            }

                            // 4. Remove giant play button overlays & tooltips
                            const playIcons = document.querySelectorAll('[class*="play"], [class*="Play"]');
                            playIcons.forEach(btn => {
                                if (btn.tagName !== 'VIDEO' && btn.querySelector('video') === null && btn.clientHeight > 80 && btn.clientHeight < 400) {
                                    btn.style.display = 'none';
                                }
                            });
                            const tooltips = Array.from(document.querySelectorAll('div, span, p')).filter(el => {
                                const t = (el.innerText || '').toLowerCase();
                                return t.includes('toàn màn hình') || t.includes('fullscreen');
                            });
                            tooltips.forEach(t => {
                                if (t.clientHeight > 0 && t.clientHeight < 80) {
                                    t.style.display = 'none';
                                }
                            });
                        };

                        // Initialize 70/30 split layout by default
                        window.__setSplitLayout(true);
                        window.__killLagAndAutoplay();

                        if (!window.__lagInterval) {
                            window.__lagInterval = setInterval(window.__killLagAndAutoplay, 1200);
                        }
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
                return false
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

    private var is7030Layout = true
    private var isVirtualMouseActive = false

    private fun toggleLayoutAndMouse() {
        if (!isVirtualMouseActive) {
            // Step 1: Enable virtual mouse in 70/30 mode so user can pick episodes freely
            isVirtualMouseActive = true
            virtualMouseHelper.setMouseEnabled(true)
            showNotice("🖱️ Chuột ảo BẬT: Dùng phím điều hướng để chọn tập")
        } else {
            // Step 2: Toggle 100% fullscreen vs 70/30
            is7030Layout = !is7030Layout
            isVirtualMouseActive = false
            virtualMouseHelper.setMouseEnabled(false)
            fallbackWebView.evaluateJavascript("if (window.__setSplitLayout) { window.__setSplitLayout($is7030Layout); }", null)
            showNotice(if (is7030Layout) "📺 Chế độ 7/3 (70% Video + 30% Chọn tập)" else "🎬 Chế độ 100% Toàn màn hình")
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode

        // Toggle Episode Drawer and Virtual Mouse on Menu key or Info key or Settings key
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_INFO || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                toggleLayoutAndMouse()
            }
            return true
        }

        if (fallbackWebView.visibility == View.VISIBLE) {
            // When virtual mouse is active, handle D-pad for cursor and OK for click
            if (isVirtualMouseActive && virtualMouseHelper.isMouseDpadKey(keyCode)) {
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
                        fallbackWebView.evaluateJavascript("if (window.__playNextEpisode) { window.__playNextEpisode(); } if (window.__killLagAndAutoplay) setTimeout(window.__killLagAndAutoplay, 800);", null)
                        showNotice("Tập tiếp theo ▶")
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        fallbackWebView.evaluateJavascript("if (window.__playPrevEpisode) { window.__playPrevEpisode(); } if (window.__killLagAndAutoplay) setTimeout(window.__killLagAndAutoplay, 800);", null)
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
