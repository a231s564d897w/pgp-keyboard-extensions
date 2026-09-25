package dev.jason.gboardpatches.extension.accesspoint;

import android.content.Context;
import android.content.res.Resources;

/**
 * Resolve Access Point icons without the Gboard "G" logo when possible.
 */
public final class AccessPointIcons {
    public static final int FALLBACK_G = 0x7f08048b;

    private AccessPointIcons() {
    }

    public static int keyboard(Context context) {
        return first(context,
                new String[] {"ic_menu_edit", "ic_input_get", "ic_dialog_dialer"},
                "quantum_ic_keyboard_white_24", "ic_keyboard_white_24dp", "ic_keyboard");
    }

    public static int calculator(Context context) {
        return first(context,
                new String[] {"ic_menu_sort_by_size", "ic_input_add", "ic_menu_agenda"},
                "quantum_ic_calculate_white_24", "ic_calculate_white_24dp", "ic_calculator");
    }

    public static int pass(Context context) {
        return first(context,
                new String[] {"ic_lock_lock", "ic_lock_idle_lock", "ic_menu_manage"},
                "quantum_ic_vpn_key_white_24", "quantum_ic_lock_white_24", "ic_vpn_key_white_24dp");
    }

    public static int browser(Context context) {
        return first(context,
                new String[] {"ic_menu_search", "ic_menu_view", "ic_dialog_info"},
                "quantum_ic_public_white_24", "quantum_ic_language_white_24", "ic_public_white_24dp");
    }

    private static int first(Context context, String[] androidNames, String... gboardNames) {
        if (context == null) {
            return FALLBACK_G;
        }
        try {
            Resources res = Resources.getSystem();
            for (String name : androidNames) {
                int id = res.getIdentifier(name, "drawable", "android");
                if (id != 0) {
                    return id;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Resources res = context.getResources();
            String pkg = context.getPackageName();
            for (String name : gboardNames) {
                int id = res.getIdentifier(name, "drawable", pkg);
                if (id != 0) {
                    return id;
                }
            }
        } catch (Throwable ignored) {
        }
        return FALLBACK_G;
    }
}
