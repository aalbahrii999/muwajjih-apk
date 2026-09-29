package app.muwajjih

import android.util.Base64
import android.webkit.JavascriptInterface
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class RouterBridge {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(9, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    @JavascriptInterface
    fun version(): String = "1.0.0"

    @JavascriptInterface
    fun versionCode(): Int = 1

    /** Huawei HiLink password_type 4. The raw password is not logged. */
    @JavascriptInterface
    fun huaweiPassword(username: String, password: String, token: String): String {
        fun b64(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(digest, Base64.NO_WRAP)
        }
        return b64(username + b64(password) + token)
    }

    @JavascriptInterface
    fun exchange(
        baseUrl: String,
        path: String,
        method: String,
        body: String,
        cookie: String,
        token: String,
    ): String {
        return try {
            val url = safeUrl(baseUrl, path)
            val verb = if (method.equals("POST", ignoreCase = true)) "POST" else "GET"
            val builder = Request.Builder().url(url).header("Accept", "*/*")
            headerOrNull(cookie)?.let { builder.header("Cookie", it) }
            headerOrNull(token)?.let { builder.header("__RequestVerificationToken", it) }
            if (verb == "POST") {
                builder.post(body.take(100_000).toRequestBody(XML))
            } else {
                builder.get()
            }
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string()?.take(200_000).orEmpty()
                val setCookie = response.header("Set-Cookie")?.substringBefore(';')
                val next = response.header("__RequestVerificationToken")
                JSONObject()
                    .put("ok", true)
                    .put("status", response.code)
                    .put("text", text)
                    .put("cookie", setCookie ?: JSONObject.NULL)
                    .put("token", next ?: JSONObject.NULL)
                    .toString()
            }
        } catch (error: Reject) {
            fail(error.message ?: "طلب مرفوض.")
        } catch (error: Exception) {
            val host = runCatching { URI(baseUrl).host }.getOrNull() ?: baseUrl
            val timedOut = error.message?.contains("timeout", ignoreCase = true) == true
            val message = if (timedOut) {
                "انتهت المهلة والراوتر ما رد خلال ٨ ثوانٍ."
            } else {
                "ما قدرنا نوصل للراوتر على $host. تأكد أنك على واي فاي الجهاز."
            }
            fail(message)
        }
    }

    private fun fail(message: String): String = JSONObject().put("ok", false).put("error", message).toString()

    private fun headerOrNull(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.length > 400 || trimmed.contains('\n') || trimmed.contains('\r')) return null
        return trimmed
    }

    private fun safeUrl(baseUrl: String, path: String): String {
        val uri = try {
            URI(baseUrl.trim())
        } catch (_: Exception) {
            throw Reject("عنوان الراوتر غير صالح.")
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") throw Reject("العنوان لازم يبدأ بـ http أو https.")
        val host = uri.host?.lowercase() ?: throw Reject("عنوان الراوتر غير صالح.")
        if (host == "localhost" || host == "127.0.0.1" || host == "0.0.0.0" || host == "169.254.169.254") {
            throw Reject("هذا العنوان ممنوع.")
        }
        if (uri.userInfo != null) throw Reject("العنوان غير مسموح.")
        val port = if (uri.port == -1) "" else ":${uri.port}"
        if (uri.port != -1 && uri.port != 80 && uri.port != 443 && uri.port != 8080) {
            throw Reject("منفذ الراوتر غير مسموح. استخدم 80 أو 443 أو 8080.")
        }
        if (!path.startsWith("/api/") || path.contains("..") || !path.matches(Regex("^/api/[A-Za-z0-9_./-]+$"))) {
            throw Reject("مسار الطلب غير مسموح.")
        }
        return "$scheme://$host$port$path"
    }

    private class Reject(message: String) : Exception(message)

    companion object {
        private val XML = "application/xml; charset=UTF-8".toMediaType()
    }
}
