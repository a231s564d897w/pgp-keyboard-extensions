package dev.jason.gboardpatches.extension.unitto;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Path C: bundled Unitto-style data under assets/pgp-unitto/ (install-time pack).
 */
public final class PgpUnittoAssets {
    private static final String TAG = "PGP";
    private static final String PREFIX = "pgp-unitto/";

    private PgpUnittoAssets() {
    }

    public static String readAsset(Context context, String relativePath) {
        if (context == null || relativePath == null) {
            return null;
        }
        try (InputStream in = context.getAssets().open(PREFIX + relativePath);
             BufferedReader br = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Throwable t) {
            Log.d(TAG, "asset " + relativePath + ": " + t.getMessage());
            return null;
        }
    }

    public static Double convertLength(Context context, double value, String from, String to) {
        return convertLinear(context, "length_to_meter.json", value, from, to);
    }

    public static Double convertMass(Context context, double value, String from, String to) {
        return convertLinear(context, "mass_to_kg.json", value, from, to);
    }

    public static Double convertTemp(double value, String from, String to) {
        try {
            double c;
            switch (from.toUpperCase()) {
                case "C":
                    c = value;
                    break;
                case "F":
                    c = (value - 32.0) * 5.0 / 9.0;
                    break;
                case "K":
                    c = value - 273.15;
                    break;
                default:
                    return null;
            }
            switch (to.toUpperCase()) {
                case "C":
                    return c;
                case "F":
                    return c * 9.0 / 5.0 + 32.0;
                case "K":
                    return c + 273.15;
                default:
                    return null;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static Double convertLinear(Context context, String asset, double value,
            String from, String to) {
        try {
            String json = readAsset(context, asset);
            if (json == null) {
                return null;
            }
            JSONObject map = new JSONObject(json);
            double fromBase = map.getDouble(from.toLowerCase());
            double toBase = map.getDouble(to.toLowerCase());
            return value * fromBase / toBase;
        } catch (Throwable t) {
            return null;
        }
    }
}
