package dev.jason.gboardpatches.extension.accesspoint;

import android.content.Context;
import android.content.res.Resources;

/**
 * Modern filled Access Point icons (PGP vectors first, then Material / system).
 */
public final class AccessPointIcons {
    public static final int FALLBACK_G = 0x7f08048b;

    private AccessPointIcons() {
    }

    public static int keyboard(Context context) {
        return first(context,
                "ic_pgp_keyboard",
                new String[] {"ic_menu_edit", "ic_dialog_dialer"},
                "quantum_ic_keyboard_white_24", "ic_keyboard_white_24dp", "ic_keyboard");
    }

    public static int calculator(Context context) {
        return first(context,
                "ic_pgp_calculator",
                new String[] {"ic_menu_sort_by_size", "ic_input_add"},
                "quantum_ic_calculate_white_24", "ic_calculate_white_24dp", "ic_calculator",
                "quantum_ic_exposure_white_24");
    }

    public static int pass(Context context) {
        return first(context,
                "ic_pgp_pass",
                new String[] {"ic_lock_lock", "ic_lock_idle_lock"},
                "quantum_ic_vpn_key_white_24", "quantum_ic_lock_white_24", "ic_vpn_key_white_24dp",
                "quantum_ic_key_white_24");
    }

    public static int browser(Context context) {
        return first(context,
                "ic_pgp_browser",
                new String[] {"ic_menu_search", "ic_menu_view"},
                "quantum_ic_travel_explore_white_24", "quantum_ic_language_white_24",
                "quantum_ic_public_white_24", "ic_public_white_24dp", "ic_search_white_24dp");
    }

    private static int first(Context context, String pgpName, String[] androidNames,
            String... gboardNames) {
        if (context == null) {
            return FALLBACK_G;
        }
        // 1) Our modern vector drawables in the extension package
        try {
            Resources res = context.getResources();
            String pkg = context.getPackageName();
            int id = res.getIdentifier(pgpName, "drawable", pkg);
            if (id != 0) {
                return id;
            }
            // Extension resources sometimes merge under a different package
            id = res.getIdentifier(pgpName, "drawable", "dev.jason.gboardpatches");
            if (id != 0) {
                return id;
            }
        } catch (Throwable ignored) {
        }
        // 2) Android system icons
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
        // 3) Gboard / Material quantum icons present in the host APK
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
