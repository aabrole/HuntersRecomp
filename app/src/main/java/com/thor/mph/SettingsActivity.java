package com.thor.mph;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Hunters Recomp launcher/settings screen.
 *
 * Every control writes SharedPreferences that MyGame translates into the
 * ndsrecomp runner's command-line options, so this UI only ever drives knobs
 * the engine already supports (no config side-channels).
 */
public class SettingsActivity extends Activity {
    static final String PREFS = "hunters_recomp";

    // action key -> [label, default binding] (runner --mph-pad-bind-<action>)
    static final String[][] ACTIONS = {
        {"jump", "Jump", "Pad A"},
        {"morph-ball", "Morph Ball", "Pad B"},
        {"shoot", "Shoot", "Pad RT"},
        {"scan-shoot", "Scan / Alt Fire", "Pad LT"},
        {"missile", "Missile", "Pad X"},
        {"beam", "Beam Select", "Pad LB"},
        {"boost-zoom", "Boost / Zoom", "Pad RB"},
        {"scan-visor", "Scan Visor", "Pad R3"},
        {"ui-ok", "UI OK (touch tap)", "Pad Y"},
        {"virtual-stylus", "Virtual Stylus", "None"},
        {"menu", "Menu", "Pad Start"},
        {"weapon1", "Weapon 1", "None"},
        {"weapon2", "Weapon 2", "None"},
        {"weapon3", "Weapon 3", "None"},
    };
    static final String[] PAD_VALUES = {
        "None", "Pad A", "Pad B", "Pad X", "Pad Y", "Pad LB", "Pad RB",
        "Pad LT", "Pad RT", "Pad L3", "Pad R3", "Pad Up", "Pad Down",
        "Pad Left", "Pad Right", "Pad Start", "Pad Back",
    };

    static final int BG = Color.parseColor("#0E1116");
    static final int CARD = Color.parseColor("#171C24");
    static final int ACCENT = Color.parseColor("#FF8A3D");
    static final int TEXT = Color.parseColor("#E8E2D8");
    static final int DIM = Color.parseColor("#8A8578");

    static final String ROM_SHA1 = "90164d1ac127ee5f9815ea4ae7de798c7b5fc629";
    static final long ROM_SIZE = 67108864L;

    private SharedPreferences prefs;
    private TextView romStatus;
    private Button locateButton;

    private java.io.File romFile() {
        return new java.io.File(getExternalFilesDir(null), "mph.nds");
    }

    private void refreshRomStatus() {
        java.io.File rom = romFile();
        if (rom.exists() && rom.length() == ROM_SIZE) {
            romStatus.setText("ROM: found (Metroid Prime Hunters, USA rev 0)");
            romStatus.setTextColor(Color.parseColor("#7FC97F"));
            locateButton.setVisibility(View.GONE);
        } else {
            romStatus.setText("ROM not found. This app includes no game data: "
                + "provide your own dump of Metroid Prime Hunters (USA rev 0, "
                + "64 MiB). Tap LOCATE ROM and pick your .nds file.");
            romStatus.setTextColor(Color.parseColor("#E07A5F"));
            locateButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != 41 || result != RESULT_OK || data == null
                || data.getData() == null) return;
        android.net.Uri uri = data.getData();
        romStatus.setText("Copying and verifying ROM…");
        romStatus.setTextColor(DIM);
        new Thread(() -> {
            String error = null;
            java.io.File dest = romFile();
            try (java.io.InputStream in =
                     getContentResolver().openInputStream(uri);
                 java.io.OutputStream out =
                     new java.io.FileOutputStream(dest)) {
                java.security.MessageDigest md =
                    java.security.MessageDigest.getInstance("SHA-1");
                byte[] buf = new byte[1 << 16];
                long total = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    md.update(buf, 0, n);
                    total += n;
                }
                StringBuilder hex = new StringBuilder();
                for (byte b : md.digest())
                    hex.append(String.format("%02x", b));
                if (total != ROM_SIZE)
                    error = "That file is " + total
                        + " bytes; the USA rev 0 cartridge is 64 MiB.";
                else if (!hex.toString().equals(ROM_SHA1))
                    error = "That dump is not USA revision 0 (SHA-1 "
                        + "mismatch). Rev 1 and other regions cannot work: "
                        + "the recompiled code was generated from rev 0.";
            } catch (Exception e) {
                error = "Copy failed: " + e.getMessage();
            }
            final String err = error;
            runOnUiThread(() -> {
                if (err != null) {
                    romFile().delete();
                    romStatus.setText(err);
                    romStatus.setTextColor(Color.parseColor("#E07A5F"));
                    locateButton.setVisibility(View.VISIBLE);
                } else {
                    refreshRomStatus();
                }
            });
        }).start();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        // Provision the app-owned external files dir on first open so users
        // (and adb) can drop mph.nds into a folder the app can actually read.
        getExternalFilesDir(null);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        setContentView(scroll);

