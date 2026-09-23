package dev.jason.gboardpatches.extension.calculator;

import android.content.Context;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Access Point contribution for the scientific calculator floating window.
 *
 * Registers a button in the Access Points menu that can be dragged onto the
 * top toolbar. Tapping it opens {@link GboardCalculatorOverlayHost}.
 */
public final class GboardCalculatorAccessPoint1803Contribution {
    public static final GboardCalculatorAccessPoint1803Contribution INSTANCE =
            new GboardCalculatorAccessPoint1803Contribution();

    public static final String TOKEN = "calculator";

    /** Re-use a built-in drawable for now; replace with a dedicated icon later. */
    static final int ICON_DRAWABLE_ID = 0x7f08048b;

    private static volatile Handles handles;

    private GboardCalculatorAccessPoint1803Contribution() {
    }

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
            active.builderIconResourceMethod.invoke(builder,
                    dev.jason.gboardpatches.extension.accesspoint.AccessPointIcons.calculator(safeContext));
            active.builderLabelTextField.set(builder, "Calculator");
            active.builderContentDescriptionTextField.set(builder, "Calculator");
            active.builderRunnableMethod.invoke(builder, new OpenAction(safeContext));
            Object descriptor = active.builderBuildMethod.invoke(builder);
            active.controllerRegisterMethod.invoke(controller, descriptor, false);
        } catch (Throwable ignored) {
            // Fail closed
        }
    }

    static boolean isEnabled(Context context) {
        if (context == null) {
            return false;
        }
        return dev.jason.gboardpatches.extension.overlay.OverlayFeatureSettings
                .isCalculatorEnabled(context);
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
        synchronized (GboardCalculatorAccessPoint1803Contribution.class) {
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
                    GboardCalculatorOverlayHost.setInputConnection(ic);
                } catch (Throwable ignoredIc) {
                }
                // Primary: Unitto Compose/Web floating experience
                try {
                    if (dev.jason.gboardpatches.extension.unitto.UnittoComposeOverlayHost.isShowing()) {
                        dev.jason.gboardpatches.extension.unitto.UnittoComposeOverlayHost.hide(context);
                    } else {
                        // Hide lightweight keypad if open
                        if (GboardCalculatorOverlayHost.isShowing()) {
                            GboardCalculatorOverlayHost.hide(context);
                        }
                        if (!dev.jason.gboardpatches.extension.unitto.UnittoComposeOverlayHost.show(context)) {
                            // Fall back to offline keypad
                            GboardCalculatorOverlayHost.show(context);
                        }
                    }
                } catch (Throwable t) {
                    if (GboardCalculatorOverlayHost.isShowing()) {
                        GboardCalculatorOverlayHost.hide(context);
                    } else {
                        GboardCalculatorOverlayHost.show(context);
                    }
                }
            } catch (Throwable ignored) {
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
