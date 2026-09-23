package dev.jason.gboardpatches.extension.toprowswipe;

import android.content.Context;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Access Points for Custom Key Row presets (drag to top toolbar).
 *
 * Tokens:
 * - {@code custom_key_row_preset} – re-apply the current active preset
 * - {@code custom_key_row_preset_<id>} – one per saved preset (activate that preset)
 *
 * Deleted / disabled presets simply stop being registered on the next controller pass.
 */
public final class GboardPresetAccessPoint1803Contribution {
    public static final GboardPresetAccessPoint1803Contribution INSTANCE =
            new GboardPresetAccessPoint1803Contribution();

    public static final String TOKEN_ACTIVE = "custom_key_row_preset";
    public static final String TOKEN_PREFIX = "custom_key_row_preset_";

    static final int ICON_DRAWABLE_ID = 0x7f08048b;

    private static volatile Handles handles;

    private GboardPresetAccessPoint1803Contribution() {
    }

    public static String tokenForPresetId(String id) {
        if (id == null || id.isEmpty()) {
            return TOKEN_ACTIVE;
        }
        return TOKEN_PREFIX + id;
    }

    public Object extendOrderCatalog(Context context, Object original) {
        try {
            if (!(original instanceof Collection<?> collection)) {
                return original;
            }
            List<String> values = new ArrayList<>();
            for (Object item : collection) {
                if (item instanceof String) {
                    values.add((String) item);
                }
            }
            if (!values.contains(TOKEN_ACTIVE)) {
                values.add(TOKEN_ACTIVE);
            }
            if (context != null) {
                for (KeyRowPreset p : KeyRowPresetStore.readAll(context)) {
                    if (p == null || p.id == null) {
                        continue;
                    }
                    String token = tokenForPresetId(p.id);
                    if (!values.contains(token)) {
                        values.add(token);
                    }
                }
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
            if (controller == null || context == null) {
                return;
            }
            Context application = context.getApplicationContext();
            Context safeContext = application != null ? application : context;
            Handles active = handles(controller.getClass().getClassLoader());

            // Generic "active preset" button
            registerOne(active, controller, safeContext, TOKEN_ACTIVE, "PGP Keys", null);

            // One button per saved preset
            for (KeyRowPreset p : KeyRowPresetStore.readAll(safeContext)) {
                if (p == null || p.id == null) {
                    continue;
                }
                String label = p.name != null && !p.name.isEmpty() ? p.name : "Preset";
                if (label.length() > 14) {
                    label = label.substring(0, 14);
                }
                registerOne(active, controller, safeContext,
                        tokenForPresetId(p.id), label, p.id);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void registerOne(Handles active, Object controller, Context context,
            String token, String label, String presetIdOrNull) throws Throwable {
        Object builder = active.descriptorBuilderFactory.invoke(null);
        active.builderTokenMethod.invoke(builder, token);
        active.builderIconResourceMethod.invoke(builder, ICON_DRAWABLE_ID);
        active.builderLabelTextField.set(builder, label);
        active.builderContentDescriptionTextField.set(builder,
                "Custom Key Row: " + label);
        active.builderRunnableMethod.invoke(builder,
                new ActivatePresetAction(context, presetIdOrNull));
        Object descriptor = active.builderBuildMethod.invoke(builder);
        active.controllerRegisterMethod.invoke(controller, descriptor, false);
    }

    private static Handles handles(ClassLoader classLoader) throws Throwable {
        Handles cached = handles;
        if (cached != null && cached.classLoader == classLoader) {
            return cached;
        }
        Handles resolved = Handles.resolve(classLoader);
        handles = resolved;
        return resolved;
    }

    /**
     * @param presetId if null, re-apply whatever is currently active;
     *                 otherwise switch active to that id first.
     */
    private static final class ActivatePresetAction implements Runnable {
        private final WeakReference<Context> contextReference;
        private final String presetId;

        ActivatePresetAction(Context context, String presetId) {
            contextReference = new WeakReference<>(context);
            this.presetId = presetId;
        }

        @Override
        public void run() {
            Context context = contextReference.get();
            if (context == null) {
                return;
            }
            try {
                if (presetId != null) {
                    KeyRowPresetStore.writeActivePresetId(context, presetId);
                }
                KeyRowPreset active = KeyRowPresetStore.readActivePreset(context);
                if (active == null) {
                    Toast.makeText(context, "No active preset", Toast.LENGTH_SHORT).show();
                    return;
                }
                List<KeySlot> qRow = active.slotsForRow(RowType.Q_TO_P);
                for (int i = 0; i < GboardTopRowSwipeSettings.SLOT_COUNT; i++) {
                    GboardTopRowSwipeSettings.SlotText legacy =
                            i < qRow.size()
                                    ? qRow.get(i).toLegacy()
                                    : new GboardTopRowSwipeSettings.SlotText("", "");
                    GboardTopRowSwipeSettings.writeSlot(context, i, legacy);
                }
                CustomKeyRowRuntime.invalidate();
                Toast.makeText(context, "Applied: " + active.name, Toast.LENGTH_SHORT).show();
                try {
                    dev.jason.gboardpatches.extension.debug.GboardDebugPanel.log(
                            "Preset", "toolbar apply id=" + active.id + " name=" + active.name);
                } catch (Throwable ignored) {
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
