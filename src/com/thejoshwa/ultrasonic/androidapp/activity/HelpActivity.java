/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */

package com.thejoshwa.ultrasonic.androidapp.activity;

import android.app.Activity;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import com.thejoshwa.ultrasonic.androidapp.R;
import com.thejoshwa.ultrasonic.androidapp.util.Util;

/**
 * An HTML-based help screen with Back and Done buttons at the bottom.
 *
 * @author Sindre Mehus
 */
public final class HelpActivity extends Activity {
	private static final String TAG = HelpActivity.class.getSimpleName();
    private WebView webView;
    private Button backButton;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        setContentView(R.layout.help);
        
        webView = (WebView) findViewById(R.id.help_contents);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new HelpClient());
        if (bundle != null) {
            webView.restoreState(bundle);
        } else {
            webView.loadUrl(getResources().getString(R.string.help_url));
        }

        backButton = (Button) findViewById(R.id.help_back);
        backButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                webView.goBack();
            }
        });

        Button doneButton = (Button) findViewById(R.id.help_close);
        doneButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        webView.saveState(state);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private final class HelpClient extends WebViewClient {
        @Override
        public void onLoadResource(WebView webView, String url) {
            setProgressBarIndeterminateVisibility(true);
            setTitle(getResources().getString(R.string.help_loading));
            super.onLoadResource(webView, url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            setProgressBarIndeterminateVisibility(false);
            String versionName = null;
            
    		try {
    			versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
    		} catch (NameNotFoundException e) {
    			Log.e(TAG, e.getMessage(), e);
    		}
    		
            setTitle(view.getTitle() + " (" + versionName + ")");
            backButton.setEnabled(view.canGoBack());
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            Util.toast(HelpActivity.this, description);
        }
    }
}
