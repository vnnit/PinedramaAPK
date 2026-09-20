package com.vnnit.pinedramatv

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String?,
    @SerializedName("body") val body: String?,
    @SerializedName("assets") val assets: List<GitHubAsset>?
)

data class GitHubAsset(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("size") val size: Long
)

object GitHubUpdateChecker {
    private const val GITHUB_REPO = "vnnit/PinedramaAPK"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases"
    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun checkForUpdate(context: Context, showToastIfLatest: Boolean = false) {
        val request = Request.Builder()
            .url(API_URL)
            .header("Accept", "application/vnd.github+json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (showToastIfLatest) {
                    mainHandler.post {
                        Toast.makeText(context, "Lỗi kiểm tra cập nhật: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    if (showToastIfLatest) {
                        mainHandler.post {
                            Toast.makeText(context, "Không thể kiểm tra cập nhật (HTTP ${response.code})", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return
                }

                val bodyStr = response.body?.string() ?: return
                try {
                    val listType = object : com.google.gson.reflect.TypeToken<List<GitHubRelease>>() {}.type
                    val releases: List<GitHubRelease> = Gson().fromJson(bodyStr, listType)

                    // Find latest TV release (tag contains -tv or v1.*)
                    val tvRelease = releases.firstOrNull { it.tagName.contains("-tv", ignoreCase = true) }
                        ?: releases.firstOrNull()

                    if (tvRelease == null) return

                    val currentVersion = getAppVersionName(context)
                    val apkAsset = tvRelease.assets?.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

                    if (isNewerVersion(tvRelease.tagName, currentVersion) && apkAsset != null) {
                        mainHandler.post {
                            showUpdateDialog(context, tvRelease, apkAsset)
                        }
                    } else if (showToastIfLatest) {
                        mainHandler.post {
                            Toast.makeText(context, "Bạn đang dùng phiên bản mới nhất ($currentVersion)", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        })
    }

    private fun extractVersionNumbers(versionStr: String): List<Int> {
        val regex = Regex("""(\d+(\.\d+)+)""")
        val match = regex.find(versionStr)?.value ?: versionStr
        return match.split(".").map { it.toIntOrNull() ?: 0 }
    }

    private fun isNewerVersion(latestTag: String, currentVersion: String): Boolean {
        try {
            val latestParts = extractVersionNumbers(latestTag)
            val currentParts = extractVersionNumbers(currentVersion)
            val length = maxOf(latestParts.size, currentParts.size)

            for (i in 0 until length) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
        } catch (e: Exception) {
            return latestTag != currentVersion
        }
        return false
    }

    private fun showUpdateDialog(context: Context, release: GitHubRelease, asset: GitHubAsset) {
        val notes = release.body?.takeIf { it.isNotBlank() } ?: "Có bản cập nhật mới cho PineDrama TV."

        AlertDialog.Builder(context)
            .setTitle("Đã có bản cập nhật mới (${release.tagName})")
            .setMessage(notes)
            .setPositiveButton("Cập nhật ngay") { _, _ ->
                downloadAndInstall(context, asset.downloadUrl)
            }
            .setNegativeButton("Để sau", null)
            .show()
    }

    private fun downloadAndInstall(context: Context, downloadUrl: String) {
        val progressDialog = ProgressDialog(context).apply {
            setTitle("Đang tải bản cập nhật...")
            setMessage("Vui lòng đợi giây lát...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCancelable(false)
            show()
        }

        val request = Request.Builder().url(downloadUrl).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    progressDialog.dismiss()
                    Toast.makeText(context, "Tải cập nhật thất bại: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    mainHandler.post {
                        progressDialog.dismiss()
                        Toast.makeText(context, "Lỗi tải file (HTTP ${response.code})", Toast.LENGTH_LONG).show()
                    }
                    return
                }

                val body = response.body ?: return
                val totalLength = body.contentLength()

                val apkFile = File(context.externalCacheDir ?: context.cacheDir, "update.apk")
                if (apkFile.exists()) apkFile.delete()

                try {
                    val input = body.byteStream()
                    val output = FileOutputStream(apkFile)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalDownloaded: Long = 0

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead
                        if (totalLength > 0) {
                            val progress = ((totalDownloaded * 100) / totalLength).toInt()
                            mainHandler.post {
                                progressDialog.progress = progress
                            }
                        }
                    }

                    output.flush()
                    output.close()
                    input.close()

                    mainHandler.post {
                        progressDialog.dismiss()
                        installApk(context, apkFile)
                    }
                } catch (e: Exception) {
                    mainHandler.post {
                        progressDialog.dismiss()
                        Toast.makeText(context, "Lỗi lưu file: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun installApk(context: Context, apkFile: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Không thể mở cài đặt: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getAppVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }
}
