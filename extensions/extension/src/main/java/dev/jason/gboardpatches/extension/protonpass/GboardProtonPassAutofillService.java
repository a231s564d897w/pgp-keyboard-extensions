package dev.jason.gboardpatches.extension.protonpass;

import android.app.assist.AssistStructure;
import android.os.CancellationSignal;
import android.service.autofill.AutofillService;
import android.service.autofill.FillCallback;
import android.service.autofill.FillRequest;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveCallback;
import android.service.autofill.SaveRequest;
import android.util.Log;
import android.view.autofill.AutofillId;
import android.view.inputmethod.EditorInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin AutofillService that surfaces password fields to the Proton Pass overlay.
 *
 * This does not replace Proton Pass as a password manager. It:
 * 1. Observes fill requests (package + field hints)
 * 2. Publishes whatever credential list was last provided via
 *    {@link ProtonPassAutofillBridge#publishEntries}
 * 3. Leaves full vault unlock / crypto to the Proton Pass app
 *
 * Enable under system Settings → Passwords & accounts → Autofill service
 * (user must pick this service; requires manifest entry below).
 *
 * Manifest (merge into the host APK):
 * <pre>
 * &lt;service
 *   android:name="dev.jason.gboardpatches.extension.protonpass.GboardProtonPassAutofillService"
 *   android:permission="android.permission.BIND_AUTOFILL_SERVICE"
 *   android:exported="true"&gt;
 *   &lt;intent-filter&gt;
 *     &lt;action android:name="android.service.autofill.AutofillService"/&gt;
 *   &lt;/intent-filter&gt;
 *   &lt;meta-data
 *     android:name="android.autofill"
 *     android:resource="@xml/gboard_patches_autofill_service"/&gt;
 * &lt;/service&gt;
 * </pre>
 */
public class GboardProtonPassAutofillService extends AutofillService {
    private static final String TAG = "PGP";

    @Override
    public void onFillRequest(FillRequest request, CancellationSignal cancellationSignal,
            FillCallback callback) {
        try {
            String pkg = null;
            if (request != null && request.getFillContexts() != null
                    && !request.getFillContexts().isEmpty()) {
                AssistStructure structure = request.getFillContexts()
                        .get(request.getFillContexts().size() - 1).getStructure();
                if (structure != null) {
                    pkg = structure.getActivityComponent() != null
                            ? structure.getActivityComponent().getPackageName()
                            : null;
                }
            }
            ProtonPassAutofillBridge.onEditorInfo(fakeEditorInfo(pkg));
            try {
                dev.jason.gboardpatches.extension.debug.GboardDebugPanel.log(
                        "Autofill", "onFillRequest pkg=" + pkg);
            } catch (Throwable ignored) {
            }
            // Return empty system FillResponse – we use the floating overlay instead
            // of the system Autofill UI. Credential data must be published separately
            // (demo button, future Proton Pass SDK, or companion).
            if (callback != null) {
                callback.onSuccess(null);
            }
        } catch (Throwable t) {
            Log.w(TAG, "onFillRequest failed", t);
            if (callback != null) {
                try {
                    callback.onSuccess(null);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @Override
    public void onSaveRequest(SaveRequest request, SaveCallback callback) {
        if (callback != null) {
            try {
                callback.onSuccess();
            } catch (Throwable ignored) {
            }
        }
    }

    private static EditorInfo fakeEditorInfo(String packageName) {
        EditorInfo info = new EditorInfo();
        info.packageName = packageName;
        return info;
    }
}
