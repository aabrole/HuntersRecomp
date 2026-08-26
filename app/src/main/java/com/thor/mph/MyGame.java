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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        File dir = getExternalFilesDir(null);
        biosDir = dir.getAbsolutePath();
        File cfg = new File(dir, "game.toml");
        copyAsset("game.toml", cfg);
        cfgPath = cfg.getAbsolutePath();
        romPath = new File(dir, "mph.nds").getAbsolutePath();

        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupSecondDisplay();
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
        return new String[] {
            biosDir, "0",
            "--rom", romPath,
            "--config", cfgPath,
            "--freebios",
            "--boot", "direct",
            "--generated-firmware",
            "--interactive",
            // SDL on Android permits only one window; the SDL window stacks
            // both DS screens on the main display. The bottom screen is ALSO
            // mirrored to the Thor's second physical display via a Presentation.
            "--screen-layout", "stacked",
            "--mph-prime-controls", "on",
            // Enables AMHE0 direct-aim: right-stick (and touch) aim deltas are
            // fed to the game's own aim fields. Without this the right stick
            // moves but never drives the camera. Gates prime controls too
            // (main.cpp: mph_mouse_aim_policy).
            "--relative-mouse-touch", "on",
            "--mph-pad-aim-sensitivity", "100",
            "--no-save",
        };
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

        Presentation p = new Presentation(this, target);
        p.setContentView(view);
        try {
            p.show();
            secondPresentation = p;
        } catch (Exception e) {
            android.util.Log.e("ThorMPH", "second display show failed", e);
        }
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
