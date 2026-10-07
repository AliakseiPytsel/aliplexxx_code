package com.example.calculator;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Вычисляет математические выражения методом рекурсивного спуска с учётом приоритета операций.
 *
 * <p>Грамматика (от низшего приоритета к высшему):
 * <pre>
 * expression := term (('+' | '-') term)*
 * term       := unary (('*' | '/') unary | unary)*   // второй вариант — неявное умножение: 2π, 3(4+1)
 * unary      := ('-' | '+') unary | power
 * power      := postfix ('^' unary)?                // правоассоциативно: 2^3^2 = 2^9
 * postfix    := primary ('!' | '%')*
 * primary    := number | 'π' | 'e' | '√' unary | function '(' expression ')' | '(' expression ')'
 * </pre>
 *
 * <p>Поддерживаемые функции: sin, cos, tan (tg), cot (ctg), ln, log (lg — десятичный), sqrt, abs.
 * Принимаются как ASCII-операторы (+ - * /), так и символы с клавиатуры калькулятора (− × ÷).
 */
public final class ExpressionEvaluator {

    /** Порог, ниже которого значение тригонометрической функции считается нулём (sin(π) ≈ 1.2e-16). */
    private static final double EPSILON = 1e-12;

    private static final int MAX_FACTORIAL = 170; // 171! уже не помещается в double

    /** Имена, отсортированные так, чтобы более длинные проверялись раньше (sqrt раньше s...). */
    private static final String[] NAMES = {
            "sqrt", "sin", "cos", "tan", "cot", "ctg", "abs", "log", "tg", "ln", "lg", "pi", "e"
    };

    private ExpressionEvaluator() {
    }

    /**
     * @param expression выражение, например {@code 12-(sin(3^3)+ln(√4))}
     * @param degrees    true — аргументы тригонометрических функций в градусах, false — в радианах
     * @return значение выражения
     * @throws CalculationException если выражение некорректно или не может быть вычислено
     */
    public static double evaluate(String expression, boolean degrees) {
        return new Parser(expression, degrees).parse();
    }

    /** Форматирует результат: до 12 значащих цифр, без лишних нулей, большие/малые числа — через E. */
    public static String format(double value) {
        if (value == 0) {
            return "0"; // заодно убирает "-0"
        }
        BigDecimal rounded = new BigDecimal(value).round(new MathContext(12)).stripTrailingZeros();
        double abs = Math.abs(value);
        if (abs >= 1e12 || abs < 1e-6) {
            return rounded.toString(); // например 1.5E+15 или 2.5E-7
        }
        return rounded.toPlainString();
    }

    private static final class Parser {
        private final String text;
        private final boolean degrees;
        private int pos;

        Parser(String text, boolean degrees) {
            this.text = text == null ? "" : text;
            this.degrees = degrees;
        }

        double parse() {
            skipSpaces();
            if (pos >= text.length()) {
                throw new CalculationException("Введите выражение");
            }
            double value = parseExpression();
            skipSpaces();
            if (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == ')') {
                    throw new CalculationException("Лишняя закрывающая скобка");
                }
                throw new CalculationException("Неожиданный символ «" + c + "»");
            }
            return value;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (true) {
                if (eat('+')) {
                    value = check(value + parseTerm());
                } else if (eat('-') || eat('−')) {
                    value = check(value - parseTerm());
                } else {
                    return value;
                }
            }
        }

        private double parseTerm() {
            double value = parseUnary();
            while (true) {
                if (eat('*') || eat('×')) {
                    value = check(value * parseUnary());
                } else if (eat('/') || eat('÷')) {
                    double divisor = parseUnary();
                    if (divisor == 0) {
                        throw new CalculationException("Деление на ноль");
                    }
                    value = check(value / divisor);
                } else if (startsOperand()) {
                    value = check(value * parseUnary()); // неявное умножение: 2π, 2(3+4), 3sin(x)
                } else {
                    return value;
                }
            }
        }

        private double parseUnary() {
            if (eat('-') || eat('−')) {
                return -parseUnary();
            }
            if (eat('+')) {
                return parseUnary();
            }
            return parsePower();
        }

        private double parsePower() {
            double base = parsePostfix();
            if (eat('^')) {
                return power(base, parseUnary());
            }
            return base;
        }

        private double parsePostfix() {
            double value = parsePrimary();
            while (true) {
                if (eat('!')) {
                    value = factorial(value);
                } else if (eat('%')) {
                    value = value / 100;
                } else {
                    return value;
                }
            }
        }

        private double parsePrimary() {
            skipSpaces();
            if (pos >= text.length()) {
                throw new CalculationException("Выражение не завершено");
            }
            char c = text.charAt(pos);
            if (eat('(')) {
                double value = parseExpression();
                expectClosingParenthesis();
                return value;
            }
            if (eat('√')) {
                return sqrt(parseUnary());
            }
            if (eat('π')) {
                return Math.PI;
            }
            if (isDigit(c) || c == '.') {
                return parseNumber();
            }
            if (isLatinLetter(c)) {
                return parseName();
            }
            if (c == ')') {
                throw new CalculationException("Пустые скобки");
            }
            throw new CalculationException("Пропущено число перед «" + c + "»");
        }

