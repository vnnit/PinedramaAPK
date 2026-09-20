package com.vnnit.pinedramatv

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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

    private lateinit var playerView: PlayerView
    private lateinit var fallbackWebView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvOverlayNotice: TextView

    private var exoPlayer: ExoPlayer? = null
    private var videoUrl: String = ""
    private var currentResizeModeIndex = 0
    private val resizeModes = listOf(
        Pair(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Vừa màn hình (FIT)"),
        Pair(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Thu phóng lấp đầy (ZOOM)"),
        Pair(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Kéo giãn toàn màn hình (STRETCH)")
    )

    private val handler = Handler(Looper.getMainLooper())
    private val hideNoticeRunnable = Runnable {
        tvOverlayNotice.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        fallbackWebView = findViewById(R.id.fallbackWebView)
        progressBar = findViewById(R.id.progressBar)
        tvOverlayNotice = findViewById(R.id.tvOverlayNotice)

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
        progressBar.visibility = View.VISIBLE

        if (isDirectStreamUrl(url)) {
            playWithExoPlayer(url)
        } else {
            // For general web links (TikTok, PineDrama, etc.)
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
                    if (!lower.contains("googlesyndication") && !lower.contains("doubleclick")) {
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
                view?.loadUrl(next)
                return true
            }
        }

        fallbackWebView.loadUrl(targetUrl)
    }

    private fun switchToWebView(url: String) {
        playerView.visibility = View.GONE
        fallbackWebView.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        showNotice("Đang hiển thị trang web video")
    }

    private fun showNotice(text: String) {
        tvOverlayNotice.text = text
        tvOverlayNotice.visibility = View.VISIBLE
        handler.removeCallbacks(hideNoticeRunnable)
        handler.postDelayed(hideNoticeRunnable, 2500)
    }

    private fun toggleAspectRatio() {
        currentResizeModeIndex = (currentResizeModeIndex + 1) % resizeModes.size
        val (mode, label) = resizeModes[currentResizeModeIndex]
        playerView.resizeMode = mode
        showNotice("Tỉ lệ hình ảnh: $label")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
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
                if (fallbackWebView.visibility == View.VISIBLE && fallbackWebView.canGoBack()) {
                    fallbackWebView.goBack()
                    return true
                }
                finish()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
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
