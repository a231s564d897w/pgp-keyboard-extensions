package dev.jason.gboardpatches.extension.protonpass;

import android.content.Context;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Access Point contribution for the Proton Pass floating credentials window.
 *
 * Registers a button in the Access Points menu that can be dragged onto the
 * top toolbar. Tapping it opens {@link GboardProtonPassOverlayHost}.
 *
 * Pattern mirrors {@code GboardFloatingWebSearchAccessPoint1803Contribution}.
 */
public final class GboardProtonPassAccessPoint1803Contribution {
    public static final GboardProtonPassAccessPoint1803Contribution INSTANCE =
            new GboardProtonPassAccessPoint1803Contribution();

    /** Token used in the Access Point order catalog (must be unique). */
    public static final String TOKEN = "proton_pass";

    /**
     * Re-use a built-in Gboard drawable for now (same as Web Search).
     * Replace with a dedicated Proton Pass icon resource later.
     */
    static final int ICON_DRAWABLE_ID = 0x7f08048b;

    private static volatile Handles handles;

    private GboardProtonPassAccessPoint1803Contribution() {
    }

    /**
     * Ensures {@link #TOKEN} appears in the Access Point order catalog so the
     * button can be shown in the menu and dragged to the toolbar.
     */
    public Object extendOrderCatalog(Context context, Object original) {
        try {
            if (!isEnabled(context) || !(original instanceof Collection<?> collection)) {
                return original;
            }
            List<String> values = copyStrings(collection);
            if (!values.contains(TOKEN)) {
                values.add(TOKEN);
            }
            Class<?> immutableCollection = Class.forName(
                    "vxe", false, original.getClass().getClassLoader());
            Method copy = immutableCollection.getDeclaredMethod("n", Collection.class);
            copy.setAccessible(true);
            return copy.invoke(null, values);
        } catch (Throwable ignored) {
            return original;
        }
    }

    /**
     * Registers the Access Point descriptor with the controller so the button
     * appears in the menu and can be pinned to the top toolbar.
     */
    public void register(Object controller, Context context) {
        try {
            if (controller == null || context == null || !isEnabled(context)) {
                return;
            }
            Context application = context.getApplicationContext();
            Context safeContext = application != null ? application : context;
            Handles active = handles(controller.getClass().getClassLoader());
            Object builder = active.descriptorBuilderFactory.invoke(null);
            active.builderTokenMethod.invoke(builder, TOKEN);
            active.builderIconResourceMethod.invoke(builder, ICON_DRAWABLE_ID);
            active.builderLabelTextField.set(builder, "Proton Pass");
            active.builderContentDescriptionTextField.set(builder, "Proton Pass");
            active.builderRunnableMethod.invoke(builder, new OpenAction(safeContext));
            Object descriptor = active.builderBuildMethod.invoke(builder);
            // false = not permanently forced onto the toolbar; user can drag it there
            active.controllerRegisterMethod.invoke(controller, descriptor, false);
        } catch (Throwable ignored) {
            // A synthetic Access Point must fail closed.
        }
    }

    static boolean isEnabled(Context context) {
        if (context == null) {
            return false;
        }
        return dev.jason.gboardpatches.extension.overlay.OverlayFeatureSettings
                .isProtonPassEnabled(context);
    }

    static List<String> copyStrings(Collection<?> values) {
        List<String> result = new ArrayList<>();
        if (values != null) {
            for (Object value : values) {
                if (value instanceof String stringValue && !result.contains(stringValue)) {
                    result.add(stringValue);
                }
            }
        }
        return result;
    }

    private static Handles handles(ClassLoader classLoader) throws Throwable {
        Handles current = handles;
        if (current != null && current.classLoader == classLoader) {
            return current;
        }
        synchronized (GboardProtonPassAccessPoint1803Contribution.class) {
            current = handles;
            if (current == null || current.classLoader != classLoader) {
                current = Handles.resolve(classLoader);
                handles = current;
            }
            return current;
        }
    }