        private double parseNumber() {
            int start = pos;
            boolean hasDot = false;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (isDigit(c)) {
                    pos++;
                } else if (c == '.') {
                    if (hasDot) {
                        throw new CalculationException("Лишняя точка в числе");
                    }
                    hasDot = true;
                    pos++;
                } else {
                    break;
                }
            }
            if (pos - start == 1 && hasDot) {
                throw new CalculationException("Некорректное число «.»");
            }
            // Экспоненциальная запись вида 1.5E+12 (так форматируются очень большие результаты).
            if (pos < text.length() && text.charAt(pos) == 'E') {
                int save = pos;
                pos++;
                if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
                    pos++;
                }
                if (pos < text.length() && isDigit(text.charAt(pos))) {
                    while (pos < text.length() && isDigit(text.charAt(pos))) {
                        pos++;
                    }
                } else {
                    pos = save; // это не экспонента, а, например, константа e
                }
            }
            return check(Double.parseDouble(text.substring(start, pos)));
        }

        private double parseName() {
            String name = null;
            for (String candidate : NAMES) {
                if (text.regionMatches(true, pos, candidate, 0, candidate.length())) {
                    name = candidate;
                    break;
                }
            }
            if (name == null) {
                int end = pos;
                while (end < text.length() && isLatinLetter(text.charAt(end))) {
                    end++;
                }
                throw new CalculationException("Неизвестная функция «" + text.substring(pos, end) + "»");
            }
            pos += name.length();

            if (name.equals("pi")) {
                return Math.PI;
            }
            if (name.equals("e")) {
                return Math.E;
            }
            if (!eat('(')) {
                throw new CalculationException("После «" + name + "» нужна скобка «(»");
            }
            double argument = parseExpression();
            expectClosingParenthesis();
            return applyFunction(name, argument);
        }

        private double applyFunction(String name, double x) {
            switch (name) {
                case "sin":
                    return snapToZero(Math.sin(toRadians(x)));
                case "cos":
                    return snapToZero(Math.cos(toRadians(x)));
                case "tan":
                case "tg": {
                    double rad = toRadians(x);
                    double cos = Math.cos(rad);
                    if (Math.abs(cos) < EPSILON) {
                        throw new CalculationException("Тангенс не определён для этого угла");
                    }
                    return check(snapToZero(Math.sin(rad) / cos));
                }
                case "cot":
                case "ctg": {
                    double rad = toRadians(x);
                    double sin = Math.sin(rad);
                    if (Math.abs(sin) < EPSILON) {
                        throw new CalculationException("Котангенс не определён для этого угла");
                    }
                    return check(snapToZero(Math.cos(rad) / sin));
                }
                case "ln":
                    requirePositiveForLog(x);
                    return Math.log(x);
                case "log":
                case "lg":
                    requirePositiveForLog(x);
                    return Math.log10(x);
                case "sqrt":
                    return sqrt(x);
                case "abs":
                    return Math.abs(x);
                default:
                    throw new CalculationException("Неизвестная функция «" + name + "»");
            }
        }

        private double toRadians(double x) {
            return degrees ? Math.toRadians(x) : x;
        }

        private void expectClosingParenthesis() {
            if (!eat(')')) {
                throw new CalculationException("Не хватает закрывающей скобки");
            }
        }

        /** Следующий символ начинает операнд — значит, между операндами подразумевается умножение. */
        private boolean startsOperand() {
            skipSpaces();
            if (pos >= text.length()) {
                return false;
            }
            char c = text.charAt(pos);
            return isDigit(c) || c == '.' || c == '(' || c == 'π' || c == '√' || isLatinLetter(c);
        }

        private boolean eat(char expected) {
            skipSpaces();
            if (pos < text.length() && text.charAt(pos) == expected) {
                pos++;
                return true;
            }
            return false;
        }

        private void skipSpaces() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }
    }

    private static double sqrt(double x) {
        if (x < 0) {
            throw new CalculationException("Корень из отрицательного числа");
        }
        return Math.sqrt(x);
    }

    private static double power(double base, double exponent) {
        if (base == 0 && exponent < 0) {
            throw new CalculationException("Деление на ноль");
        }
        if (base < 0 && exponent != Math.rint(exponent)) {
            // Нечётный корень из отрицательного числа определён: (-8)^(1/3) = -2
            double inverse = 1 / exponent;
            double rounded = Math.rint(inverse);
            if (Math.abs(inverse - rounded) < 1e-9 && Math.abs(rounded % 2) == 1) {
                return check(-Math.pow(-base, exponent));
            }
            throw new CalculationException("Нельзя возвести отрицательное число в дробную степень");
        }
        return check(Math.pow(base, exponent));
    }

    private static double factorial(double n) {
        if (n < 0 || n != Math.rint(n)) {
            throw new CalculationException("Факториал определён только для целых чисел ≥ 0");
        }
        if (n > MAX_FACTORIAL) {
            throw new CalculationException("Слишком большое число для факториала");
        }
        double result = 1;
        for (int i = 2; i <= (int) n; i++) {
            result *= i;
        }
        return result;
    }

    private static void requirePositiveForLog(double x) {
        if (x <= 0) {
            throw new CalculationException("Логарифм определён только для чисел > 0");
        }
    }

    private static double snapToZero(double value) {
        return Math.abs(value) < EPSILON ? 0 : value;
    }

    private static double check(double value) {
        if (Double.isNaN(value)) {
            throw new CalculationException("Результат не определён");
        }
        if (Double.isInfinite(value)) {
            throw new CalculationException("Слишком большое число");
        }
        return value;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isLatinLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
}
