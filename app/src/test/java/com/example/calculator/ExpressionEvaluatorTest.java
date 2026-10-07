package com.example.calculator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ExpressionEvaluatorTest {

    private static final double DELTA = 1e-9;

    private static double rad(String expression) {
        return ExpressionEvaluator.evaluate(expression, false);
    }

    private static double deg(String expression) {
        return ExpressionEvaluator.evaluate(expression, true);
    }

    private static void assertError(String expression, String expectedMessage) {
        try {
            rad(expression);
            fail("Ожидалась ошибка для " + expression);
        } catch (CalculationException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    @Test
    public void exampleFromTask() {
        assertEquals(12 - (Math.sin(27) + Math.log(2)), rad("12-(sin(3^3) + ln(√4))"), DELTA);
        assertEquals(12 - (Math.sin(27) + Math.log(2)), rad("12−(sin(3^3)+ln(√4))"), DELTA);
    }

    @Test
    public void operatorPriority() {
        assertEquals(14, rad("2+3×4"), DELTA);
        assertEquals(20, rad("(2+3)×4"), DELTA);
        assertEquals(1, rad("10÷5−1"), DELTA);
        assertEquals(512, rad("2^3^2"), DELTA);
        assertEquals(-4, rad("−2^2"), DELTA);
        assertEquals(0.25, rad("2^−2"), DELTA);
        assertEquals(2.5, rad("10 / 4"), DELTA);
        assertEquals(-1, rad("2*-3+5"), DELTA);
    }

    @Test
    public void implicitMultiplication() {
        assertEquals(2 * Math.PI, rad("2π"), DELTA);
        assertEquals(14, rad("2(3+4)"), DELTA);
        assertEquals(6, rad("2√9"), DELTA);
        assertEquals(Math.E * Math.E, rad("ee"), DELTA);
        assertEquals(12, rad("(1+2)(2+2)"), DELTA);
    }

    @Test
    public void functions() {
        assertEquals(3, rad("√9"), DELTA);
        assertEquals(4, rad("sqrt(16)"), DELTA);
        assertEquals(120, rad("5!"), DELTA);
        assertEquals(1, rad("0!"), DELTA);
        assertEquals(2, rad("log(100)"), DELTA);
        assertEquals(1, rad("ln(e)"), DELTA);
        assertEquals(0, rad("sin(π)"), DELTA);
        assertEquals(-1, rad("cos(pi)"), DELTA);
        assertEquals(1, rad("tan(π/4)"), DELTA);
        assertEquals(1, rad("cot(π/4)"), DELTA);
        assertEquals(5, rad("abs(−5)"), DELTA);
        assertEquals(0.15, rad("15%"), DELTA);
        assertEquals(30, rad("200×15%"), DELTA);
        assertEquals(-2, rad("(−8)^(1/3)"), DELTA);
    }

    @Test
    public void degrees() {
        assertEquals(0.5, deg("sin(30)"), DELTA);
        assertEquals(0, deg("cos(90)"), DELTA);
        assertEquals(1, deg("tg(45)"), DELTA);
        assertEquals(0, deg("sin(180)"), DELTA);
    }

    @Test
    public void errors() {
        assertError("5÷0", "Деление на ноль");
        assertError("1/(2−2)", "Деление на ноль");
        assertError("0^−1", "Деление на ноль");
        assertError("√(−4)", "Корень из отрицательного числа");
        assertError("ln(0)", "Логарифм определён только для чисел > 0");
        assertError("log(−1)", "Логарифм определён только для чисел > 0");
        assertError("(−3)!", "Факториал определён только для целых чисел ≥ 0");
        assertError("2.5!", "Факториал определён только для целых чисел ≥ 0");
        assertError("171!", "Слишком большое число для факториала");
        assertError("tan(π/2)", "Тангенс не определён для этого угла");
        assertError("cot(0)", "Котангенс не определён для этого угла");
        assertError("(−8)^0.5", "Нельзя возвести отрицательное число в дробную степень");
        assertError("10^400", "Слишком большое число");
        assertError("", "Введите выражение");
        assertError("2+", "Выражение не завершено");
        assertError("(2+3", "Не хватает закрывающей скобки");
        assertError("2+3)", "Лишняя закрывающая скобка");
        assertError("×3", "Пропущено число перед «×»");
        assertError("1.2.3", "Лишняя точка в числе");
        assertError("()", "Пустые скобки");
        assertError("sin 30", "После «sin» нужна скобка «(»");
        assertError("foo(1)", "Неизвестная функция «foo»");
    }

    @Test
    public void formatting() {
        assertEquals("4", ExpressionEvaluator.format(4.0));
        assertEquals("0", ExpressionEvaluator.format(-0.0));
        assertEquals("0.5", ExpressionEvaluator.format(0.49999999999999994));
        assertEquals("0.3", ExpressionEvaluator.format(0.1 + 0.2));
        assertEquals("-2.5", ExpressionEvaluator.format(-2.5));
        assertEquals("3.14159265359", ExpressionEvaluator.format(Math.PI));
        assertEquals("1.5E+15", ExpressionEvaluator.format(1.5e15));
        assertEquals("2.5E-7", ExpressionEvaluator.format(2.5e-7));
    }

    @Test
    public void formattedResultCanBeParsedAgain() {
        assertEquals(3e15, rad(ExpressionEvaluator.format(1.5e15) + "×2"), 1);
        assertEquals(5e-7, rad(ExpressionEvaluator.format(2.5e-7) + "×2"), DELTA);
    }
}
