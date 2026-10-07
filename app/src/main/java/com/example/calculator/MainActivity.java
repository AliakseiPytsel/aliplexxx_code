package com.example.calculator;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "MainActivity";

    // Ключи для сохранения состояния в Bundle при повороте экрана
    private static final String KEY_EXPRESSION = "expression";
    private static final String KEY_RESULT = "result";
    private static final String KEY_RESULT_IS_ERROR = "result_is_error";
    private static final String KEY_JUST_EVALUATED = "just_evaluated";
    private static final String KEY_DEGREE_MODE = "degree_mode";

    private static final int MAX_LENGTH = 200;
    private static final String MINUS = "−";

    private TextView expressionView;
    private TextView resultView;
    private Button angleModeButton;

    private String expression = "";
    private String result = "";            // последний результат или текст ошибки
    private boolean resultIsError = false;
    private boolean justEvaluated = false; // только что нажали "=" и получили результат
    private boolean degreeMode = false;    // false — радианы, true — градусы

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        // Светлые значки статус-бара поверх тёмной панели приложения
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));
        setContentView(R.layout.activity_main); // для альбомной ориентации подставится res/layout-land

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        expressionView = findViewById(R.id.expression);
        resultView = findViewById(R.id.result);
        angleModeButton = findViewById(R.id.btn_angle_mode);
        ViewGroup keypad = findViewById(R.id.keypad);

        applySystemBarInsets(findViewById(R.id.main), toolbar, keypad);
        setClickListenerOnButtons(keypad);
        render();
    }

    /** Отступы, чтобы интерфейс не залезал под статус-бар, панель навигации и вырез камеры. */
    private void applySystemBarInsets(View root, View toolbar, View keypad) {
        int toolbarHeight = toolbar.getMinimumHeight();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, 0, bars.right, 0);
            // Панель заходит под статус-бар, а заголовок остаётся ниже него
            toolbar.setMinimumHeight(toolbarHeight + bars.top);
            toolbar.setPadding(0, bars.top, 0, 0);
            keypad.setPadding(0, 0, 0, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /** Один обработчик на все кнопки: обходим клавиатуру и назначаем this как OnClickListener. */
    private void setClickListenerOnButtons(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Button) {
                child.setOnClickListener(this);
            } else if (child instanceof ViewGroup) {
                setClickListenerOnButtons((ViewGroup) child);
            }
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.btn_clear) {
            clear();
        } else if (id == R.id.btn_backspace) {
            backspace();
        } else if (id == R.id.btn_equals) {
            calculate();
        } else if (id == R.id.btn_angle_mode) {
            toggleAngleMode();
        } else if (view.getTag() != null) {
            input(view.getTag().toString()); // в android:tag кнопки лежит вставляемый текст
        }
        render();
    }

    private void input(String token) {
        if (justEvaluated) {
            justEvaluated = false;
            // После "=" оператор продолжает вычисление с результатом, а число начинает новое выражение
            expression = continuesPreviousResult(token) ? result : "";
        }
        resultIsError = false;

        if (token.equals(".")) {
            appendDecimalPoint();
        } else if (isBinaryOperator(token)) {
            appendOperator(token);
        } else {
            append(token);
        }
    }

    private void appendDecimalPoint() {
        boolean hasDigits = false;
        for (int i = expression.length() - 1; i >= 0; i--) {
            char c = expression.charAt(i);
            if (c == '.') {
                return; // в текущем числе уже есть точка
            }
            if (!Character.isDigit(c)) {
                break;
            }
            hasDigits = true;
        }
        append(hasDigits ? "." : "0.");
    }

    private void appendOperator(String operator) {
        char last = expression.isEmpty() ? 0 : expression.charAt(expression.length() - 1);
        // Минус может быть унарным: в начале, после "(" или после ×, ÷, ^ (например 2×−3)
        if (operator.equals(MINUS) && last != '+' && last != '−') {
            append(operator);
            return;
        }
        // Иначе заменяем стоящие в конце операторы новым
        String base = expression;
        while (!base.isEmpty() && isBinaryOperator(base.substring(base.length() - 1))) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isEmpty() || base.endsWith("(")) {
            return; // бинарному оператору нужен левый операнд
        }
        expression = base;
        append(operator);
    }

    private void append(String text) {
        if (expression.length() + text.length() > MAX_LENGTH) {
            Toast.makeText(this, R.string.expression_too_long, Toast.LENGTH_SHORT).show();
            return;
        }
        expression += text;
    }

    private void backspace() {
        justEvaluated = false;
        resultIsError = false;
        if (expression.isEmpty()) {
            return;
        }
        // Функции удаляем целиком: "sin(" стирается одним нажатием
        for (String function : new String[]{"sin(", "cos(", "tan(", "cot(", "log(", "ln("}) {
            if (expression.endsWith(function)) {
                expression = expression.substring(0, expression.length() - function.length());
                return;
            }
        }
        expression = expression.substring(0, expression.length() - 1);
    }

    private void clear() {
        expression = "";
        result = "";
        resultIsError = false;
        justEvaluated = false;
    }

    private void calculate() {
        if (expression.isEmpty()) {
            return;
        }
        expression = closeParentheses(expression);
        try {
            result = ExpressionEvaluator.format(ExpressionEvaluator.evaluate(expression, degreeMode));
            resultIsError = false;
            justEvaluated = true;
        } catch (CalculationException e) {
            result = e.getMessage();
            resultIsError = true;
            justEvaluated = false;
        }
    }

    private void toggleAngleMode() {
        degreeMode = !degreeMode;
        if (justEvaluated) {
            calculate(); // пересчитать показанный результат в новом режиме
        }
    }

    /** Обновляет экран по текущему состоянию. */
    private void render() {
        expressionView.setText(expression);
        angleModeButton.setText(degreeMode ? R.string.angle_mode_deg : R.string.angle_mode_rad);

        if (resultIsError) {
            resultView.setTextColor(ContextCompat.getColor(this, R.color.result_error));
            resultView.setText(result);
        } else if (justEvaluated) {
            resultView.setTextColor(ContextCompat.getColor(this, R.color.result_text));
            resultView.setText(getString(R.string.result_format, result));
        } else {
            // Предпросмотр результата во время ввода; ошибки до нажатия "=" не показываем
            resultView.setTextColor(ContextCompat.getColor(this, R.color.result_preview));
            String preview = preview();
            resultView.setText(preview == null ? "" : getString(R.string.result_format, preview));
        }
    }

    private String preview() {
        if (expression.isEmpty()) {
            return null;
        }
        try {
            double value = ExpressionEvaluator.evaluate(closeParentheses(expression), degreeMode);
            return ExpressionEvaluator.format(value);
        } catch (CalculationException e) {
            return null;
        }
    }

    // ---------- Сохранение данных при изменении конфигурации (поворот экрана) ----------

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(TAG, "onSaveInstanceState: " + expression);
        outState.putString(KEY_EXPRESSION, expression);
        outState.putString(KEY_RESULT, result);
        outState.putBoolean(KEY_RESULT_IS_ERROR, resultIsError);
        outState.putBoolean(KEY_JUST_EVALUATED, justEvaluated);
        outState.putBoolean(KEY_DEGREE_MODE, degreeMode);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        expression = savedInstanceState.getString(KEY_EXPRESSION, "");
        result = savedInstanceState.getString(KEY_RESULT, "");
        resultIsError = savedInstanceState.getBoolean(KEY_RESULT_IS_ERROR);
        justEvaluated = savedInstanceState.getBoolean(KEY_JUST_EVALUATED);
        degreeMode = savedInstanceState.getBoolean(KEY_DEGREE_MODE);
        Log.d(TAG, "onRestoreInstanceState: " + expression);
        render();
    }

    // ---------- Логирование жизненного цикла (видно в Logcat по тегу MainActivity) ----------

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }

    // ---------- Вспомогательные методы ----------

    private static boolean isBinaryOperator(String token) {
        return token.equals("+") || token.equals(MINUS) || token.equals("×")
                || token.equals("÷") || token.equals("^");
    }

    /** Токены, которые после "=" применяются к полученному результату. */
    private static boolean continuesPreviousResult(String token) {
        return isBinaryOperator(token) || token.equals("!") || token.equals("%") || token.equals("^2");
    }

    /** Дописывает недостающие закрывающие скобки: "sin(30" → "sin(30)". */
    private static String closeParentheses(String text) {
        int open = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                open++;
            } else if (c == ')' && open > 0) {
                open--;
            }
        }
        StringBuilder builder = new StringBuilder(text);
        for (int i = 0; i < open; i++) {
            builder.append(')');
        }
        return builder.toString();
    }
}
