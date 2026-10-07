package app.reversevoice;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Hosts the Reverse Voice web app (bundled in assets/www) in a full-screen WebView.
 * Pages are served from https://appassets.androidplatform.net, a secure origin, so
 * getUserMedia (microphone) and localStorage behave like on a normal https site.
 */
public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/assets/www/index.html";
    private static final int REQ_MIC = 1;

    private WebView webView;
    private PermissionRequest pendingMic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView = new WebView(this);
        webView.setBackgroundColor(getColor(R.color.bg));
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new Bridge(), "ReverseAndroid");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                if (HOST.equals(url.getHost())) return false;
                // Anything outside the app opens in the browser.
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, url));
                } catch (ActivityNotFoundException ignored) {
                }
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            /** The page asks for the microphone (getUserMedia): grant it once Android allows RECORD_AUDIO. */
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                boolean wantsMic = false;
                for (String r : request.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) wantsMic = true;
                }
                if (!wantsMic) {
                    request.deny();
                    return;
                }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                    return;
                }
                if (pendingMic != null) pendingMic.deny();
                pendingMic = request;
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingMic == request) pendingMic = null;
            }
        });

        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        else webView.loadUrl(START_URL);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode != REQ_MIC || pendingMic == null) return;
        boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (ok) pendingMic.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        else pendingMic.deny();
        pendingMic = null;
    }

    /** Back button: the page goes one screen back (window.rvBack returns true); otherwise leave the app. */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        webView.evaluateJavascript("window.rvBack ? rvBack() : false", value -> {
            if (!"true".equals(value)) finish();
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }

    /** Called from the page as window.ReverseAndroid. */
    private class Bridge {
        /** Share a WAV file (base64) through the Android share sheet. */
        @JavascriptInterface
        public void shareWav(final String base64, final String name, final String title) {
            runOnUiThread(() -> {
                try {
                    File dir = new File(getCacheDir(), "share");
                    if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("no share dir");
                    File[] old = dir.listFiles();
                    if (old != null) for (File f : old) //noinspection ResultOfMethodCallIgnored
                        f.delete();
                    String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
                    File file = new File(dir, safe);
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        out.write(Base64.decode(base64, Base64.DEFAULT));
                    }
                    Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", file);
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("audio/wav");
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.setClipData(ClipData.newRawUri(safe, uri));
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, title));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Share failed", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
