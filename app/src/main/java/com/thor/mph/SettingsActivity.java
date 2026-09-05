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

    private SharedPreferences prefs;

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
        titles.addView(title);
        titles.addView(sub);
        titles.addView(sub2);
        header.addView(titles);
        root.addView(header);

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
        play.setOnClickListener(v ->
            startActivity(new Intent(this, MyGame.class)));
        root.addView(play, playLp);

        // ── Video card ───────────────────────────────────────────────────
        LinearLayout video = card(root, "VIDEO");
        addSpinner(video, "Internal resolution (3D)", "internal_res",
            new String[]{"1x (native 256×192)", "2x", "3x (1344×576)", "4x"},
            new String[]{"1", "2", "3", "4"}, "3");
        addSpinner(video, "Texture upscaling (xBR)", "tex_upscale",
            new String[]{"Off", "2x", "4x"},
            new String[]{"1", "2", "4"}, "2");

        // ── Controls card ────────────────────────────────────────────────
        LinearLayout controls = card(root, "CONTROLS");
        addSlider(controls, "Right-stick aim sensitivity", "aim_sens", 10, 400, 100);
        addSlider(controls, "Virtual stylus sensitivity", "stylus_sens", 10, 400, 20);
        addSwitch(controls, "Invert aim Y axis", "invert_y", false);
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
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextColor(TEXT);
        sw.setTextSize(14);
        sw.setChecked(prefs.getBoolean(key, def));
        sw.setOnCheckedChangeListener((b, checked) ->
            prefs.edit().putBoolean(key, checked).apply());
        parent.addView(sw);
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

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
