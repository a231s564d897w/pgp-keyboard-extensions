package dev.jason.gboardpatches.extension.accesspoint;

import android.content.Context;
import android.content.res.Resources;

/**
 * Resolve toolbar / Access Point icons from Gboard's own drawables by name.
 * Falls back to a known stock id when the name is missing on a given build.
 */
public final class AccessPointIcons {
    /** Last-resort stock G logo used by upstream web-search contribution. */
    public static final int FALLBACK_G = 0x7f08048b;

    private AccessPointIcons() {
    }

    public static int keyboard(Context context) {
        return first(context, FALLBACK_G,
                "quantum_ic_keyboard_white_24",
                "quantum_ic_keyboard_grey600_24",
                "ic_keyboard_white_24dp",
                "ic_keyboard",
                "gboard_keyboard_icon",
                "product_logo_keyboard_color_24");
    }

    public static int calculator(Context context) {
        return first(context, FALLBACK_G,
                "quantum_ic_calculate_white_24",
                "quantum_ic_calculator_white_24",
                "ic_calculate_white_24dp",
                "ic_calculator",
                "quantum_ic_exposure_white_24");
    }

    public static int pass(Context context) {
        return first(context, FALLBACK_G,
                "quantum_ic_vpn_key_white_24",
                "quantum_ic_lock_white_24",
                "quantum_ic_key_white_24",
                "ic_vpn_key_white_24dp",
                "ic_lock_white_24dp",
                "ic_password");
    }

    public static int browser(Context context) {
        return first(context, FALLBACK_G,
                "quantum_ic_public_white_24",
                "quantum_ic_language_white_24",
                "quantum_ic_open_in_browser_white_24",
                "ic_public_white_24dp",
                "ic_language_white_24dp",
                "ic_web");
    }

    private static int first(Context context, int fallback, String... names) {
        if (context == null) {
            return fallback;
        }
        try {
            Resources res = context.getResources();
            String pkg = context.getPackageName();
            for (String name : names) {
                int id = res.getIdentifier(name, "drawable", pkg);
                if (id != 0) {
                    return id;
                }
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }
}
