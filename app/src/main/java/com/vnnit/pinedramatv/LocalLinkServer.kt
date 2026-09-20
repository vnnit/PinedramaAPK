package com.vnnit.pinedramatv

import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class LocalLinkServer(
    port: Int = 8080,
    private val onUrlReceived: (String) -> Unit
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        if (method == Method.POST && uri == "/play") {
            try {
                val files = HashMap<String, String>()
                session.parseBody(files)
                val params = session.parameters
                var targetUrl = params["url"]?.firstOrNull() ?: ""

                if (targetUrl.isBlank()) {
                    // Try parsing raw post data
                    val postData = files["postData"] ?: ""
                    if (postData.isNotBlank() && postData.contains("url=")) {
                        val parts = postData.split("&")
                        for (part in parts) {
                            if (part.startsWith("url=")) {
                                targetUrl = java.net.URLDecoder.decode(part.substring(4), "UTF-8")
                                break
                            }
                        }
                    }
                }

                if (targetUrl.isNotBlank()) {
                    onUrlReceived(targetUrl.trim())
                    val json = "{\"status\":\"ok\",\"message\":\"Đã gửi lên TV thành công!\"}"
                    return newFixedLengthResponse(Response.Status.OK, "application/json", json)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{\"error\":\"${e.message}\"}")
            }
        }

        // Return HTML Web Interface
        val html = """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <title>Phát video lên Sony TV</title>
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
                    body { background: #121212; color: #ffffff; display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 100vh; padding: 20px; }
                    .card { background: #1E1E1E; border-radius: 16px; padding: 24px; width: 100%; max-width: 480px; box-shadow: 0 8px 24px rgba(0,0,0,0.5); text-align: center; }
                    h1 { color: #00E676; font-size: 24px; margin-bottom: 8px; }
                    p.subtitle { color: #aaaaaa; font-size: 14px; margin-bottom: 24px; }
                    textarea { width: 100%; height: 110px; background: #2A2A2A; border: 2px solid #444; border-radius: 12px; padding: 12px; color: #fff; font-size: 15px; resize: none; outline: none; transition: border-color 0.2s; }
                    textarea:focus { border-color: #00E676; }
                    .btn-group { display: flex; gap: 10px; margin-top: 16px; }
                    button { flex: 1; height: 50px; border: none; border-radius: 12px; font-size: 16px; font-weight: bold; cursor: pointer; transition: transform 0.1s, opacity 0.2s; }
                    button:active { transform: scale(0.98); }
                    .btn-primary { background: #00E676; color: #121212; }
                    .btn-secondary { background: #333333; color: #ffffff; }
                    #msg { margin-top: 18px; font-size: 15px; font-weight: 500; min-height: 24px; }
                    .success { color: #00E676; }
                    .error { color: #FF5252; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h1>🎬 PineDrama TV Sender</h1>
                    <p class="subtitle">Dán link video bạn thích để phát ngay lên TV</p>
                    
                    <textarea id="urlInput" placeholder="Dán link video (TikTok, PineDrama, link web, mp4, m3u8...)..."></textarea>
                    
                    <div class="btn-group">
                        <button class="btn-secondary" onclick="pasteClipboard()">Dán từ Clipboard</button>
                        <button class="btn-primary" onclick="sendToTV()">🚀 Phát trên TV</button>
                    </div>

                    <div id="msg"></div>
                </div>

                <script>
                    async function pasteClipboard() {
                        try {
                            const text = await navigator.clipboard.readText();
                            if (text) {
                                document.getElementById('urlInput').value = text;
                                showMsg('Đã dán link!', 'success');
                            }
                        } catch(e) {
                            showMsg('Hãy dán thủ công vào ô nhập!', 'error');
                        }
                    }

                    async function sendToTV() {
                        const url = document.getElementById('urlInput').value.trim();
                        if (!url) {
                            showMsg('Vui lòng dán link video!', 'error');
                            return;
                        }

                        showMsg('Đang gửi lên TV...', '');
                        try {
                            const formData = new URLSearchParams();
                            formData.append('url', url);

                            const res = await fetch('/play', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                                body: formData.toString()
                            });

                            const data = await res.json();
                            if (data.status === 'ok') {
                                showMsg('✅ Video đã được mở trên TV!', 'success');
                            } else {
                                showMsg('❌ Lỗi: ' + (data.error || 'Không gửi được'), 'error');
                            }
                        } catch(e) {
                            showMsg('❌ Không thể kết nối đến TV. Hãy kiểm tra chung Wifi!', 'error');
                        }
                    }

                    function showMsg(text, type) {
                        const el = document.getElementById('msg');
                        el.innerText = text;
                        el.className = type;
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", html)
    }
}
