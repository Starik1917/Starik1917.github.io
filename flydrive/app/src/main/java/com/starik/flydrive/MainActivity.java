package com.starik.flydrive;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public final class MainActivity extends Activity {
    private GameView gameView;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        gameView = new GameView(this);
        setContentView(gameView);
    }

    @Override protected void onResume() {
        super.onResume();
        if (gameView != null) gameView.resumeLoop();
    }

    @Override protected void onPause() {
        if (gameView != null) gameView.pauseLoop();
        super.onPause();
    }
}
