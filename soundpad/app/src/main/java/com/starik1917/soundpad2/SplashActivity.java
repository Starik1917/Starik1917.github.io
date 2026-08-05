package com.starik1917.soundpad2;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SplashActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable openMain = () -> {
        if (isFinishing()) return;
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(9, 9, 15));
        window.setNavigationBarColor(Color.rgb(9, 9, 15));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(32), dp(32), dp(32), dp(32));
        root.setBackgroundResource(R.drawable.splash_background);

        ImageView wave = new ImageView(this);
        wave.setImageResource(R.drawable.ic_wave);
        LinearLayout.LayoutParams waveLp = new LinearLayout.LayoutParams(dp(96), dp(96));
        waveLp.bottomMargin = dp(18);
        root.addView(wave, waveLp);

        TextView title = new TextView(this);
        title.setText("SoundPad");
        title.setTextColor(Color.WHITE);
        title.setTextSize(34);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-2, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("tap  •  play  •  repeat");
        subtitle.setTextColor(Color.rgb(190, 179, 225));
        subtitle.setTextSize(13);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-2, -2);
        subLp.topMargin = dp(8);
        root.addView(subtitle, subLp);

        View dot = new View(this);
        dot.setBackgroundColor(Color.rgb(142, 112, 255));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(34), dp(3));
        dotLp.topMargin = dp(24);
        root.addView(dot, dotLp);

        setContentView(root);
        handler.postDelayed(openMain, 650);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(openMain);
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
