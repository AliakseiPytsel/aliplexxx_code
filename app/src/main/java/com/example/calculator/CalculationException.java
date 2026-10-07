package com.example.calculator;

/**
 * Ошибка вычисления или разбора выражения. Сообщение показывается пользователю как есть.
 */
public class CalculationException extends RuntimeException {

    public CalculationException(String message) {
        super(message);
    }
}
