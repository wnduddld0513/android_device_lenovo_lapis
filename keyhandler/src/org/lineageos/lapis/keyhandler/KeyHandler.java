/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lapis.keyhandler;

import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import com.android.internal.os.DeviceKeyHandler;

/**
 * Folio cover: puts the device to sleep when the cover is closed and wakes it
 * when the cover is opened.
 *
 * The cover magnet is reported by the wake-up hall effect sensor of the sensor
 * HAL (qti.sensor.hall_effect). The hall_irq input device only advertises
 * KEY 0x2ee-0x2f1 and never reports them: its two hall sensors serve the pen
 * charger. A magnet near the back of the tablet (keyboard folded behind it)
 * also trips the hall sensor, so a closed event is confirmed by the light
 * sensor reading dark before the device goes to sleep.
 *
 * Stylus: the display driver turns the stylus scanning of the touch controller
 * off at 90 and 144 Hz (dsi_panel_match_fps_setting), but the touch controller
 * still reports a stylus near the screen with KEY_SKIN_STYLUS, which has no key
 * code. While it comes, an invisible overlay window asks for a 120 Hz render
 * rate, which no 90 or 144 Hz mode can give, so the display stays at 120 Hz
 * (or lower, under a higher priority limit). The panel has no VRR, so
 * DisplayManager#requestDisplayModes() is ignored. The window goes away
 * STYLUS_TIMEOUT_MS after the last report, like the stock SurfaceFlinger pen
 * timer (ro.surface_flinger.set_pen_timer_ms).
 */
public class KeyHandler implements DeviceKeyHandler {
    private static final String TAG = "LapisFolio";

    private static final int TYPE_HALL_EFFECT = 33171002;
    private static final float COVERED_MAX_LUX = 20f;

    private static final int SCAN_CODE_STYLUS_NEAR = 249; // KEY_SKIN_STYLUS
    private static final float STYLUS_REFRESH_RATE = 120f;
    private static final long STYLUS_TIMEOUT_MS = 10000;

    private final Context mContext;
    private final PowerManager mPowerManager;
    private final SensorManager mSensorManager;
    private final Sensor mLightSensor;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private boolean mLightRegistered;
    // The cover was closed on a dark screen, so opening it wakes the device
    private boolean mWakeOnOpen;

    // Created on the first stylus report: the window manager is not up yet
    // when the key handler is
    private Context mWindowContext;
    // Shown while the stylus is near
    private View mStylusView;
    private final Runnable mStylusGoneRunnable = this::onStylusGone;

    private final SensorEventListener mHallListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            // 1: cover away (open), anything else: cover near (closed)
            onCover((int) event.values[0] != 1);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private final SensorEventListener mLightListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.values[0] <= COVERED_MAX_LUX) {
                unregisterLight();
                sleepIfInteractive();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    public KeyHandler(Context context) {
        mContext = context;
        mPowerManager = context.getSystemService(PowerManager.class);
        mSensorManager = context.getSystemService(SensorManager.class);
        mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        // The wake-up variant also reports while the screen is off
        final Sensor hall = mSensorManager.getDefaultSensor(TYPE_HALL_EFFECT, true);
        if (hall == null) {
            Log.w(TAG, "No hall effect sensor, folio cover disabled");
            return;
        }
        mSensorManager.registerListener(mHallListener, hall, SensorManager.SENSOR_DELAY_NORMAL,
                mHandler);
    }

    @Override
    public KeyEvent handleKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_UNKNOWN
                || event.getScanCode() != SCAN_CODE_STYLUS_NEAR) {
            return event;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mHandler.post(this::onStylusNear);
        }
        // Only the refresh rate needs it; apps never see it
        return null;
    }

    private void onStylusNear() {
        mHandler.removeCallbacks(mStylusGoneRunnable);
        mHandler.postDelayed(mStylusGoneRunnable, STYLUS_TIMEOUT_MS);
        if (mStylusView != null) {
            return;
        }
        if (mWindowContext == null) {
            final Display display = mContext.getSystemService(DisplayManager.class)
                    .getDisplay(Display.DEFAULT_DISPLAY);
            mWindowContext = mContext.createDisplayContext(display).createWindowContext(
                    WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY, null);
        }
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(1, 1,
                WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.setTitle("LapisStylus");
        // An animating window cannot ask for a refresh rate
        lp.windowAnimations = 0;
        lp.preferredMinDisplayRefreshRate = STYLUS_REFRESH_RATE;
        lp.preferredMaxDisplayRefreshRate = STYLUS_REFRESH_RATE;
        final View view = new View(mWindowContext);
        try {
            mWindowContext.getSystemService(WindowManager.class).addView(view, lp);
        } catch (RuntimeException e) {
            // Runs in system_server: losing the stylus refresh rate beats a crash
            Log.e(TAG, "Failed to add the stylus window", e);
            return;
        }
        mStylusView = view;
    }

    private void onStylusGone() {
        if (mStylusView != null) {
            mWindowContext.getSystemService(WindowManager.class).removeView(mStylusView);
            mStylusView = null;
        }
    }

    private void onCover(boolean closed) {
        if (!closed) {
            unregisterLight();
            if (mWakeOnOpen && !mPowerManager.isInteractive()) {
                mPowerManager.wakeUp(SystemClock.uptimeMillis(), PowerManager.WAKE_REASON_LID,
                        "lapis:folio");
            }
            mWakeOnOpen = false;
        } else if (!mPowerManager.isInteractive()) {
            // Closed on a screen that is already off: opening wakes it
            mWakeOnOpen = true;
        } else if (mLightSensor == null) {
            sleepIfInteractive();
        } else if (!mLightRegistered) {
            mLightRegistered = mSensorManager.registerListener(mLightListener, mLightSensor,
                    SensorManager.SENSOR_DELAY_NORMAL, mHandler);
        }
    }

    private void sleepIfInteractive() {
        if (mPowerManager.isInteractive()) {
            mWakeOnOpen = true;
            mPowerManager.goToSleep(SystemClock.uptimeMillis(),
                    PowerManager.GO_TO_SLEEP_REASON_LID_SWITCH, 0);
        }
    }

    private void unregisterLight() {
        if (mLightRegistered) {
            mSensorManager.unregisterListener(mLightListener);
            mLightRegistered = false;
        }
    }
}
