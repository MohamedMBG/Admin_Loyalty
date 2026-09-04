package com.example.adminloyalty.utils;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Edge-to-edge inset handling.
 *
 * <p>From target SDK 35 on, Android 15 draws every window edge to edge and ignores
 * {@code android:statusBarColor} / {@code android:navigationBarColor}. Without this the content
 * slides under the status and navigation bars. Padding the root by the bar insets restores the
 * pre-35 layout while the window background paints the bar strips, so the screens look unchanged.</p>
 */
public final class SystemBars {

    private SystemBars() {
    }

    /**
     * Pads {@code root} by the system bars, the display cutout, and the keyboard when it is open.
     * The view's own padding is preserved and added on top. Insets are consumed, so nested views
     * that still declare {@code fitsSystemWindows} do not apply them a second time.
     */
    public static void applyInsetPadding(View root) {
        final int left = root.getPaddingLeft();
        final int top = root.getPaddingTop();
        final int right = root.getPaddingRight();
        final int bottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime());

            view.setPadding(
                    left + bars.left,
                    top + bars.top,
                    right + bars.right,
                    // The keyboard replaces the navigation bar rather than stacking on it.
                    bottom + Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
