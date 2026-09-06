package com.thor.mph;

import android.app.Presentation;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import org.libsdl.app.SDLActivity;

/**
 * Metroid Prime Hunters (AMHE0) on the AYN Thor.
 *
 * The main display shows the SDL window (both DS screens stacked for now). If
 * the Thor's second physical display is present, we also mirror the DS bottom
 * screen onto it via a Presentation whose Surface is handed to the native
 * runner (libmain.so) through nativeSetSecondSurface().
 */
public class MyGame extends SDLActivity {
    private String romPath;
    private String cfgPath;
    private String biosDir;
    private Presentation secondPresentation;

    // Implemented in libmain.so (android_second_screen.cpp).
    public static native void nativeSetSecondSurface(Surface surface);
    public static native void nativeSecondScreenTouch(float nx, float ny,
                                                      boolean down);
    public static native void nativeSetSecondScreenStretch(boolean stretch);
    public static native void nativeFlushDurableState();
    // RetroAchievements bridge (retroachievements.cpp).
    public static native void nativeRaBind();
    public static native void nativeRaHttpResponse(long id, int status,
                                                   byte[] body);
    public static volatile MyGame instance;

    /** Called from the emulation thread: perform the HTTP request off-thread
     *  and hand the response back to native, which dispatches it on its own
     *  thread. Status -1 signals a client-side failure (rc_client retries). */
    public static void raHttpRequest(final long id, final String url,
                                     final String post, final String type) {
        new Thread(() -> {
            int status = -1;
            byte[] body = new byte[0];
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                    new java.net.URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setRequestProperty("User-Agent",
                    "HuntersRecomp/" + versionName() + " rcheevos");
                if (post != null) {
                    c.setDoOutput(true);
                    c.setRequestMethod("POST");
                    c.setRequestProperty("Content-Type",
                        type != null ? type
                                     : "application/x-www-form-urlencoded");
                    byte[] data = post.getBytes("UTF-8");
                    c.setFixedLengthStreamingMode(data.length);
                    try (java.io.OutputStream out = c.getOutputStream()) {
                        out.write(data);
                    }
                }
                status = c.getResponseCode();
                java.io.InputStream in = status >= 400 ? c.getErrorStream()
                                                       : c.getInputStream();
                if (in != null) {
                    java.io.ByteArrayOutputStream bo =
                        new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                    body = bo.toByteArray();
                    in.close();
                }
            } catch (Exception e) {
                android.util.Log.w("ThorMPH", "RA http failed: " + e);
            }
            nativeRaHttpResponse(id, status, body);
        }, "ra-http").start();
    }

    private static String versionName() {
        try {
            MyGame a = instance;
            if (a == null) return "?";
            return a.getPackageManager().getPackageInfo(a.getPackageName(), 0)
                .versionName;
        } catch (Exception e) { return "?"; }
    }

    private android.widget.TextView raStatusView, raBannerView;
    private static String raStatusText;
    private final android.os.Handler raHandler =
        new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable raHideBanner = () -> {
        if (raBannerView != null) raBannerView.setVisibility(android.view.View.GONE);
    };

    /** Unlock / status pop-up: toast on the main screen and a banner on the
     *  bottom screen's RA strip that fades after a few seconds. */
    public static void raNotify(final String title, final String body) {
        final MyGame a = instance;
        if (a == null) return;
        a.runOnUiThread(() -> {
            android.widget.Toast.makeText(a, title + "\n" + body,
                android.widget.Toast.LENGTH_LONG).show();
            if (a.raBannerView != null && a.raStripEnabled) {
                a.raBannerView.setText(title + "\n" + body);
                a.raBannerView.setVisibility(android.view.View.VISIBLE);
                a.raHandler.removeCallbacks(a.raHideBanner);
                a.raHandler.postDelayed(a.raHideBanner, 7000);
            }
        });
    }

    private boolean raStripAllowed() {
        android.content.SharedPreferences prefs =
            getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE);
        return prefs.getBoolean("ra_strip", false)
            && prefs.getString("bottom_aspect", "fit").equals("fit");
    }

    /** Size the RA views to the letterbox bars of a 4:3 image on this panel. */
    private void layoutRaStrip(int w, int h) {
        if (raStatusView == null || raBannerView == null || w <= 0 || h <= 0) return;
        int contentH = Math.round(w * 3f / 4f);
        int bar = (h - contentH) / 2;   // top/bottom black bar height
        boolean ok = raStripAllowed() && bar >= Math.round(
            22 * getResources().getDisplayMetrics().density);
        raStripEnabled = ok;
        android.widget.FrameLayout.LayoutParams sl =
            (android.widget.FrameLayout.LayoutParams) raStatusView.getLayoutParams();
        android.widget.FrameLayout.LayoutParams bl =
            (android.widget.FrameLayout.LayoutParams) raBannerView.getLayoutParams();
        sl.height = ok ? bar : android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        bl.height = ok ? bar : android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        raStatusView.setLayoutParams(sl);
        raBannerView.setLayoutParams(bl);
        raStatusView.setGravity(android.view.Gravity.CENTER);
        raBannerView.setGravity(android.view.Gravity.CENTER);
        raStatusView.setBackgroundColor(0xFF000000);
        raStatusView.setVisibility(ok && raStatusText != null && !raStatusText.isEmpty()
            ? android.view.View.VISIBLE : android.view.View.GONE);
        if (!ok) raBannerView.setVisibility(android.view.View.GONE);
        if (ok && raStatusText != null) raStatusView.setText(raStatusText);
    }
    private boolean raStripEnabled = false;

    /** Persistent line on the bottom screen: user, game progress, mode. */
    public static void raStatus(final String line) {
        raStatusText = line;
        final MyGame a = instance;
        if (a == null) return;
        a.runOnUiThread(() -> {
            if (a.raStatusView != null) {
                a.raStatusView.setText(line);
                a.raStatusView.setVisibility(!a.raStripEnabled || line == null
                    || line.isEmpty() ? android.view.View.GONE
                                      : android.view.View.VISIBLE);
            }
        });
    }

    /** Persist the session token so later launches skip the password. */
    public static void raStoreToken(String user, String token) {
        final MyGame a = instance;
        if (a == null) return;
        a.getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE).edit()
            .putString("ra_user", user).putString("ra_token", token)
            .remove("ra_password").apply();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        File dir = getExternalFilesDir(null);
        biosDir = dir.getAbsolutePath();
        File cfg = new File(dir, "game.toml");
        copyAsset("game.toml", cfg);
        cfgPath = cfg.getAbsolutePath();
        romPath = new File(dir, "mph.nds").getAbsolutePath();

        super.onCreate(savedInstanceState);
        instance = this;
        try { nativeRaBind(); } catch (Throwable ignored) {}
        // NOTE: setSustainedPerformanceMode(true) was tried and removed: it
        // caps CPU/GPU clocks at a thermally sustainable level, which is the
        // opposite of what a frame-budget-bound emulator needs.
        if (getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE)
                .getBoolean("show_fps", false))
            startFpsOverlay();
        // Bottom-screen presentation preference (native side defaults to 4:3).
        nativeSetSecondScreenStretch(
            getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE)
                .getString("bottom_aspect", "fit").equals("stretch"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupSecondDisplay();
    }

    @Override
    protected void onPause() {
        // Persist what a swipe-away would lose: coverage part + dirty save.
        try { nativeFlushDurableState(); } catch (Throwable ignored) {}
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // Re-attach the second screen whenever we come back to the front
        // (covers focus transitions where onResume does not re-fire).
        if (hasFocus) {
            setupSecondDisplay();
            enterImmersiveMode();
        }
    }

    // Hide the status and navigation bars while playing (immersive sticky:
    // a swipe peeks them temporarily). Without this the Android nav/home bar
    // overlays the game on units with navigation buttons enabled.
    private void enterImmersiveMode() {
        android.view.Window w = getWindow();
        w.setDecorFitsSystemWindows(false);
        android.view.WindowInsetsController c = w.getInsetsController();
        if (c != null) {
            c.hide(android.view.WindowInsets.Type.systemBars());
            c.setSystemBarsBehavior(android.view.WindowInsetsController
                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    @Override
    protected void onDestroy() {
        if (secondPresentation != null) {
            secondPresentation.dismiss();
            secondPresentation = null;
        }
        super.onDestroy();
    }

    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL2", "main" };
    }

    @Override
    protected String[] getArguments() {
        android.content.SharedPreferences prefs =
            getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE);
        java.util.ArrayList<String> args = new java.util.ArrayList<>();
        java.util.Collections.addAll(args,
            biosDir, "0",
            "--rom", romPath,
            "--config", cfgPath,
            "--freebios",
            "--boot", "direct",
            "--generated-firmware",
            "--interactive",
            // SDL on Android permits only one window; the top screen fills it
            // and the bottom screen goes to the Thor's second physical display.
            "--screen-layout", "stacked",
            "--mph-prime-controls", "on",
            // AMHE0 direct-aim gate: right-stick/touch aim deltas feed the
            // game's own aim fields (main.cpp: mph_mouse_aim_policy).
            "--relative-mouse-touch", "on",
            // Persist the cartridge flash save next to the ROM so in-game
            // saves (at the ship) survive restarts.
            "--save-path", new File(getExternalFilesDir(null), "mph.sav")
                .getAbsolutePath(),
            // Alpha diagnostics: per-second perf/audio counters written where
            // adb (and bug reporters) can pull them.
            "--diagnostics", "on",
            "--diagnostics-dir", new File(getExternalFilesDir(null),
                "diagnostics").getAbsolutePath(),
            "--diagnostics-interval-ms", "1000",
            // Record static-bank Tier-3 miss targets so every player session
            // produces a promotable coverage manifest.
            "--discover-static-misses");
        // ── User settings (SettingsActivity) ─────────────────────────────
        args.add("--mph-pad-aim-sensitivity");
        args.add(String.valueOf(prefs.getInt("aim_sens", 100)));
        args.add("--mph-virtual-stylus-sensitivity");
        args.add(String.valueOf(prefs.getInt("stylus_sens", 20)));
        args.add("--relative-mouse-invert-y");
        args.add(prefs.getBoolean("invert_y", false) ? "on" : "off");
        args.add("--internal-resolution");
        args.add(prefs.getString("internal_res", "3"));
        args.add("--texture-upscale");
        args.add(prefs.getString("tex_upscale", "2"));
        // RetroAchievements: login by stored token, else by password once.
        if (prefs.getBoolean("ra_enabled", false)
                && !prefs.getString("ra_user", "").isEmpty()) {
            args.add("--ra-user");
            args.add(prefs.getString("ra_user", ""));
            String token = prefs.getString("ra_token", "");
            String pw = prefs.getString("ra_password", "");
            if (!token.isEmpty()) { args.add("--ra-token"); args.add(token); }
            else if (!pw.isEmpty()) { args.add("--ra-password"); args.add(pw); }
            args.add("--ra-hardcore");
            args.add(prefs.getBoolean("ra_hardcore", false) ? "on" : "off");
            if (prefs.getBoolean("ra_hash_override", false)) {
                // RA's only linked hash for game 1378 (rev 1); user opt-in.
                args.add("--ra-hash-override");
                args.add("b6947e630bcf9e68aa3cf998d89357ac");
            }
        }
        for (String[] action : SettingsActivity.ACTIONS) {
            String bound = prefs.getString("bind_" + action[0], action[2]);
            if (bound.equals(action[2])) continue;  // engine default
            args.add("--mph-pad-bind-" + action[0]);
            args.add(bound);
        }
        android.util.Log.i("ThorMPH", "runner args: " + args);
        return args.toArray(new String[0]);
    }

    private void setupSecondDisplay() {
        if (secondPresentation != null) return;
        DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) return;
        Display target = null;
        for (Display d : dm.getDisplays()) {
            if (d.getDisplayId() != Display.DEFAULT_DISPLAY
                    && (d.getFlags() & Display.FLAG_PRESENTATION) != 0) {
                target = d;
                break;
            }
        }
        if (target == null) {
            // Fall back to any non-default display.
            for (Display d : dm.getDisplays()) {
                if (d.getDisplayId() != Display.DEFAULT_DISPLAY) {
                    target = d;
                    break;
                }
            }
        }
        if (target == null) return;

        final SurfaceView view = new SurfaceView(this);
        view.setOnTouchListener(new android.view.View.OnTouchListener() {
            @Override public boolean onTouch(android.view.View v, android.view.MotionEvent e) {
                float nx = e.getX() / Math.max(1, v.getWidth());
                float ny = e.getY() / Math.max(1, v.getHeight());
                int a = e.getActionMasked();
                boolean down = a != android.view.MotionEvent.ACTION_UP
                        && a != android.view.MotionEvent.ACTION_CANCEL;
                nativeSecondScreenTouch(nx, ny, down);
                return true;
            }
        });
        view.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder h) {
                nativeSetSecondSurface(h.getSurface());
            }
            @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {
                nativeSetSecondSurface(h.getSurface());
            }
            @Override public void surfaceDestroyed(SurfaceHolder h) {
                nativeSetSecondSurface(null);
            }
        });

        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.addView(view, new android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        // RetroAchievements strip: persistent status at the bottom edge and
        // unlock banners that fade out, drawn over the DS bottom screen.
        raStatusView = new android.widget.TextView(this);
        raStatusView.setTextColor(0xFFE8E2D8);
        raStatusView.setTextSize(13);
        raStatusView.setBackgroundColor(0x99000000);
        int pad = Math.round(8 * getResources().getDisplayMetrics().density);
        raStatusView.setPadding(pad, pad / 2, pad, pad / 2);
        raStatusView.setVisibility(android.view.View.GONE);
        frame.addView(raStatusView, new android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM));
        raBannerView = new android.widget.TextView(this);
        raBannerView.setTextColor(0xFF14100A);
        raBannerView.setBackgroundColor(0xF0FF8A3D);
        raBannerView.setTextSize(15);
        raBannerView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        raBannerView.setPadding(pad * 2, pad, pad * 2, pad);
        raBannerView.setVisibility(android.view.View.GONE);
        frame.addView(raBannerView, new android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.TOP));
        // Place the strip and banners inside the letterbox bars only: the
        // user opts in, and only Original 4:3 mode has black bars. In stretch
        // mode nothing is drawn over the DS bottom screen.
        frame.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
            layoutRaStrip(frame.getWidth(), frame.getHeight()));
        Presentation p = new Presentation(this, target);
        p.setContentView(frame);
        // If the system (or a hardware shortcut) dismisses the second-screen
        // window, forget it so the next onResume() rebuilds it. Without this
        // the stale reference blocks re-attach and the bottom screen never
        // comes back after leaving and returning to the app.
        p.setOnDismissListener(d -> {
            secondPresentation = null;
            nativeSetSecondSurface(null);
        });
        try {
            p.show();
            secondPresentation = p;
        } catch (Exception e) {
            android.util.Log.e("ThorMPH", "second display show failed", e);
        }
    }

    // True engine FPS counter: the runner writes per-second perf records to
    // the diagnostics JSONL; tail the newest file and surface fps + audio
    // underruns. Engine truth, not a SurfaceFlinger guess.
    private android.widget.TextView fpsView;
    private final android.os.Handler fpsHandler =
        new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable fpsTick = new Runnable() {
        @Override public void run() {
            try {
                File dir = new File(getExternalFilesDir(null), "diagnostics");
                File[] files = dir.listFiles((d, n) -> n.startsWith("performance-"));
                if (files != null && files.length > 0) {
                    File newest = files[0];
                    for (File f : files)
                        if (f.lastModified() > newest.lastModified()) newest = f;
                    String last = null;
                    try (java.io.RandomAccessFile raf =
                             new java.io.RandomAccessFile(newest, "r")) {
                        long len = raf.length();
                        long start = Math.max(0, len - 4096);
                        raf.seek(start);
                        byte[] buf = new byte[(int) (len - start)];
                        raf.readFully(buf);
                        String[] lines = new String(buf).split("\n");
                        for (int i = lines.length - 1; i >= 0; --i)
                            if (lines[i].contains("\"fps\"")) { last = lines[i]; break; }
                    }
                    if (last != null) {
                        org.json.JSONObject o = new org.json.JSONObject(last);
                        double fps = o.optDouble("fps", 0);
                        int und = o.optInt("underruns_delta", 0);
                        fpsView.setText(String.format(java.util.Locale.US,
                            "%.0f FPS%s", fps, und > 0 ? " ·" + und + "⚠" : ""));
                        fpsView.setTextColor(fps >= 55 ? 0xFF7FC97F
                            : fps >= 40 ? 0xFFE8C468 : 0xFFE07A5F);
                    }
                }
            } catch (Exception ignored) {}
            fpsHandler.postDelayed(this, 1000);
        }
    };

    private void startFpsOverlay() {
        fpsView = new android.widget.TextView(this);
        fpsView.setTextSize(14);
        fpsView.setTypeface(android.graphics.Typeface.MONOSPACE,
            android.graphics.Typeface.BOLD);
        fpsView.setTextColor(0xFF7FC97F);
        fpsView.setBackgroundColor(0x66000000);
        int p = Math.round(6 * getResources().getDisplayMetrics().density);
        fpsView.setPadding(p, p / 2, p, p / 2);
        android.widget.FrameLayout.LayoutParams lp =
            new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP | android.view.Gravity.END);
        lp.topMargin = p;
        lp.rightMargin = p;
        addContentView(fpsView, lp);
        fpsHandler.postDelayed(fpsTick, 1500);
    }

    private void copyAsset(String name, File dest) {
        try (InputStream in = getAssets().open(name);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (Exception e) {
            android.util.Log.e("ThorMPH", "copyAsset " + name + " failed", e);
        }
    }
}
