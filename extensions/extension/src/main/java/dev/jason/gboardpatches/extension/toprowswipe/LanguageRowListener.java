package dev.jason.gboardpatches.extension.toprowswipe;

import android.content.Context;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import java.util.Locale;

/**
 * Decides whether the A–L home row should show a final {@code L} or {@code Ñ}.
 *
 * English layouts → L
 * Spanish / Latin-with-Ñ (and similar) → Ñ
 *
 * Used by the visual editor label and by SoftKey rebinding for {@link RowType#A_TO_L}.
 */
public final class LanguageRowListener {
    private LanguageRowListener() {
    }

    public enum HomeRowVariant {
        /** Standard English: last key is L */
        ENGLISH_L,
        /** Latin with Ñ: last key is Ñ */
        LATIN_N_TILDE
    }

    /**
     * Inspect the current IME subtype / locale and return the preferred variant.
     * Falls back to ENGLISH_L when detection fails.
     */
    public static HomeRowVariant resolve(Context context) {
        if (context == null) {
            return HomeRowVariant.ENGLISH_L;
        }
        try {
            InputMethodManager imm = (InputMethodManager)
                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm == null) {
                return HomeRowVariant.ENGLISH_L;
            }
            InputMethodSubtype subtype = imm.getCurrentInputMethodSubtype();
            if (subtype != null) {
                String localeStr = subtype.getLocale();
                if (localeStr != null && !localeStr.isEmpty()) {
                    return variantForLocaleTag(localeStr);
                }
                // Extra values sometimes carry language hints
                String extra = subtype.getExtraValue();
                if (extra != null) {
                    String lower = extra.toLowerCase(Locale.US);
                    if (lower.contains("ñ") || lower.contains("spanish")
                            || lower.contains("es-") || lower.contains("locale=es")) {
                        return HomeRowVariant.LATIN_N_TILDE;
                    }
                }
            }
            // Fallback: system locale
            Locale sys = Locale.getDefault();
            if (sys != null) {
                return variantForLocaleTag(sys.toString());
            }
        } catch (Throwable ignored) {
        }
        return HomeRowVariant.ENGLISH_L;
    }

    public static String finalKeyLabel(HomeRowVariant variant) {
        return variant == HomeRowVariant.LATIN_N_TILDE ? "Ñ" : "L";
    }

    public static String finalKeyLabel(Context context) {
        return finalKeyLabel(resolve(context));
    }

    /**
     * Whether the given language/locale tag should use Ñ.
     * Spanish (es), Basque (eu), Galician (gl), Asturian, etc.
     */
    static HomeRowVariant variantForLocaleTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            return HomeRowVariant.ENGLISH_L;
        }
        String lower = tag.toLowerCase(Locale.US).replace('_', '-');
        // Spanish and closely related
        if (lower.startsWith("es")
                || lower.startsWith("eu")
                || lower.startsWith("gl")
                || lower.startsWith("ast")
                || lower.contains("-es")
                || lower.contains("spanish")
                || lower.contains("castellano")) {
            return HomeRowVariant.LATIN_N_TILDE;
        }
        return HomeRowVariant.ENGLISH_L;
    }

    /**
     * Apply the language-aware final key to an A–L slot list in place.
     * If the last slot's display is empty or is L/Ñ, it is updated to match the variant.
     */
    public static void applyToAtoLSlots(Context context, java.util.List<KeySlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return;
        }
        HomeRowVariant variant = resolve(context);
        String label = finalKeyLabel(variant);
        int last = slots.size() - 1;
        KeySlot old = slots.get(last);
        String display = old.displayText != null ? old.displayText.trim() : "";
        if (display.isEmpty() || "L".equalsIgnoreCase(display) || "Ñ".equalsIgnoreCase(display)
                || "N".equalsIgnoreCase(display)) {
            slots.set(last, new KeySlot(
                    label,
                    label,
                    old.isJavaScript,
                    old.scriptText,
                    old.timeoutMs,
                    old.rowType,
                    old.columnIndex));
        }
    }
}