    private static final class OpenAction implements Runnable {
        private final WeakReference<Context> contextReference;

        OpenAction(Context context) {
            contextReference = new WeakReference<>(context);
        }

        @Override
        public void run() {
            Context context = contextReference.get();
            if (context == null) {
                return;
            }
            try {
                try {
                    android.view.inputmethod.InputConnection ic =
                            dev.jason.gboardpatches.extension.overlay.InputConnectionBridge
                                    .refreshFrom(context);
                    GboardProtonPassOverlayHost.setInputConnection(ic);
                } catch (Throwable ignoredIc) {
                }
                if (GboardProtonPassOverlayHost.isShowing()) {
                    GboardProtonPassOverlayHost.hide(context);
                } else {
                    GboardProtonPassOverlayHost.show(context);
                }
            } catch (Throwable ignored) {
                // Fail closed – never crash Gboard from an Access Point action
            }
        }
    }

    private static final class Handles {
        final ClassLoader classLoader;
        final Method descriptorBuilderFactory;
        final Method builderTokenMethod;
        final Method builderRunnableMethod;
        final Method builderBuildMethod;
        final Method builderIconResourceMethod;
        final Field builderLabelTextField;
        final Field builderContentDescriptionTextField;
        final Method controllerRegisterMethod;

        Handles(ClassLoader classLoader, Method descriptorBuilderFactory,
                Method builderTokenMethod, Method builderRunnableMethod,
                Method builderBuildMethod, Method builderIconResourceMethod,
                Field builderLabelTextField, Field builderContentDescriptionTextField,
                Method controllerRegisterMethod) {
            this.classLoader = classLoader;
            this.descriptorBuilderFactory = descriptorBuilderFactory;
            this.builderTokenMethod = builderTokenMethod;
            this.builderRunnableMethod = builderRunnableMethod;
            this.builderBuildMethod = builderBuildMethod;
            this.builderIconResourceMethod = builderIconResourceMethod;
            this.builderLabelTextField = builderLabelTextField;
            this.builderContentDescriptionTextField = builderContentDescriptionTextField;
            this.controllerRegisterMethod = controllerRegisterMethod;
        }

        static Handles resolve(ClassLoader classLoader) throws Throwable {
            Class<?> descriptor = Class.forName("mic", false, classLoader);
            Class<?> builder = Class.forName("mhx", false, classLoader);
            Class<?> controller = Class.forName("mlh", false, classLoader);
            Method descriptorBuilderFactory = descriptor.getDeclaredMethod("c");
            Method builderTokenMethod = builder.getDeclaredMethod("l", String.class);
            Method builderRunnableMethod = builder.getDeclaredMethod("q", Runnable.class);
            Method builderBuildMethod = builder.getDeclaredMethod("a");
            Method builderIconResourceMethod = builder.getDeclaredMethod("i", int.class);
            Field builderLabelTextField = builder.getDeclaredField("d");
            Field builderContentDescriptionTextField = builder.getDeclaredField("e");
            Method controllerRegisterMethod = controller.getDeclaredMethod(
                    "g", descriptor, boolean.class);
            descriptorBuilderFactory.setAccessible(true);
            builderTokenMethod.setAccessible(true);
            builderRunnableMethod.setAccessible(true);
            builderBuildMethod.setAccessible(true);
            builderIconResourceMethod.setAccessible(true);
            builderLabelTextField.setAccessible(true);
            builderContentDescriptionTextField.setAccessible(true);
            controllerRegisterMethod.setAccessible(true);
            return new Handles(classLoader, descriptorBuilderFactory, builderTokenMethod,
                    builderRunnableMethod, builderBuildMethod, builderIconResourceMethod,
                    builderLabelTextField, builderContentDescriptionTextField,
                    controllerRegisterMethod);
        }
    }
}
