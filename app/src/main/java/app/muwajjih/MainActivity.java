package app.muwajjih;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            WebView web = new WebView(this);
            web.setBackgroundColor(Color.parseColor("#0B0F14"));
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(true);
            web.addJavascriptInterface(new RouterBridge(), "Muwajjih");
            setContentView(web);
            web.loadUrl("file:///android_asset/www/index.html");
        } catch (Throwable error) {
            TextView view = new TextView(this);
            view.setTextColor(Color.parseColor("#F3EEE6"));
            view.setBackgroundColor(Color.parseColor("#0B0F14"));
            view.setPadding(48, 48, 48, 48);
            view.setText(String.valueOf(error));
            setContentView(view);
        }
    }
}
