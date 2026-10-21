package com.example.calculator;

import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "MainActivity";

    // Ключи для сохранения состояния в Bundle при повороте экрана
    private static final String KEY_EXPRESSION = "expression";
    private static final String KEY_RESULT = "result";
    private static final String KEY_RESULT_IS_ERROR = "result_is_error";
    private static final String KEY_JUST_EVALUATED = "just_evaluated";
    private static final String KEY_DEGREE_MODE = "degree_mode";
    private static final String KEY_HISTORY = "history";
    private static final String KEY_HISTORY_DIALOG_OPEN = "history_dialog_open";

    private static final int MAX_LENGTH = 200;
    private static final int MAX_HISTORY = 50; // Bundle рассчитан на небольшие данные
    private static final String MINUS = "−";
    private static final String HISTORY_SEPARATOR = " = ";

    private TextView expressionView;
    private TextView resultView;
    private Button angleModeButton;
    private ScrollView historyScroll; // есть только в портретной разметке, в альбомной — null
    private TextView historyView;     // есть только в портретной разметке, в альбомной — null
    private AlertDialog historyDialog;

    private String expression = "";
    private String result = "";            // последний результат или текст ошибки
    private boolean resultIsError = false;
    private boolean justEvaluated = false; // только что нажали "=" и получили результат
    private boolean degreeMode = false;    // false — радианы, true — градусы
    private ArrayList<String> history = new ArrayList<>(); // записи "выражение = результат", старые в начале
    private boolean historyDialogWasOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate, savedInstanceState = " + savedInstanceState);
        // Светлые значки статус-бара поверх тёмной панели приложения
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));
        setContentView(R.layout.activity_main); // для альбомной ориентации подставится res/layout-land

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        expressionView = findViewById(R.id.expression);
        resultView = findViewById(R.id.result);
        angleModeButton = findViewById(R.id.btn_angle_mode);
        historyScroll = findViewById(R.id.history_scroll);
        historyView = findViewById(R.id.history);
        ViewGroup keypad = findViewById(R.id.keypad);

        if (historyView != null) {
            historyView.setOnClickListener(v -> showHistoryDialog());
        }
        applySystemBarInsets(findViewById(R.id.main), toolbar, keypad);
        setClickListenerOnButtons(keypad);

        // Первый запуск: savedInstanceState == null, восстанавливать нечего.
        // После поворота: Activity создана заново, а в Bundle лежат данные из onSaveInstanceState.
        if (savedInstanceState != null) {
            restoreState(savedInstanceState);
        }
        render();
        if (historyDialogWasOpen) {
            showHistoryDialog(); // диалог закрылся вместе со старой Activity — открываем снова
        }
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

    // ---------- Меню верхней панели: кнопка «История» ----------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_history) {
            showHistoryDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
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
            addToHistory(getString(R.string.history_entry, expression, result));
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

    // ---------- История вычислений ----------

    private void addToHistory(String entry) {
        int size = history.size();
        if (size > 0 && history.get(size - 1).equals(entry)) {
            return; // повторное нажатие "=" не дублирует запись
        }
        history.add(entry);
        if (history.size() > MAX_HISTORY) {
            history.remove(0); // самые старые записи удаляем
        }
    }

    /** Окно со всей историей (новые сверху). Нажатие на запись возвращает её выражение для правки. */
    private void showHistoryDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.history)
                .setPositiveButton(R.string.close, null);
        if (history.isEmpty()) {
            builder.setMessage(R.string.history_empty);
        } else {
            String[] items = new String[history.size()];
            for (int i = 0; i < items.length; i++) {
                items[i] = history.get(history.size() - 1 - i);
            }
            builder.setItems(items, (dialog, which) -> useHistoryEntry(items[which]));
            builder.setNeutralButton(R.string.history_clear, (dialog, which) -> {
                history.clear();
                render();
            });
        }
        historyDialog = builder.show();
    }

    private void useHistoryEntry(String entry) {
        int separator = entry.lastIndexOf(HISTORY_SEPARATOR);
        expression = separator >= 0 ? entry.substring(0, separator) : entry;
        result = "";
        resultIsError = false;
        justEvaluated = false;
        render();
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

        // В альбомной разметке поля истории нет — findViewById вернул null, поэтому проверяем
        if (historyView != null && historyScroll != null) {
            historyView.setText(TextUtils.join("\n", history));
            historyScroll.post(() -> historyScroll.fullScroll(View.FOCUS_DOWN)); // к последней записи
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
        outState.putString(KEY_EXPRESSION, expression);
        outState.putString(KEY_RESULT, result);
        outState.putBoolean(KEY_RESULT_IS_ERROR, resultIsError);
        outState.putBoolean(KEY_JUST_EVALUATED, justEvaluated);
        outState.putBoolean(KEY_DEGREE_MODE, degreeMode);
        outState.putStringArrayList(KEY_HISTORY, history);
        outState.putBoolean(KEY_HISTORY_DIALOG_OPEN, historyDialog != null && historyDialog.isShowing());
        Log.d(TAG, "onSaveInstanceState: " + outState);
    }

    /** Достаёт данные из Bundle. Вызывается из onCreate, только если Bundle не null. */
    private void restoreState(@NonNull Bundle state) {
        expression = state.getString(KEY_EXPRESSION, "");
        result = state.getString(KEY_RESULT, "");
        resultIsError = state.getBoolean(KEY_RESULT_IS_ERROR, false);
        justEvaluated = state.getBoolean(KEY_JUST_EVALUATED, false);
        degreeMode = state.getBoolean(KEY_DEGREE_MODE, false);
        ArrayList<String> savedHistory = state.getStringArrayList(KEY_HISTORY);
        if (savedHistory != null) { // getStringArrayList вернёт null, если ключа нет
            history = savedHistory;
        }
        historyDialogWasOpen = state.getBoolean(KEY_HISTORY_DIALOG_OPEN, false);
        Log.d(TAG, "restoreState: expression = " + expression + ", history = " + history.size());
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        // Вызывается после onStart() и только при непустом Bundle. Данные мы уже восстановили
        // в onCreate, здесь оставлен только лог, чтобы видеть порядок вызовов в Logcat.
        super.onRestoreInstanceState(savedInstanceState);
        Log.d(TAG, "onRestoreInstanceState");
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
        // Закрываем диалог, иначе при повороте окно «утечёт» вместе со старой Activity
        if (historyDialog != null) {
            historyDialog.dismiss();
        }
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
