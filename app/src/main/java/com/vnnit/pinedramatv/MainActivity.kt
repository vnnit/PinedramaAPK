package com.vnnit.pinedramatv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var ivQrCode: ImageView
    private lateinit var tvServerUrl: TextView
    private lateinit var tvStatus: TextView
    private lateinit var etVideoUrl: EditText
    private lateinit var btnPlay: Button
    private lateinit var btnLoginTikTok: Button
    private lateinit var tvLoginStatus: TextView
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvVersion: TextView
    private lateinit var btnCheckUpdate: Button

    private var localServer: LocalLinkServer? = null
    private lateinit var historyManager: HistoryManager
    private lateinit var historyAdapter: HistoryAdapter

    companion object {
        private const val REQUEST_CODE_LOGIN = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupHistory()
        setupServerAndQR()
        setupListeners()
        updateLoginStatus()

        // Check for updates from GitHub Releases on launch
        GitHubUpdateChecker.checkForUpdate(this, showToastIfLatest = false)
    }

    private fun initViews() {
        ivQrCode = findViewById(R.id.ivQrCode)
        tvServerUrl = findViewById(R.id.tvServerUrl)
        tvStatus = findViewById(R.id.tvStatus)
        etVideoUrl = findViewById(R.id.etVideoUrl)
        btnPlay = findViewById(R.id.btnPlay)
        btnLoginTikTok = findViewById(R.id.btnLoginTikTok)
        tvLoginStatus = findViewById(R.id.tvLoginStatus)
        rvHistory = findViewById(R.id.rvHistory)
        tvVersion = findViewById(R.id.tvVersion)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.1.0"
        } catch (e: Exception) {
            "1.1.0"
        }
        tvVersion.text = getString(R.string.version_label, versionName)
    }

    private fun setupHistory() {
        historyManager = HistoryManager(this)
        historyAdapter = HistoryAdapter(historyManager.getHistory()) { url ->
            playVideo(url)
        }
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = historyAdapter
    }

    private fun setupServerAndQR() {
        val ip = NetworkUtil.getLocalIpAddress()
        val serverUrl = "http://$ip:8080"
        tvServerUrl.text = serverUrl

        val qrBitmap = QRCodeUtil.generateQRCode(serverUrl, 512)
        if (qrBitmap != null) {
            ivQrCode.setImageBitmap(qrBitmap)
        }

        try {
            localServer = LocalLinkServer(8080) { receivedUrl ->
                runOnUiThread {
                    Toast.makeText(this, "Đã nhận video từ điện thoại!", Toast.LENGTH_SHORT).show()
                    playVideo(receivedUrl)
                }
            }
            localServer?.start()
            tvStatus.text = getString(R.string.status_listening)
        } catch (e: Exception) {
            e.printStackTrace()
            tvStatus.text = "Lỗi khởi động máy chủ: ${e.message}"
        }
    }

    private fun setupListeners() {
        btnPlay.setOnClickListener {
            val url = etVideoUrl.text.toString().trim()
            if (url.isNotBlank()) {
                playVideo(url)
            } else {
                Toast.makeText(this, "Vui lòng nhập hoặc dán đường link video!", Toast.LENGTH_SHORT).show()
            }
        }

        btnLoginTikTok.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE_LOGIN)
        }

        btnCheckUpdate.setOnClickListener {
            Toast.makeText(this, "Đang kiểm tra bản cập nhật mới...", Toast.LENGTH_SHORT).show()
            GitHubUpdateChecker.checkForUpdate(this, showToastIfLatest = true)
        }
    }

    private fun updateLoginStatus() {
        val cookies = CookieManager.getInstance().getCookie("https://www.tiktok.com") ?: ""
        if (cookies.contains("sessionid=") || cookies.contains("sid_guard=")) {
            tvLoginStatus.text = "Trạng thái: ✅ Đã đăng nhập TikTok (Xem trọn bộ danh sách tập)"
            tvLoginStatus.setTextColor(getColor(R.color.primary))
            btnLoginTikTok.text = "Đăng xuất"
            btnLoginTikTok.setOnClickListener {
                CookieManager.getInstance().removeAllCookies {
                    CookieManager.getInstance().flush()
                    updateLoginStatus()
                    setupListeners()
                    Toast.makeText(this, "Đã đăng xuất tài khoản", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            tvLoginStatus.text = "Trạng thái: ⚠️ Chưa đăng nhập TikTok (Cần để mở khóa danh sách tập)"
            tvLoginStatus.setTextColor(getColor(R.color.text_secondary))
            btnLoginTikTok.text = "🔐 Đăng nhập TikTok"
            btnLoginTikTok.setOnClickListener {
                val intent = Intent(this, LoginActivity::class.java)
                startActivityForResult(intent, REQUEST_CODE_LOGIN)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_LOGIN) {
            updateLoginStatus()
        }
    }

    private fun playVideo(url: String) {
        historyManager.addUrl(url)
        historyAdapter.updateItems(historyManager.getHistory())

        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("EXTRA_URL", url)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        historyAdapter.updateItems(historyManager.getHistory())
        updateLoginStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        localServer?.stop()
        localServer = null
    }
}
