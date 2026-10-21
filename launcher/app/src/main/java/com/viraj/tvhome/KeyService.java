package com.viraj.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Opens Settings from remote buttons the stock Google TV launcher used to handle.
 * Matches by scan code because this remote's gear button has no Android keycode of its own.
 */
public class KeyService extends AccessibilityService {
    private int[] settingsScanCodes = new int[0];

    @Override
    protected void onServiceConnected() {
        settingsScanCodes = getResources().getIntArray(R.array.settings_key_scancodes);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (!isSettingsKey(event.getScanCode())) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        return true;
    }

    private boolean isSettingsKey(int scanCode) {
        for (int code : settingsScanCodes) {
            if (code == scanCode) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
