package dev.jason.gboardpatches.extension.calculator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight expression evaluator for the floating calculator overlay.
 * Supports + - * / ^ % parentheses, unary minus, and common functions:
 * sin cos tan sqrt ln log abs.
 *
 * NumberHub (GPL-3.0) remains the preferred full product for unit/currency/date;
 * this engine keeps the overlay self-contained and offline.
 */
public final class GboardCalculatorEngine {
    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);

    private GboardCalculatorEngine() {
    }

    public static String evaluate(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return "";
        }
        // Path B/C bridge (AAR if present) → Path A evaluatto Java.
        try {
            String unitto = dev.jason.gboardpatches.extension.unitto.UnittoEngineBridge
                    .evaluate(expression);
            if (unitto != null && !unitto.isEmpty() && !"Error".equals(unitto)) {
                return unitto;
            }
        } catch (Throwable ignored) {
        }
        try {
            String normalized = normalize(expression);
            List<String> rpn = toRpn(normalized);
            BigDecimal result = evalRpn(rpn);
            return format(result);
        } catch (Throwable t) {
            return "Error";
        }
    }

    private static String normalize(String expr) {
        String s = expr.replace("\u00d7", "*").replace("\u00f7", "/")
                .replace("\u2212", "-").replace(" ", "");
        // Replace constants
        s = s.replace("\u03c0", String.valueOf(Math.PI));
        s = s.replace("pi", String.valueOf(Math.PI));
        s = s.replace("e", String.valueOf(Math.E));
        // Unary minus after operators / start -> "0-"
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '-' && (i == 0 || isOp(s.charAt(i - 1)) || s.charAt(i - 1) == '(')) {
                sb.append("0-");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isOp(char c) {
        return c == '+' || c == '-' || c == '*' || c == '/' || c == '^' || c == '%';
    }

    private static List<String> toRpn(String s) {
        List<String> output = new ArrayList<>();
        Deque<String> ops = new ArrayDeque<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                int j = i + 1;
                while (j < s.length() && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) {
                    j++;
                }
                output.add(s.substring(i, j));
                i = j;
                continue;
            }
            if (Character.isLetter(c)) {
                int j = i + 1;
                while (j < s.length() && Character.isLetter(s.charAt(j))) {
                    j++;
                }
                String fn = s.substring(i, j).toLowerCase(Locale.US);
                ops.push(fn);
                i = j;
                continue;
            }
            if (c == '(') {
                ops.push("(");
                i++;
                continue;
            }
            if (c == ')') {
                while (!ops.isEmpty() && !"(".equals(ops.peek())) {
                    output.add(ops.pop());
                }
                if (!ops.isEmpty() && "(".equals(ops.peek())) {
                    ops.pop();
                }
                if (!ops.isEmpty() && isFunction(ops.peek())) {
                    output.add(ops.pop());
                }
                i++;
                continue;
            }
            if (isOp(c)) {
                String op = String.valueOf(c);
                while (!ops.isEmpty() && isOpChar(ops.peek())
                        && precedence(ops.peek()) >= precedence(op)) {
                    output.add(ops.pop());
                }
                ops.push(op);
                i++;
                continue;
            }
            i++;
        }
        while (!ops.isEmpty()) {
            output.add(ops.pop());
        }
        return output;
    }

    private static boolean isFunction(String t) {
        return "sin".equals(t) || "cos".equals(t) || "tan".equals(t)
                || "sqrt".equals(t) || "ln".equals(t) || "log".equals(t) || "abs".equals(t);
    }

    private static boolean isOpChar(String t) {
        return t.length() == 1 && isOp(t.charAt(0));
    }

    private static int precedence(String op) {
        switch (op) {
            case "+":
            case "-":
                return 1;
            case "*":
            case "/":
            case "%":
                return 2;
            case "^":
                return 3;
            default:
                return 0;
        }
    }

    private static BigDecimal evalRpn(List<String> rpn) {
        Deque<BigDecimal> stack = new ArrayDeque<>();
        for (String token : rpn) {
            if (isOpChar(token)) {
                BigDecimal b = stack.pop();
                BigDecimal a = stack.isEmpty() ? BigDecimal.ZERO : stack.pop();
                switch (token) {
                    case "+":
                        stack.push(a.add(b, MC));
                        break;
                    case "-":
                        stack.push(a.subtract(b, MC));
                        break;
                    case "*":
                        stack.push(a.multiply(b, MC));
                        break;
                    case "/":
                        stack.push(a.divide(b, MC));
                        break;
                    case "%":
                        stack.push(a.remainder(b, MC));
                        break;
                    case "^":
                        stack.push(BigDecimal.valueOf(
                                Math.pow(a.doubleValue(), b.doubleValue())));
                        break;
                    default:
                        break;
                }
            } else if (isFunction(token)) {
                BigDecimal a = stack.pop();
                double v = a.doubleValue();
                switch (token) {
                    case "sin":
                        stack.push(BigDecimal.valueOf(Math.sin(v)));
                        break;
                    case "cos":
                        stack.push(BigDecimal.valueOf(Math.cos(v)));
                        break;
                    case "tan":
                        stack.push(BigDecimal.valueOf(Math.tan(v)));
                        break;
                    case "sqrt":
                        stack.push(BigDecimal.valueOf(Math.sqrt(v)));
                        break;
                    case "ln":
                        stack.push(BigDecimal.valueOf(Math.log(v)));
                        break;
                    case "log":
                        stack.push(BigDecimal.valueOf(Math.log10(v)));
                        break;
                    case "abs":
                        stack.push(a.abs(MC));
                        break;
                    default:
                        break;
                }
            } else {
                stack.push(new BigDecimal(token, MC));
            }
        }
        return stack.isEmpty() ? BigDecimal.ZERO : stack.pop();
    }

    private static String format(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        String plain = stripped.toPlainString();
        if (plain.indexOf('.') >= 0 && plain.length() > 14) {
            return stripped.round(new MathContext(12, RoundingMode.HALF_UP)).toPlainString();
        }
        return plain;
    }
}
