package com.vnnit.pinedramatv

import android.content.Context
import android.webkit.CookieManager

object TikTokCookieHelper {

    private const val PREFS_NAME = "pinedrama_prefs"
    private const val KEY_SEEDED = "cookies_seeded_v2"
    private const val KEY_LOGGED_IN = "tiktok_logged_in"

    data class CookieItem(
        val name: String,
        val value: String,
        val domain: String,
        val path: String = "/",
        val isSecure: Boolean = false
    )

    private val DEFAULT_COOKIES = listOf(
        CookieItem("delay_guest_mode_vid", "8", ".www.tiktok.com", "/", true),
        CookieItem("oec_lucifer", "0101010071f4112a17e4830142a6421e57a3fecb02fe8e34817468cb9b42bf1c2cbd6ac274c79ec748122d590d8c6ad29417f3a22d8c233e77c05ac23664c8a73a12773d63bdb0ef9b4faacbf4d603", ".tiktok.com", "/", true),
        CookieItem("_ga_R5EYE54KWQ", "GS2.1.s1769049743\$o1\$g1\$t1769050025\$j14\$l0\$h472066425", ".tiktok.com", "/", false),
        CookieItem("ttcsid_C97F14JC77U63IDI7U40", "1776393811145::D3X_FzrNEb0-Z7YlopA7.3.1776396052549.1", ".tiktok.com", "/", false),
        CookieItem("tiktok_webapp_lang", "vi-VN", ".tiktok.com", "/", true),
        CookieItem("_ga_ER02CH5NW5", "GS1.1.1776393996.2.1.1776396120.0.0.570903642", ".tiktok.com", "/", false),
        CookieItem("tta_attr_id", "0.1769050053.7598011926255403015", ".tiktok.com", "/", true),
        CookieItem("tt-target-idc-sign", "PFG0nrVms9ueZQr_9E8nyHMLvC9tjZgLQFTm6TevssCPIyZuR8cBQiRHReEZ5n7uWscGTX2AhMzSADvi9jZMhBaWFIeFUjXxS_uyt7naaEU59xLcAWhWO7eg5R2gkGtW5C5HkQ0fWgpDPmwjjq7aiwLA4kMl_M8Esseh67V3Ha_aAFi69EsWuLrvIKsznXhydrW03CgHfs_9hAyNm61ASshEHyTf_0lAJBf3G-SdFHJ0k_RORRY-Hg3Q6XcBOUMrAh0fxB_E4_tTDBNQtLYSQKpWA9c3vbcQMHbRQsvM8unnBkP2WlJ2IXgkrxUn_VRYBD8Qmu3awUmmkFqMEq4zFceXowH_oK6zSk2gyA0U2fmH9p47bUnZF_zaznIS2z9HfVfb36IUOxEKqhO61HEqL6DZbNjebTZMH9Osbgwd8DuB6kFvHlqwqAy81e5Ux49Q5Psc8_6TmIlEigkg89hkjw-g5Q47Zvf3rFOtUJLd7dT4rJ7mJVRxTkRL6Ty4lfGI", ".tiktok.com", "/", false),
        CookieItem("i18next", "en", "www.tiktok.com", "/", false),
        CookieItem("csrf_session_id", "6f038e9b24929d388cbcf1c90846aecd", "www.tiktok.com", "/", true),
        CookieItem("_ga", "GA1.1.533857810.1747748653", ".tiktok.com", "/", false),
        CookieItem("living_user_id", "327512518301", "www.tiktok.com", "/", false),
        CookieItem("msToken", "EUwjH8u16hHUZ-evMmkDpR0aeKNUpggxG2f2N6tAzua27GCFw1dbChFHvQFYtyec7Oq1NwbMm3QGYjfjrDGKuE4Jh_Frm3b3JPmr6IJxzb6jWWJvsNjNeO80PRYX4Hgb7mHJ4uXGCpDkHl4MakdwihtrgYGxHmlGfQXPVv3a4Mg=", ".tiktok.com", "/", true),
        CookieItem("tt_session_tlb_tag", "sttt%7C5%7Cvv9dqB5vAXi5kxQ6ge71Mv________-6i9DvTI0-oGb7bHhu1m3Vtr_nhhNlgHK07WH8aFguhR0%3D", ".tiktok.com", "/", true),
        CookieItem("sid_guard", "beff5da81e6f0178b993143a81eef532%7C1790319386%7C15552000%7CWed%2C+24-Mar-2027+06%3A56%3A26+GMT", ".tiktok.com", "/", true),
        CookieItem("ttwid", "1%7CN2Hl3TuJzv93pVYjjQz2ViKzgkbAKjH4m1e7OJFvy4s%7C1790319394%7C3f2ff37aa0bc365fa1a1b09412634e256c9a26a419135ed0969ddc8b9e34cfb5", ".tiktok.com", "/", true),
        CookieItem("ttcsid", "1782657200674::8k8LIxtEeA2htOxDyJD5.11.1782657221306.0::1.11718.12769::20617.21.1059.766::0.0.0", ".tiktok.com", "/", false),
        CookieItem("store-country-code-src", "uid", ".tiktok.com", "/", false),
        CookieItem("perf_feed_cache", "{%22expireTimestamp%22:0%2C%22itemIds%22:[%22%22]}", ".www.tiktok.com", "/", true),
        CookieItem("ttwid", "1%7CN2Hl3TuJzv93pVYjjQz2ViKzgkbAKjH4m1e7OJFvy4s%7C1751340877%7Ce9366147cfc50bf825176348399dffe548bf587021514ebc7a17497a4901045f", "www.tiktok.com", "/", true),
        CookieItem("uid_tt", "984b8b732bfbbe42382d8200b104313ba5828ba5c9759f2e2f8ea64f2e47430b", ".tiktok.com", "/", true),
        CookieItem("_ga_HV1FL86553", "GS1.1.1776396101.1.1.1776396120.0.0.958635222", ".tiktok.com", "/", false),
        CookieItem("store-country-sign", "MEIEDIZXTzwqgLWFSr8b-AQgyWl27pAbdMsElqlcg9TEzJO2hmwWCV8n1DjU1ZHRaC0EEOT35p-crJSZVixPc8Pjvv4", ".tiktok.com", "/", false),
        CookieItem("msToken", "WiDNLXdBgZE3l8OWPTWBYiJVPHf1Z2WDU2OBTD5KPM7ErTPZoAzE5z-FSpxD4OBcRl93ro8Muriljpp5fxpsZHBzANwcir6FvwPhS3wgRCoCFLlsyLSbiMeggo7gOqE_NBRkUT7NAjEGyQxsYW6LXc6NVLyxD9RjwZ88LJ3vkKk=", "www.tiktok.com", "/", false),
        CookieItem("d_ticket_ads", "021666a014db88669401b050e24773ef10272", ".tiktok.com", "/", false),
        CookieItem("s_v_web_id", "verify_mu2ttbmd_rqbHZXxm_nh5s_4eZr_AiCp_C7cqXwPK0Xh5", ".tiktok.com", "/", true),
        CookieItem("ttcsid_CMSS13RC77U1PJEFQUB0", "1782657200674::3eGbw4zMy_Hpx3W4SDej.8.1782657221306.1", ".tiktok.com", "/", false),
        CookieItem("tiktok_pc_web_lang", "vi-VN", ".tiktok.com", "/", true),
        CookieItem("store-idc", "alisg", ".tiktok.com", "/", false),
        CookieItem("lang_type", "vi-VN", "www.tiktok.com", "/", false),
        CookieItem("_ga_BZBQ2QHQSP", "GS2.1.s1782657200\$o9\$g1\$t1782657226\$j34\$l0\$h780626723", ".tiktok.com", "/", false),
        CookieItem("ttcsid_D0NEQ93C77UEFAFS947G", "1769049744491::HKWzXnVrc3uYM-4CJPAw.1.1769050025938.1", ".tiktok.com", "/", false),
        CookieItem("_fbp", "fb.1.1757644399943.1080035807", ".tiktok.com", "/", true),
        CookieItem("ssid_ucp_v1", "1.0.1-KDMzYzcwODA4YTIyMWYxODdhYzlkMWNjNDEyMDExZWZkM2VhZTE3NWQKGgiBiJ7MlKrsp14QmrbY1QYYsws4B0D0B0gEEAMaA215MiIgYmVmZjVkYTgxZTZmMDE3OGI5OTMxNDNhODFlZWY1MzIyTgogI9_4k9W95iA-_CcLrrep5Z4DR-JjfNYAzNI_U5qextwSIOIqw_SAzrAHx4NxzGX3ArY0D-r3jVr6HWBrIhcZbzXTGAIiBnRpa3Rvaw", ".tiktok.com", "/", true),
        CookieItem("ttcsid_C97F9QBC77U37LFVJTOG", "1769049744490::GiiCPAJA7Kb00SG8g6mS.1.1769050025934.1", ".tiktok.com", "/", false),
        CookieItem("_gtmeec", "e30%3D", ".tiktok.com", "/", true),
        CookieItem("tiktok_webapp_theme", "dark", ".www.tiktok.com", "/", true),
        CookieItem("_ga_JXFCPNXEGG", "GS1.1.1778378585.1.1.1778378728.0.0.1211967397", ".tiktok.com", "/", false),
        CookieItem("_ga_NBFTJ2P3P3", "GS1.1.1762863026.1.0.1762863033.0.0.313655000", ".tiktok.com", "/", false),
        CookieItem("_ga_Y2RSHPPW88", "GS2.1.s1776393810\$o3\$g1\$t1776396088\$j24\$l0\$h451937399", ".tiktok.com", "/", false),
        CookieItem("_hjSessionUser_6487441", "eyJpZCI6IjRmYzhkY2U4LTAwYmYtNTIwYi05NjljLTNmNzhlZWM3NWE2NyIsImNyZWF0ZWQiOjE3NzYyMjU0MTA4MjEsImV4aXN0aW5nIjp0cnVlfQ==", ".tiktok.com", "/", true),
        CookieItem("_tt_enable_cookie", "1", ".tiktok.com", "/", false),
        CookieItem("_ttp", "3G6wCzaSQbodqE6I8gxf2qVloI7", ".tiktok.com", "/", true),
        CookieItem("cmpl_token", "AgQYAPPd_hfkTtKOcV_2wbVdOPDXg4t0XL-M2WCk1bQ", ".tiktok.com", "/", true),
        CookieItem("d_ticket", "84e6cfc44032bced5edaef2c574ecc0f6124c", ".tiktok.com", "/", false),
        CookieItem("g_state", "{\"i_l\":0,\"i_ll\":1790319316324,\"i_b\":\"4ZR4jNLbWVmy+xahwt3A/9yEEeIJV/hObu4xi66tDG8\",\"i_e\":{\"enable_itp_optimization\":24},\"i_et\":1790319316324}", "www.tiktok.com", "/", false),
        CookieItem("last_login_method", "QRcode", "www.tiktok.com", "/", false),
        CookieItem("multi_sids", "6795845325409453057%3Abeff5da81e6f0178b993143a81eef532", ".tiktok.com", "/", true),
        CookieItem("odin_tt", "d3d6fcd1ec9efcd32018f30d4dc9202978b4ca923fc846f6db65427a2765e108a7ce5c63d44ffa75125792bf7db71e892beb00f0b254ce14ef24e1ab2b30b9b7e73ad82cf14a9a29c8900401f9eef881", ".tiktok.com", "/", false),
        CookieItem("passport_fe_beating_status", "true", ".www.tiktok.com", "/", false),
        CookieItem("sessionid", "beff5da81e6f0178b993143a81eef532", ".tiktok.com", "/", true),
        CookieItem("sessionid_ss", "beff5da81e6f0178b993143a81eef532", ".tiktok.com", "/", true),
        CookieItem("sid_guard_ads", "8b5daecddfa84f88af0b3c24fd732eb5%7C1776395914%7C259200%7CMon%2C+20-Apr-2026+03%3A18%3A34+GMT", ".tiktok.com", "/", true),
        CookieItem("sid_guard_tiktokseller", "885ff2a48d4979161d4d5b78b1f9a445%7C1782657225%7C259198%7CWed%2C+01-Jul-2026+14%3A33%3A43+GMT", ".tiktok.com", "/", true),
        CookieItem("sid_tt", "beff5da81e6f0178b993143a81eef532", ".tiktok.com", "/", true),
        CookieItem("sid_ucp_v1", "1.0.1-KDMzYzcwODA4YTIyMWYxODdhYzlkMWNjNDEyMDExZWZkM2VhZTE3NWQKGgiBiJ7MlKrsp14QmrbY1QYYsws4B0D0B0gEEAMaA215MiIgYmVmZjVkYTgxZTZmMDE3OGI5OTMxNDNhODFlZWY1MzIyTgogI9_4k9W95iA-_CcLrrep5Z4DR-JjfNYAzNI_U5qextwSIOIqw_SAzrAHx4NxzGX3ArY0D-r3jVr6HWBrIhcZbzXTGAIiBnRpa3Rvaw", ".tiktok.com", "/", true),
        CookieItem("store-country-code", "vn", ".tiktok.com", "/", false),
        CookieItem("tiktok_webapp_theme_source", "auto", ".www.tiktok.com", "/", true),
        CookieItem("tt-target-idc", "alisg", ".tiktok.com", "/", false),
        CookieItem("tt_chain_token", "uSdYOrXUTU95w0t+TxXOhQ==", ".tiktok.com", "/", true),
        CookieItem("tt_csrf_token", "iGmIv3y2-IaHVDYUrt5sq-L8XJ6Mm0pKYW8Y", ".tiktok.com", "/", true),
        CookieItem("ttcsid_C97F413C77U6S6FS3KBG", "1769049744488::4MeV7VrV5ZBw1vF5ca3G.1.1769050025932.1", ".tiktok.com", "/", false),
        CookieItem("ttcsid_CDICPPBC77UFUTJBVLI0", "1769049744490::Kmqz7040oygK1cz_jUJ5.1.1769050025935.1", ".tiktok.com", "/", false),
        CookieItem("ttcsid_CGCP5PJC77U5LCHF3VG0", "1769049744491::Rv7hQif8AculXyry6-ZJ.1.1769050025937.1", ".tiktok.com", "/", false),
        CookieItem("ttcsid_CS7J93RC77U6TI82IEB0", "1769049744531::SBCM3EsNypG6qvcu9hs_.1.1769050025939.1", ".tiktok.com", "/", false),
        CookieItem("uid_tt_ss", "984b8b732bfbbe42382d8200b104313ba5828ba5c9759f2e2f8ea64f2e47430b", ".tiktok.com", "/", true),
    )