        // ── Header ───────────────────────────────────────────────────────
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.ic_launcher_foreground);
        header.addView(mark, new LinearLayout.LayoutParams(dp(72), dp(72)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("HUNTERS RECOMP");
        title.setTextColor(TEXT);
        title.setTextSize(26);
        title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        title.setLetterSpacing(0.12f);
        TextView sub = new TextView(this);
        sub.setText("Metroid Prime Hunters © 2006 Nintendo · Developed by NST");
        sub.setTextColor(DIM);
        sub.setTextSize(13);
        TextView sub2 = new TextView(this);
        sub2.setText("Recomped for AYN Thor · github.com/aabrole");
        sub2.setTextColor(ACCENT);
        sub2.setTextSize(13);
        // Update reminder, tappable: opens the GitHub releases page where the
        // latest APKs live.
        TextView update = new TextView(this);
        update.setText(android.text.Html.fromHtml(
            "<u>Make sure you're running the latest version</u>",
            android.text.Html.FROM_HTML_MODE_LEGACY));
        update.setTextColor(Color.parseColor("#E8C468"));
        update.setTextSize(13);
        update.setOnClickListener(v -> startActivity(new Intent(
            Intent.ACTION_VIEW, android.net.Uri.parse(
                "https://github.com/aabrole/HuntersRecomp/releases"))));
        titles.addView(title);
        titles.addView(sub);
        titles.addView(sub2);
        titles.addView(update);
        header.addView(titles, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        // Installed version, top-right; tap also opens the releases page.
        TextView version = new TextView(this);
        String vn;
        try {
            vn = "v" + getPackageManager()
                .getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) { vn = "v?"; }
        version.setText(vn);
        version.setTextColor(DIM);
        version.setTextSize(14);
        version.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        version.setGravity(Gravity.TOP | Gravity.END);
        version.setOnClickListener(v -> startActivity(new Intent(
            Intent.ACTION_VIEW, android.net.Uri.parse(
                "https://github.com/aabrole/HuntersRecomp/releases"))));
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.gravity = Gravity.TOP;
        header.addView(version, vlp);
        root.addView(header);

        // ── ROM status / locate flow ─────────────────────────────────────
        romStatus = new TextView(this);
        romStatus.setTextSize(13);
        romStatus.setPadding(dp(4), dp(10), 0, 0);
        root.addView(romStatus);
        locateButton = new Button(this);
        locateButton.setText("LOCATE ROM…");
        locateButton.setTextColor(ACCENT);
        locateButton.setBackgroundColor(CARD);
        locateButton.setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("*/*");
            startActivityForResult(pick, 41);
        });
        root.addView(locateButton, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        refreshRomStatus();

        // ── Play button ──────────────────────────────────────────────────
        Button play = new Button(this);
        play.setText("▶  PLAY");
        play.setTextSize(20);
        play.setTypeface(Typeface.DEFAULT_BOLD);
        play.setTextColor(Color.parseColor("#14100A"));
        GradientDrawable playBg = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{ACCENT, Color.parseColor("#C21A1A")});
        playBg.setCornerRadius(dp(14));
        play.setBackground(playBg);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        playLp.topMargin = dp(18);
        play.setOnClickListener(v -> {
            if (romFile().exists() && romFile().length() == ROM_SIZE) {
                startActivity(new Intent(this, MyGame.class));
            } else {
                // No ROM: send the user through the locate flow instead of
                // booting into a black screen.
                refreshRomStatus();
                locateButton.performClick();
            }
        });
        root.addView(play, playLp);

        // ── Video card ───────────────────────────────────────────────────
        LinearLayout video = card(root, "VIDEO");
        addSpinner(video, "Internal resolution (3D)", "internal_res",
            new String[]{"1x (native 256×192)", "2x", "3x (1344×576)", "4x"},
            new String[]{"1", "2", "3", "4"}, "3");
        addSpinner(video, "Texture upscaling (xBR)", "tex_upscale",
            new String[]{"Off", "2x", "4x"},
            new String[]{"1", "2", "4"}, "2");
        addSpinner(video, "Bottom screen", "bottom_aspect",
            new String[]{"Original 4:3", "Stretch to fill"},
            new String[]{"fit", "stretch"}, "fit");

        // ── Controls card ────────────────────────────────────────────────
        LinearLayout controls = card(root, "CONTROLS");
        addSlider(controls, "Right-stick aim sensitivity", "aim_sens", 10, 400, 100);
        addSlider(controls, "Virtual stylus sensitivity", "stylus_sens", 10, 400, 20);
        addSwitch(controls, "Invert aim Y axis", "invert_y", false);
        addSwitch(controls, "Show FPS counter", "show_fps", false);
        TextView hint = new TextView(this);
        hint.setText("Hold SELECT for fast-forward (skips slow cinematics). "
            + "START skips FMVs in-game.");
        hint.setTextColor(DIM);
        hint.setTextSize(12);
        hint.setPadding(0, dp(8), 0, 0);
        controls.addView(hint);

        // ── Bindings card ────────────────────────────────────────────────
        LinearLayout binds = card(root, "BUTTON BINDINGS");
        TextView bhint = new TextView(this);
        bhint.setText("Touchscreen actions mapped to physical buttons. "
            + "Left stick = move, right stick = aim (fixed).");
        bhint.setTextColor(DIM);
        bhint.setTextSize(12);
        bhint.setPadding(0, 0, 0, dp(8));
        binds.addView(bhint);
        for (String[] action : ACTIONS)
            addBinding(binds, action[1], "bind_" + action[0], action[2]);
        Button reset = new Button(this);
        reset.setText("Reset bindings to defaults");
        reset.setTextColor(ACCENT);
        reset.setBackgroundColor(Color.TRANSPARENT);
        reset.setOnClickListener(v -> {
            SharedPreferences.Editor e = prefs.edit();
            for (String[] action : ACTIONS) e.remove("bind_" + action[0]);
            e.apply();
            recreate();
        });
        binds.addView(reset);

        // ── Diagnostics sharing ──────────────────────────────────────────
        Button share = new Button(this);
        share.setText("SHARE DIAGNOSTICS (for bug reports)");
        share.setTextColor(ACCENT);
        share.setBackgroundColor(CARD);
        share.setOnClickListener(v -> shareDiagnostics());
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        shareLp.topMargin = dp(18);
        root.addView(share, shareLp);

        // ── Credits ──────────────────────────────────────────────────────
        TextView credits = new TextView(this);
        credits.setText("AYN Thor port by Aman Abrole · github.com/aabrole\n"
            + "Built on mstan/MetroidPrimeHuntersRecomp + ndsrecomp\n"
            + "Wi-Fi & GPU foundations: melonDS (GPL-3.0) · "
            + "No game data included — requires your own AMHE-0 ROM dump");
        credits.setTextColor(DIM);
        credits.setTextSize(11);
        credits.setGravity(Gravity.CENTER_HORIZONTAL);
        credits.setPadding(0, dp(20), 0, dp(8));
        root.addView(credits);
    }

    // ── UI helpers ───────────────────────────────────────────────────────
    private LinearLayout card(LinearLayout parent, String heading) {
        TextView head = new TextView(this);
        head.setText(heading);
        head.setTextColor(ACCENT);
        head.setTextSize(13);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setLetterSpacing(0.2f);
        head.setPadding(dp(4), dp(22), 0, dp(6));
        parent.addView(head);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(12));
        card.setBackground(bg);
        int p = dp(14);
        card.setPadding(p, p, p, p);
        parent.addView(card, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private void addSlider(LinearLayout parent, String label, String key,
                           int min, int max, int def) {
        TextView tv = new TextView(this);
        int val = prefs.getInt(key, def);
        tv.setText(label + ": " + val + "%");
        tv.setTextColor(TEXT);
        tv.setTextSize(14);
        parent.addView(tv);
        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(val - min);
        bar.getProgressDrawable().setTint(ACCENT);
        bar.getThumb().setTint(ACCENT);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b, int p, boolean u) {
                int v = min + p;
                tv.setText(label + ": " + v + "%");
                prefs.edit().putInt(key, v).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });
        parent.addView(bar);
    }

    private void addSwitch(LinearLayout parent, String label, String key,
                           boolean def) {
        // Theme-proof toggle: the stock Switch is invisible on this legacy
        // theme, so render our own ON/OFF pill button.
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(TEXT);
        tv.setTextSize(14);
        row.addView(tv, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button pill = new Button(this);
        pill.setAllCaps(true);
        pill.setTextSize(13);
        pill.setTypeface(Typeface.DEFAULT_BOLD);
        pill.setMinWidth(dp(88));
        pill.setMinimumWidth(dp(88));
        java.util.function.Consumer<Boolean> paint = on -> {
            pill.setText(on ? "ON" : "OFF");
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(20));
            bg.setColor(on ? ACCENT : Color.parseColor("#3A414B"));
            pill.setBackground(bg);
            pill.setTextColor(on ? Color.parseColor("#14100A") : DIM);
        };
        paint.accept(prefs.getBoolean(key, def));
        pill.setOnClickListener(v -> {
            boolean next = !prefs.getBoolean(key, def);
            prefs.edit().putBoolean(key, next).apply();
            paint.accept(next);
        });
        row.addView(pill, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        parent.addView(row);
    }

    private void addSpinner(LinearLayout parent, String label, String key,
                            String[] labels, String[] values, String def) {
        addLabeledSpinner(parent, label, labels, values,
            prefs.getString(key, def),
            value -> prefs.edit().putString(key, value).apply());
    }

    private void addBinding(LinearLayout parent, String label, String key,
                            String def) {
        addLabeledSpinner(parent, label, PAD_VALUES, PAD_VALUES,
            prefs.getString(key, def),
            value -> prefs.edit().putString(key, value).apply());
    }

    interface ValueSink { void accept(String value); }

    private void addLabeledSpinner(LinearLayout parent, String label,
                                   String[] labels, String[] values,
                                   String current, ValueSink sink) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(TEXT);
        tv.setTextSize(14);
        row.addView(tv, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Spinner spin = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, labels) {
            @Override public View getView(int pos, View cv, ViewGroup g) {
                TextView v = (TextView) super.getView(pos, cv, g);
                v.setTextColor(ACCENT);
                return v;
            }
        };
        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item);
        spin.setAdapter(adapter);
        int sel = 0;
        for (int i = 0; i < values.length; ++i)
            if (values[i].equals(current)) { sel = i; break; }
        spin.setSelection(sel);
        spin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v,
                                                 int pos, long id) {
                sink.accept(values[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        row.addView(spin);
        parent.addView(row);
    }

    // Zip the diagnostics folder plus a device/settings summary and open the
    // share sheet, so reporters can attach one file to a GitHub issue.
    private void shareDiagnostics() {
        try {
            java.io.File dir = new java.io.File(getExternalFilesDir(null),
                "diagnostics");
            java.io.File zip = new java.io.File(getExternalFilesDir(null),
                "huntersrecomp-diagnostics.zip");
            try (java.util.zip.ZipOutputStream out =
                     new java.util.zip.ZipOutputStream(
                         new java.io.FileOutputStream(zip))) {
                StringBuilder info = new StringBuilder();
                info.append("device: ").append(android.os.Build.MANUFACTURER)
                    .append(" ").append(android.os.Build.MODEL)
                    .append(" (").append(android.os.Build.DEVICE).append(")\n")
                    .append("android: ").append(
                        android.os.Build.VERSION.RELEASE).append("\n")
                    .append("app: ").append(getPackageManager()
                        .getPackageInfo(getPackageName(), 0).versionName)
                    .append("\n").append("settings: ")
                    .append(prefs.getAll().toString()).append("\n");
                out.putNextEntry(new java.util.zip.ZipEntry("device-info.txt"));
                out.write(info.toString().getBytes());
                out.closeEntry();
                java.io.File[] files = dir.listFiles();
                if (files != null) {
                    for (java.io.File f : files) {
                        if (!f.isFile()) continue;
                        out.putNextEntry(
                            new java.util.zip.ZipEntry(f.getName()));
                        try (java.io.FileInputStream in =
                                 new java.io.FileInputStream(f)) {
                            byte[] buf = new byte[1 << 16];
                            int n;
                            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        }
                        out.closeEntry();
                    }
                }
            }
            android.net.Uri uri = androidx.core.content.FileProvider
                .getUriForFile(this, "com.thor.mph.files", zip);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/zip");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send,
                "Share diagnostics zip"));
        } catch (Exception e) {
            android.widget.Toast.makeText(this,
                "Could not build diagnostics zip: " + e.getMessage(),
                android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
