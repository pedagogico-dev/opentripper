package com.opentripper;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;

/**
 * Tela de mapa do celular (OpenStreetMap via Leaflet em WebView).
 * Mostra posicao, rumo, rastro percorrido e velocidade. Independente do Tripper Pod:
 * o GPS so fica ligado enquanto esta tela estiver aberta.
 */
public class MapActivity extends Activity implements LocationListener {

    private static final String BASE_URL = "https://github.com/OSolerHugo/opentripper";
    private static final int REQ_LOCATION = 4711;

    private WebView web;
    private FrameLayout root;
    private LocationManager lm;
    private boolean pageReady = false;
    private boolean updatesOn = false;
    private String pendingJs = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#0A0E17"));

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString()
                + " OpenTripper/0.6 (+https://github.com/OSolerHugo/opentripper)");
        web.setBackgroundColor(Color.parseColor("#0A0E17"));
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                if (pendingJs != null) {
                    web.evaluateJavascript(pendingJs, null);
                    pendingJs = null;
                }
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView close = new TextView(this);
        close.setText("\u2715  VOLTAR");
        close.setTextColor(Color.parseColor("#E8EDF3"));
        close.setTextSize(12);
        close.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        int p = dp(12);
        close.setPadding(p, dp(8), p, dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#E6141C28"));
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), Color.parseColor("#232F42"));
        close.setBackground(bg);
        close.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        lp.setMargins(0, dp(12), dp(12), 0);
        root.addView(close, lp);

        setContentView(root);

        try {
            web.loadDataWithBaseURL(BASE_URL, readAsset("map.html"), "text/html", "UTF-8", null);
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao carregar o mapa: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        if (!hasLocationPermission()) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private String readAsset(String name) throws Exception {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startLocation() {
        if (updatesOn || !hasLocationPermission()) return;
        try {
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Toast.makeText(this, "Ative o GPS do celular", Toast.LENGTH_LONG).show();
                return;
            }
            Location last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last == null) last = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (last != null) onLocationChanged(last);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper());
            updatesOn = true;
        } catch (SecurityException e) {
            Toast.makeText(this, "Permissao de localizacao negada", Toast.LENGTH_LONG).show();
        }
    }

    private void stopLocation() {
        if (!updatesOn) return;
        lm.removeUpdates(this);
        updatesOn = false;
    }

    private void pushJs(String js) {
        if (pageReady) web.evaluateJavascript(js, null);
        else pendingJs = js; // guarda so o ultimo fix ate a pagina carregar
    }

    @Override
    public void onLocationChanged(Location loc) {
        float kmh = loc.hasSpeed() ? loc.getSpeed() * 3.6f : 0f;
        float brg = (loc.hasBearing() && loc.getSpeed() > 1.0f) ? loc.getBearing() : -1f;
        float acc = loc.hasAccuracy() ? loc.getAccuracy() : 0f;
        pushJs(String.format(Locale.US, "updatePos(%.6f,%.6f,%.1f,%.1f,%.1f);",
                loc.getLatitude(), loc.getLongitude(), brg, kmh, acc));
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_LOCATION) startLocation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        startLocation();
    }

    @Override
    protected void onPause() {
        stopLocation(); // economiza bateria: GPS desligado quando o mapa sai da tela
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        root.removeView(web);
        web.destroy();
        super.onDestroy();
    }
}
