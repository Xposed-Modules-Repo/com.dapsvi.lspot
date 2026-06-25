package com.dapsvi.lspot.ui;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final String SPOTIFY_PKG = "com.spotify.music";
    private static final String MODULE_PKG = "com.dapsvi.lspot";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildLayout());
    }

    private View buildLayout() {
        ScrollView scroller = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(16));

        PackageManager pm = getPackageManager();

        root.addView(sectionTitle("LSpot"));

        PackageInfo spotifyInfo = getPackageInfo(pm, SPOTIFY_PKG);
        root.addView(checkRow("Spotify detected",
            spotifyInfo != null,
            spotifyInfo != null ? "v" + spotifyInfo.versionName : "Package com.spotify.music not found"));

        boolean xposedmodule = false;
        boolean hasScope = false;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(MODULE_PKG, PackageManager.GET_META_DATA);
            if (ai.metaData != null) {
                xposedmodule = ai.metaData.getBoolean("xposedmodule", false);
                hasScope = ai.metaData.containsKey("xposedscope");
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        root.addView(checkRow("Module declares xposedmodule",
            xposedmodule, xposedmodule ? "OK" : "Missing xposedmodule metadata"));

        root.addView(checkRow("Module scope set",
            hasScope, hasScope ? "com.spotify.music" : "Module scope unset (check your LSPosed manager)"));

        root.addView(space(dp(12)));
        boolean setupOk = xposedmodule && hasScope;
        TextView statusHeader = new TextView(this);
        statusHeader.setText(setupOk ? "Module setup complete" : "Module setup incomplete");
        statusHeader.setTextColor(setupOk ? 0xFF2E7D32 : 0xFFC62828);
        statusHeader.setTextSize(16);
        statusHeader.setTypeface(Typeface.DEFAULT_BOLD);
        statusHeader.setPadding(0, dp(4), 0, dp(4));
        root.addView(statusHeader);

        root.addView(hintText(
            "If ads are still playing:\n" +
            "1. Enable this module in LSPosed (Modules -> LSpot)\n" +
            "2. Make sure Spotify is checked under the module's scope\n" +
            "3. Force-stop Spotify and reopen it\n" +
            "4. Reboot if changes don't take effect\n\n" +
            "To verify that the hooks work, check your LSPosed manager logs for 'LSpot' entries."
        ));

        root.addView(space(dp(12)));
        Button closeBtn = new Button(this);
        closeBtn.setText("Close");
        closeBtn.setOnClickListener(v -> finish());
        root.addView(closeBtn);

        scroller.addView(root);
        return scroller;
    }

    private PackageInfo getPackageInfo(PackageManager pm, String pkg) {
        try {
            return pm.getPackageInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }


    private TextView sectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(20);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, 0, 0, dp(4));
        return tv;
    }

    private TextView checkRow(String label, boolean ok, String detail) {
        TextView tv = new TextView(this);
        String icon = ok ? "  OK  " : " FAIL ";
        SpannableString ss = new SpannableString(icon + label + "  -  " + detail);
        ss.setSpan(new StyleSpan(Typeface.BOLD), 0, icon.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv.setText(ss);
        tv.setTextColor(ok ? 0xFF2E7D32 : 0xFFC62828);
        tv.setTextSize(14);
        tv.setPadding(0, dp(4), 0, dp(4));
        return tv;
    }

    private TextView hintText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(0xFF616161);
        tv.setPadding(dp(24), dp(2), 0, dp(2));
        return tv;
    }

    private View space(int px) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px));
        return v;
    }

    private int dp(int dps) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dps * density + 0.5f);
    }
}
