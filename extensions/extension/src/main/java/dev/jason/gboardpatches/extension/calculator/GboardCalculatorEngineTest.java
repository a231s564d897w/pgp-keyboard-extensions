package dev.jason.gboardpatches.extension.calculator;

/**
 * Lightweight assertions for {@link GboardCalculatorEngine}.
 * Run from a unit-test classpath or call {@link #runAll()} from debug.
 */
public final class GboardCalculatorEngineTest {
    private GboardCalculatorEngineTest() {
    }

    public static void runAll() {
        expect("1+2", "3");
        expect("10/4", "2.5");
        expect("2^10", "1024");
        expect("sqrt(9)", "3");
        expect("(1+2)*3", "9");
        expect("", "");
    }

    private static void expect(String expr, String expected) {
        String got = GboardCalculatorEngine.evaluate(expr);
        if (expected.equals(got)) {
            return;
        }
        throw new AssertionError("expr=" + expr + " expected=" + expected + " got=" + got);
    }
}
