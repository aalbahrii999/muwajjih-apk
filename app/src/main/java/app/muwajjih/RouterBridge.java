package app.muwajjih;

import android.util.Base64;
import android.webkit.JavascriptInterface;

import org.json.JSONObject;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RouterBridge {
    private static final MediaType XML = MediaType.get("application/xml; charset=UTF-8");

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(9, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    @JavascriptInterface
    public String version() {
        return "1.0.1";
    }

    @JavascriptInterface
    public int versionCode() {
        return 2;
    }

    @JavascriptInterface
    public String huaweiPassword(String username, String password, String token) {
        return b64(username + b64(password) + token);
    }

    private static String b64(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(digest, Base64.NO_WRAP);
        } catch (Exception error) {
            return "";
        }
    }

    @JavascriptInterface
    public String exchange(
            String baseUrl,
            String path,
            String method,
            String body,
            String cookie,
            String token
    ) {
        try {
            String url = safeUrl(baseUrl, path);
            boolean post = method != null && method.equalsIgnoreCase("POST");
            Request.Builder builder = new Request.Builder().url(url).header("Accept", "*/*");
            String cookieHeader = headerOrNull(cookie);
            if (cookieHeader != null) builder.header("Cookie", cookieHeader);
            String tokenHeader = headerOrNull(token);
            if (tokenHeader != null) builder.header("__RequestVerificationToken", tokenHeader);
            if (post) {
                String payload = body == null ? "" : body;
                if (payload.length() > 100_000) payload = payload.substring(0, 100_000);
                builder.post(RequestBody.create(payload, XML));
            } else {
                builder.get();
            }
            try (Response response = client.newCall(builder.build()).execute()) {
                String text = "";
                if (response.body() != null) {
                    text = response.body().string();
                    if (text.length() > 200_000) text = text.substring(0, 200_000);
                }
                String setCookie = response.header("Set-Cookie");
                if (setCookie != null) {
                    int semi = setCookie.indexOf(';');
                    if (semi >= 0) setCookie = setCookie.substring(0, semi);
                }
                String next = response.header("__RequestVerificationToken");
                JSONObject json = new JSONObject();
                json.put("ok", true);
                json.put("status", response.code());
                json.put("text", text);
                json.put("cookie", setCookie == null ? JSONObject.NULL : setCookie);
                json.put("token", next == null ? JSONObject.NULL : next);
                return json.toString();
            }
        } catch (Reject error) {
            return fail(error.getMessage() == null ? "طلب مرفوض." : error.getMessage());
        } catch (Exception error) {
            String host = baseUrl;
            try {
                String parsed = new URI(baseUrl).getHost();
                if (parsed != null) host = parsed;
            } catch (Exception ignored) {
            }
            String raw = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
            String message = raw.contains("timeout")
                    ? "انتهت المهلة والراوتر ما رد خلال ٨ ثوانٍ."
                    : "ما قدرنا نوصل للراوتر على " + host + ". تأكد أنك على واي فاي الجهاز.";
            return fail(message);
        }
    }

    private static String fail(String message) {
        try {
            return new JSONObject().put("ok", false).put("error", message).toString();
        } catch (Exception error) {
            return "{\"ok\":false,\"error\":\"تعذّر الطلب.\"}";
        }
    }

    private static String headerOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 400 || trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0) {
            return null;
        }
        return trimmed;
    }

    private static String safeUrl(String baseUrl, String path) throws Reject {
        URI uri;
        try {
            uri = new URI(baseUrl == null ? "" : baseUrl.trim());
        } catch (Exception error) {
            throw new Reject("عنوان الراوتر غير صالح.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new Reject("العنوان لازم يبدأ بـ http أو https.");
        }
        if (uri.getHost() == null) throw new Reject("عنوان الراوتر غير صالح.");
        String host = uri.getHost().toLowerCase();
        if (host.equals("localhost") || host.equals("127.0.0.1") || host.equals("0.0.0.0") || host.equals("169.254.169.254")) {
            throw new Reject("هذا العنوان ممنوع.");
        }
        if (uri.getUserInfo() != null) throw new Reject("العنوان غير مسموح.");
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443 && port != 8080) {
            throw new Reject("منفذ الراوتر غير مسموح. استخدم 80 أو 443 أو 8080.");
        }
        if (path == null || !path.startsWith("/api/") || path.contains("..") || !path.matches("^/api/[A-Za-z0-9_./-]+$")) {
            throw new Reject("مسار الطلب غير مسموح.");
        }
        String portPart = port == -1 ? "" : ":" + port;
        return scheme + "://" + host + portPart + path;
    }

    private static final class Reject extends Exception {
        Reject(String message) {
            super(message);
        }
    }
}
