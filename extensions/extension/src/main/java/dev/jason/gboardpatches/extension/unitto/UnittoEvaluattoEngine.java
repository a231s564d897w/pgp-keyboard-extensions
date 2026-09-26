package dev.jason.gboardpatches.extension.unitto;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * In-process expression engine modeled on Unitto's evaluatto
 * ({@code io.github.sadellie.evaluatto.Expression}), GPL-3.0.
 *
 * Built into the PGP extension so the calculator Access Point works offline
 * without WebView or an external app.
 *
 * Supports: + - * / ^ % ! parentheses, unary minus, sqrt, sin cos tan,
 * asin acos atan, ln log exp, pi, e, implicit multiply, percent.
 */
public final class UnittoEvaluattoEngine {
    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_EVEN);
    private static final MathContext TRIG = new MathContext(100, RoundingMode.HALF_EVEN);
    private static final int SCALE = 16;

    private static final Set<String> FUNCS = new HashSet<>(Arrays.asList(
            "sin", "cos", "tan", "asin", "acos", "atan", "arsin", "arcos", "actan",
            "ln", "log", "exp", "sqrt"
    ));

    private final List<String> tokens;
    private int cursor;
    private final boolean radianMode;

    private UnittoEvaluattoEngine(List<String> tokens, boolean radianMode) {
        this.tokens = tokens;
        this.radianMode = radianMode;
        this.cursor = 0;
    }

    public static String evaluate(String expression) {
        return evaluate(expression, true);
    }

    public static String evaluate(String expression, boolean radians) {
        if (expression == null || expression.trim().isEmpty()) {
            return "";
        }
        try {
            String normalized = normalize(expression);
            List<String> toks = tokenize(normalized);
            toks = repairLexicon(toks);
            UnittoEvaluattoEngine eng = new UnittoEvaluattoEngine(toks, radians);
            BigDecimal result = eng.parseExpression();
            return format(result);
        } catch (Throwable t) {
            return "Error";
        }
    }

    private static String normalize(String expr) {
        // Match Unitto Token operators (see com.sadellie.unitto.core.common.Token)
        String s = expr
                .replace('×', '*')   // ×
                .replace('÷', '/')   // ÷
                .replace('−', '-')   // − MINUS SIGN
                .replace('−', '-')
                .replace('#', '%')        // Unitto MODULO token often '#'
                .replace(" ", "")
                .replace(" ", "")    // Token.SPACE nbsp
                .replace('π', 'π')
                .toLowerCase(Locale.US);
        s = s.replace("π", "pi");
        s = s.replace("√", "sqrt");
        return s;
    }

    private static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                int j = i + 1;
                int dots = c == '.' ? 1 : 0;
                while (j < s.length()) {
                    char d = s.charAt(j);
                    if (Character.isDigit(d)) {
                        j++;
                    } else if (d == '.' && dots == 0) {
                        dots++;
                        j++;
                    } else {
                        break;
                    }
                }
                out.add(s.substring(i, j));
                i = j;
                continue;
            }
            if (Character.isLetter(c)) {
                int j = i + 1;
                while (j < s.length() && Character.isLetter(s.charAt(j))) {
                    j++;
                }
                out.add(s.substring(i, j));
                i = j;
                continue;
            }
            // multi-char ops
            if (c == '!' || c == '%' || c == '^' || c == '(' || c == ')'
                    || c == '+' || c == '-' || c == '*' || c == '/') {
                out.add(String.valueOf(c));
                i++;
                continue;
            }
            i++; // skip unknown
        }
        return out;
    }

    private static List<String> repairLexicon(List<String> tokens) {
        List<String> t = new ArrayList<>(tokens);
        // auto-close brackets
        int left = 0, right = 0;
        for (String x : t) {
            if ("(".equals(x)) left++;
            if (")".equals(x)) right++;
        }
        for (int k = 0; k < left - right; k++) {
            t.add(")");
        }
        // implicit multiply: 2pi, )(, 2sin, pi(
        List<String> fixed = new ArrayList<>();
        for (int i = 0; i < t.size(); i++) {
            String cur = t.get(i);
            fixed.add(cur);
            if (i + 1 >= t.size()) {
                break;
            }
            String next = t.get(i + 1);
            boolean afterNum = isNumber(cur) || "pi".equals(cur) || "e".equals(cur)
                    || ")".equals(cur) || "!".equals(cur);
            boolean beforeNeed = isNumber(next) || "pi".equals(next) || "e".equals(next)
                    || "(".equals(next) || FUNCS.contains(next) || "sqrt".equals(next);
            if (afterNum && beforeNeed) {
                // "2 3" style number split already one token; 2( → multiply
                if (!(isNumber(cur) && isNumber(next))) {
                    fixed.add("*");
                }
            }
        }
        return fixed;
    }

    private static boolean isNumber(String t) {
        if (t == null || t.isEmpty()) return false;
        char c = t.charAt(0);
        return Character.isDigit(c) || c == '.';
    }

    private String peek() {
        return cursor < tokens.size() ? tokens.get(cursor) : "";
    }

    private boolean match(String token) {
        if (token.equals(peek())) {
            cursor++;
            return true;
        }
        return false;
    }

    private BigDecimal parseExpression() {
        if (tokens.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal v = parseTerm();
        while (true) {
            if (match("+")) {
                v = v.add(parseTerm(), MC);
            } else if (match("-")) {
                v = v.subtract(parseTerm(), MC);
            } else {
                break;
            }
        }
        return v;
    }

    private BigDecimal parseTerm() {
        BigDecimal v = parseFactor();
        while (true) {
            if (match("*")) {
                v = v.multiply(parseFactor(), MC);
            } else if (match("/")) {
                BigDecimal d = parseFactor();
                if (d.compareTo(BigDecimal.ZERO) == 0) {
                    throw new ArithmeticException("div0");
                }
                v = v.divide(d, SCALE, RoundingMode.HALF_EVEN);
            } else if (match("%") && !isNumber(peek()) && !"(".equals(peek()) && peek().isEmpty()) {
                // bare trailing % handled in factor
                break;
            } else {
                break;
            }
        }
        return v;
    }

    private BigDecimal parseFactor() {
        if (match("+")) {
            return parseFactor();
        }
        if (match("-")) {
            return parseFactor().negate();
        }

        BigDecimal expr = null;

        if (match("(")) {
            expr = parseExpression();
            if (!match(")")) {
                throw new IllegalArgumentException("paren");
            }
        } else if (isNumber(peek())) {
            expr = new BigDecimal(peek()).setScale(SCALE, RoundingMode.HALF_EVEN);
            cursor++;
        } else if (match("pi")) {
            expr = BigDecimal.valueOf(Math.PI);
        } else if (match("e")) {
            expr = BigDecimal.valueOf(Math.E);
        } else if (match("sqrt")) {
            expr = sqrt(parseFuncArg());
        } else if (match("sin")) {
            expr = trigSin(parseFuncArg());
        } else if (match("cos")) {
            expr = trigCos(parseFuncArg());
        } else if (match("tan")) {
            expr = trigTan(parseFuncArg());
        } else if (match("asin") || match("arsin")) {
            expr = trigAsin(parseFuncArg());
        } else if (match("acos") || match("arcos")) {
            expr = trigAcos(parseFuncArg());
        } else if (match("atan") || match("actan")) {
            expr = trigAtan(parseFuncArg());
        } else if (match("ln")) {
            expr = ln(parseFuncArg());
        } else if (match("log")) {
            expr = log10(parseFuncArg());
        } else if (match("exp")) {
            expr = exp(parseFuncArg());
        }

        if (match("^")) {
            if (expr == null) {
                throw new IllegalArgumentException("pow");
            }
            BigDecimal exp = parseFactor();
            expr = pow(expr, exp);
        }
        if (match("!")) {
            if (expr == null) {
                throw new IllegalArgumentException("fact");
            }
            expr = factorial(expr);
        }
        // percentage of value: 50% → 0.5 when not part of a+b%
        if (match("%")) {
            if (expr == null) {
                throw new IllegalArgumentException("%");
            }
            expr = expr.divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_EVEN);
        }

        if (expr == null) {
            throw new IllegalArgumentException("bad");
        }
        return expr;
    }

    private BigDecimal parseFuncArg() {
        if (match("(")) {
            BigDecimal v = parseExpression();
            if (!match(")")) {
                throw new IllegalArgumentException("fnparen");
            }
            return v;
        }
        return parseFactor();
    }

    private static BigDecimal sqrt(BigDecimal x) {
        if (x.compareTo(BigDecimal.ZERO) < 0) {
            throw new ArithmeticException("sqrt");
        }
        return BigDecimal.valueOf(Math.sqrt(x.doubleValue()));
    }

    private BigDecimal trigSin(BigDecimal x) {
        double a = x.doubleValue();
        if (!radianMode) {
            a = Math.toRadians(a);
        }
        return BigDecimal.valueOf(Math.sin(a));
    }

    private BigDecimal trigCos(BigDecimal x) {
        double a = x.doubleValue();
        if (!radianMode) {
            a = Math.toRadians(a);
        }
        return BigDecimal.valueOf(Math.cos(a));
    }

    private BigDecimal trigTan(BigDecimal x) {
        double a = x.doubleValue();
        if (!radianMode) {
            a = Math.toRadians(a);
        }
        return BigDecimal.valueOf(Math.tan(a));
    }

    private BigDecimal trigAsin(BigDecimal x) {
        double a = Math.asin(x.doubleValue());
        if (!radianMode) {
            a = Math.toDegrees(a);
        }
        return BigDecimal.valueOf(a);
    }

    private BigDecimal trigAcos(BigDecimal x) {
        double a = Math.acos(x.doubleValue());
        if (!radianMode) {
            a = Math.toDegrees(a);
        }
        return BigDecimal.valueOf(a);
    }

    private BigDecimal trigAtan(BigDecimal x) {
        double a = Math.atan(x.doubleValue());
        if (!radianMode) {
            a = Math.toDegrees(a);
        }
        return BigDecimal.valueOf(a);
    }

    private static BigDecimal ln(BigDecimal x) {
        return BigDecimal.valueOf(Math.log(x.doubleValue()));
    }

    private static BigDecimal log10(BigDecimal x) {
        return BigDecimal.valueOf(Math.log10(x.doubleValue()));
    }

    private static BigDecimal exp(BigDecimal x) {
        return BigDecimal.valueOf(Math.exp(x.doubleValue()));
    }

    private static BigDecimal pow(BigDecimal base, BigDecimal exp) {
        if (base.compareTo(BigDecimal.ZERO) == 0 && exp.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("0^0");
        }
        try {
            int n = exp.intValueExact();
            return base.pow(n, MC);
        } catch (Throwable t) {
            return BigDecimal.valueOf(Math.pow(base.doubleValue(), exp.doubleValue()));
        }
    }

    private static BigDecimal factorial(BigDecimal x) {
        if (x.compareTo(BigDecimal.ZERO) < 0 || x.stripTrailingZeros().scale() > 0) {
            throw new ArithmeticException("fact");
        }
        int n = x.intValueExact();
        if (n > 200) {
            throw new ArithmeticException("too big");
        }
        BigDecimal r = BigDecimal.ONE;
        for (int i = 2; i <= n; i++) {
            r = r.multiply(BigDecimal.valueOf(i), MC);
        }
        return r;
    }

    private static String format(BigDecimal v) {
        BigDecimal s = v.stripTrailingZeros();
        String plain = s.toPlainString();
        if (plain.indexOf('.') >= 0) {
            while (plain.endsWith("0")) {
                plain = plain.substring(0, plain.length() - 1);
            }
            if (plain.endsWith(".")) {
                plain = plain.substring(0, plain.length() - 1);
            }
        }
        return plain.isEmpty() ? "0" : plain;
    }
}
