package dev.jason.gboardpatches.extension.debug;

/**
 * Runs offline pure-logic checks and writes results to {@link GboardDebugPanel}.
 */
public final class GboardPatchesSelfTest {
    private GboardPatchesSelfTest() {
    }

    public static void runAll() {
        int pass = 0;
        int fail = 0;
        try {
            dev.jason.gboardpatches.extension.calculator.GboardCalculatorEngineTest.runAll();
            GboardDebugPanel.log("SelfTest", "CalculatorEngine OK");
            pass++;
        } catch (Throwable t) {
            GboardDebugPanel.log("SelfTest", "CalculatorEngine FAIL: " + t.getMessage());
            fail++;
        }
        try {
            dev.jason.gboardpatches.extension.toprowswipe.KeyRowPresetJsonTest.runAll();
            GboardDebugPanel.log("SelfTest", "KeyRowPreset JSON OK");
            pass++;
        } catch (Throwable t) {
            GboardDebugPanel.log("SelfTest", "KeyRowPreset JSON FAIL: " + t.getMessage());
            fail++;
        }
        try {
            String v = dev.jason.gboardpatches.extension.calculator.GboardCalculatorEngine
                    .evaluate("sqrt(16)+2^3");
            if (!"12".equals(v) && !"12.0".equals(v)) {
                // engine may format as 12
                if (v == null || !v.startsWith("12")) {
                    throw new AssertionError("sqrt(16)+2^3 => " + v);
                }
            }
            GboardDebugPanel.log("SelfTest", "Compound expr OK (" + v + ")");
            pass++;
        } catch (Throwable t) {
            GboardDebugPanel.log("SelfTest", "Compound expr FAIL: " + t.getMessage());
            fail++;
        }
        GboardDebugPanel.log("SelfTest", "Done pass=" + pass + " fail=" + fail);
    }
}