    fun ensureCookiesSeeded(context: Context, force: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alreadySeeded = prefs.getBoolean(KEY_SEEDED, false)
        if (alreadySeeded && !force) return

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)

        for (item in DEFAULT_COOKIES) {
            val cookieBuilder = StringBuilder()
            cookieBuilder.append(item.name).append("=").append(item.value)
            cookieBuilder.append("; Domain=").append(item.domain)
            cookieBuilder.append("; Path=").append(item.path)
            if (item.isSecure) {
                cookieBuilder.append("; Secure")
            }
            cookieBuilder.append("; SameSite=None")
            val cookieStr = cookieBuilder.toString()

            val targetUrls = listOf(
                "https://www.tiktok.com",
                "https://tiktok.com",
                "https://shortdrama.tiktok.com"
            )
            for (u in targetUrls) {
                cm.setCookie(u, cookieStr)
            }
        }
        cm.flush()
        prefs.edit()
            .putBoolean(KEY_SEEDED, true)
            .putBoolean(KEY_LOGGED_IN, true)
            .apply()
    }

    fun isAccountActive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val flag = prefs.getBoolean(KEY_LOGGED_IN, false)
        val cm = CookieManager.getInstance()
        val c = cm.getCookie("https://www.tiktok.com") ?: ""
        return flag || c.contains("sessionid") || c.contains("sid_tt")
    }

    fun clearSession(context: Context, onComplete: () -> Unit) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LOGGED_IN, false).apply()
        val cm = CookieManager.getInstance()
        cm.removeAllCookies {
            cm.flush()
            onComplete()
        }
    }
}
