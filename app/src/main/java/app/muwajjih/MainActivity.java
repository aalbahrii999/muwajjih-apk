package app.muwajjih;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

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
            FrameLayout root = new FrameLayout(this);
            root.setBackgroundColor(Color.parseColor("#0B0F14"));
            root.addView(web, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
                Insets bars = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars()
                                | WindowInsetsCompat.Type.displayCutout()
                                | WindowInsetsCompat.Type.ime()
                );
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
            setContentView(root);
            ViewCompat.requestApplyInsets(root);
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
