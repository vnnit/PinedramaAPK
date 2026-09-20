package com.vnnit.pinedramatv

import android.content.Intent
import android.os.Bundle
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
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvVersion: TextView
    private lateinit var btnCheckUpdate: Button

    private var localServer: LocalLinkServer? = null
    private lateinit var historyManager: HistoryManager
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupHistory()
        setupServerAndQR()
        setupListeners()

        // Check for updates from GitHub Releases on launch
        GitHubUpdateChecker.checkForUpdate(this, showToastIfLatest = false)
    }

    private fun initViews() {
        ivQrCode = findViewById(R.id.ivQrCode)
        tvServerUrl = findViewById(R.id.tvServerUrl)
        tvStatus = findViewById(R.id.tvStatus)
        etVideoUrl = findViewById(R.id.etVideoUrl)
        btnPlay = findViewById(R.id.btnPlay)
        rvHistory = findViewById(R.id.rvHistory)
        tvVersion = findViewById(R.id.tvVersion)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)

        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
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

        // Generate and set QR code
        val qrBitmap = QRCodeUtil.generateQRCode(serverUrl, 512)
        if (qrBitmap != null) {
            ivQrCode.setImageBitmap(qrBitmap)
        }

        // Start embedded HTTP server
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

        btnCheckUpdate.setOnClickListener {
            Toast.makeText(this, "Đang kiểm tra bản cập nhật mới...", Toast.LENGTH_SHORT).show()
            GitHubUpdateChecker.checkForUpdate(this, showToastIfLatest = true)
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
    }

    override fun onDestroy() {
        super.onDestroy()
        localServer?.stop()
        localServer = null
    }
}
