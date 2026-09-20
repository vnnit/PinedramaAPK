# 🎬 PineDrama TV (Web-to-TV Video Player)

Ứng dụng xem video phim ngắn (PineDrama, TikTok, web video, m3u8, mp4) dành riêng cho **Android TV / Google TV / Sony Smart TV**.

## 🌟 Tính năng nổi bật

1. 📱 **Quét mã QR từ điện thoại:**
   - Mở app trên TV, màn hình sẽ hiển thị mã QR và địa chỉ IP nội bộ (ví dụ: `http://192.168.1.15:8080`).
   - Cầm điện thoại (iPhone hoặc Android) quét mã QR $\rightarrow$ Mở trang web điều khiển.
   - Dán link video bạn muốn xem $\rightarrow$ Bấm **"Phát trên TV"** $\rightarrow$ TV tự động phát video full màn hình!

2. 📺 **Tối ưu 100% cho Remote TV:**
   - **Phím OK / Center**: Tạm dừng (Pause) / Tiếp tục phát (Play).
   - **Phím Trái / Phải**: Tua lại -10 giây / Tua tới +10 giây.
   - **Phím Lên / Xuống**: Thay đổi tỉ lệ hiển thị video (Vừa màn hình `FIT` / Thu phóng lấp đầy `ZOOM` / Kéo giãn toàn màn hình `STRETCH`).
   - **Phím Quay lại (Back)**: Trở về màn hình chính.

3. 🔄 **Tự động cập nhật (GitHub Auto-Updater):**
   - App tự động kiểm tra bản cập nhật mới nhất từ GitHub Releases (`vnnit/PinedramaAPK`).
   - Khi có bản mới, TV sẽ hiển thị thông báo và tự tải về cập nhật ngay trên màn hình.

4. 🚀 **Tự động biên dịch (CI/CD với GitHub Actions):**
   - Mỗi lần push code mới lên nhánh `main`, GitHub Actions sẽ tự động build file APK và phát hành lên mục **Releases**.
