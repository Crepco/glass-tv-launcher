package com.hamza.glass;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

public final class HomeActivity extends Activity {
    private HomeView view;

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Uri u = i.getData();
            if (u != null) view.apps.forget(u.getSchemeSpecificPart());
            view.reload();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        view = new HomeView(this);
        setContentView(view);
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        view.goHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.reload();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        return view.onKey(e) || super.dispatchKeyEvent(e);
    }
}
