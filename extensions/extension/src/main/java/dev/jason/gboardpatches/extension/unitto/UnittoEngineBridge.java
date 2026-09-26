package dev.jason.gboardpatches.extension.unitto;

import android.util.Log;

/**
 * PGP Path B/C bridge: prefer Unitto library AAR classes when present on classpath,
 * else {@link UnittoEvaluattoEngine} (Path A, always available).
 *
 * Real AAR types are loaded by reflection so the extension still compiles before
 * unitto-engine.aar is dropped into libs/.
 */
public final class UnittoEngineBridge {
    private static final String TAG = "PGP";

    /** Possible fully-qualified Expression types from vendored Unitto library. */
    private static final String[] AAR_EXPRESSION_CLASSES = {
            "io.github.sadellie.evaluatto.Expression",
            "com.sadellie.unitto.core.evaluatto.Expression",
    };

    private UnittoEngineBridge() {
    }

    public static String evaluate(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return "";
        }
        String fromAar = tryAarEvaluate(expression);
        if (fromAar != null) {
            return fromAar;
        }
        return UnittoEvaluattoEngine.evaluate(expression);
    }

    private static String tryAarEvaluate(String expression) {
        for (String className : AAR_EXPRESSION_CLASSES) {
            try {
                Class<?> cls = Class.forName(className);
                Object expr = cls.getConstructor(String.class).newInstance(expression);
                Object result = cls.getMethod("calculate").invoke(expr);
                if (result != null) {
                    return stripTrailingZeros(result.toString());
                }
            } catch (ClassNotFoundException ignored) {
                // AAR not on classpath yet
            } catch (Throwable t) {
                Log.d(TAG, "AAR evaluate via " + className + ": " + t.getMessage());
            }
        }
        return null;
    }

    private static String stripTrailingZeros(String s) {
        if (s == null) {
            return "";
        }
        if (s.indexOf('.') < 0) {
            return s;
        }
        while (s.endsWith("0")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? "0" : s;
    }
}
