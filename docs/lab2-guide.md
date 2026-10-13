# Лабораторная работа №2 — полный разбор

# Часть 1. Суть лабораторной работы

Нужно сделать Android-приложение «калькулятор», которое:

1. **Считает сложные выражения целиком**, например `12-(sin(3^3)+ln(√4))`, и соблюдает приоритет: сначала скобки, потом степень, потом умножение и деление, потом сложение и вычитание.
2. **Поддерживает функции:** корень, степень, логарифм, факториал, sin, cos и другие.
3. **Имеет два интерфейса:** для вертикального (портретного) и горизонтального (альбомного) положения телефона.
4. **Не теряет данные при повороте экрана.** Это главный теоретический момент лабы.
5. **Обрабатывает ошибки:** деление на 0, неправильный ввод и т. п.

Чему лаба учит (это и есть «теория»):

- как строится экран из **View** (кнопки, текстовые поля) и **Layout** (контейнеры);
- что такое **Activity** и её **жизненный цикл**;
- как делать **обработчик нажатий**;
- как **сохранять данные при повороте** (`onSaveInstanceState`);
- как устроен **Android-проект** и как подключать **библиотеки**.

---

# Часть 2. Как мы делали лабу (по шагам)

1. **Прочитали задание** (docx) и методичку (PDF). В методичке используется **Java** и **XML-разметка**, поэтому выбрали их же.
2. **Решили, как считать выражения.** Задание разрешает библиотеки (EvalEx, mXparser), но мы написали **свой вычислитель** `ExpressionEvaluator`. Так все сообщения об ошибках на русском, и можно объяснить, как считается приоритет.
3. **Создали структуру проекта** как в Android Studio: Gradle-файлы, манифест, папки `java` и `res`.
4. **Сделали две разметки:** `res/layout/activity_main.xml` для портретной ориентации и `res/layout-land/activity_main.xml` для альбомной. Внешний вид взяли со скриншотов из задания.
5. **Написали `MainActivity`:** обработку нажатий, ввод, вывод результата, сохранение при повороте.
6. **Написали unit-тесты** для вычислителя и прогнали их: все 8 прошли.
7. **Написали README**, инструкцию для Mac, ответы на вопросы и Word-файлы.

---

# Часть 3. Где лежит код

```
app/src/main/
├── AndroidManifest.xml              ← «паспорт» приложения
├── java/com/example/calculator/
│   ├── MainActivity.java            ← экран (кнопки, ввод, поворот)
│   ├── ExpressionEvaluator.java     ← вычисление выражения
│   └── CalculationException.java    ← класс ошибки
└── res/
    ├── layout/activity_main.xml       ← разметка (портрет)
    ├── layout-land/activity_main.xml  ← разметка (альбом)
    └── values/                        ← строки, цвета, стили
```

**Как всё связано:**

```
Запуск → Манифест → MainActivity.onCreate() → setContentView(разметка)
Нажатие кнопки → onClick() → input() → render() → текст на экране
Кнопка «=» → calculate() → ExpressionEvaluator.evaluate() → ответ или ошибка
Поворот → onSaveInstanceState() → Activity уничтожается → создаётся заново → onRestoreInstanceState()
```

---

# Часть 4. MainActivity.java — каждая строка

## Пакет и импорты

```java
package com.example.calculator;      // пакет — «адрес» класса

import android.graphics.Color;       // константы цветов (нужен Color.TRANSPARENT)
import android.os.Bundle;            // Bundle — «коробка» ключ→значение для сохранения данных
import android.util.Log;             // запись сообщений в Logcat (журнал)
import android.view.View;            // View — базовый класс любого элемента экрана
import android.view.ViewGroup;       // ViewGroup — контейнер, в котором лежат другие View
import android.widget.Button;        // кнопка
import android.widget.TextView;      // текстовое поле
import android.widget.Toast;         // всплывающее сообщение внизу экрана

import androidx.activity.EdgeToEdge;             // рисовать приложение на весь экран
import androidx.activity.SystemBarStyle;         // цвет значков в статус-баре
import androidx.annotation.NonNull;              // пометка «не может быть null»
import androidx.appcompat.app.AppCompatActivity; // базовый класс экрана
import androidx.core.content.ContextCompat;      // достать цвет из ресурсов
import androidx.core.graphics.Insets;            // размеры системных панелей
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar; // верхняя панель с заголовком
```

## Объявление класса

```java
public class MainActivity extends AppCompatActivity implements View.OnClickListener {
```

- `extends AppCompatActivity`: наш класс **наследует** Activity, то есть становится экраном и получает жизненный цикл.
- `implements View.OnClickListener`: класс обещает иметь метод `onClick(View)`, то есть сам умеет обрабатывать нажатия.

## Константы

```java
private static final String TAG = "MainActivity";            // метка для фильтра в Logcat

private static final String KEY_EXPRESSION = "expression";   // ключи, под которыми
private static final String KEY_RESULT = "result";           // данные кладутся в Bundle
private static final String KEY_RESULT_IS_ERROR = "result_is_error";
private static final String KEY_JUST_EVALUATED = "just_evaluated";
private static final String KEY_DEGREE_MODE = "degree_mode";

private static final int MAX_LENGTH = 200;   // максимальная длина выражения
private static final String MINUS = "−";     // минус с кнопки (это не дефис "-")
```

`private` — видно только внутри класса, `static` — общее для класса, `final` — нельзя изменить. Вместе это константа.

## Поля (переменные объекта)

```java
private TextView expressionView;   // поле, где показано выражение
private TextView resultView;       // поле, где показан результат или ошибка
private Button angleModeButton;    // кнопка RAD/DEG

private String expression = "";          // что ввёл пользователь, например "2+sin(30"
private String result = "";              // результат ("4") или текст ошибки
private boolean resultIsError = false;   // true — в result лежит ошибка
private boolean justEvaluated = false;   // true — только что нажали "="
private boolean degreeMode = false;      // false = радианы, true = градусы
```

**Ключевая идея:** всё, что на экране, строится из этих 5 переменных. Поэтому при повороте достаточно сохранить только их.

## onCreate — создание экрана

```java
@Override                                        // переопределяем метод родителя
protected void onCreate(Bundle savedInstanceState) {
    // savedInstanceState = null при первом запуске, после поворота — сохранённые данные
    super.onCreate(savedInstanceState);          // обязательно: родитель делает свою инициализацию
    Log.d(TAG, "onCreate");                      // пишем в журнал «onCreate»
    EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT)); // на весь экран, светлые значки
    setContentView(R.layout.activity_main);      // подключаем XML-разметку; Android сам выберет
                                                 // layout/ или layout-land/ по ориентации

    MaterialToolbar toolbar = findViewById(R.id.toolbar); // находим элемент по его id из XML
    setSupportActionBar(toolbar);                // делаем его «шапкой» экрана
    expressionView = findViewById(R.id.expression);       // запоминаем ссылки на элементы
    resultView = findViewById(R.id.result);
    angleModeButton = findViewById(R.id.btn_angle_mode);
    ViewGroup keypad = findViewById(R.id.keypad);         // контейнер со всеми кнопками

    applySystemBarInsets(findViewById(R.id.main), toolbar, keypad); // отступы от системных панелей
    setClickListenerOnButtons(keypad);           // назначить обработчик всем кнопкам
    render();                                    // нарисовать начальное состояние
}
```

## applySystemBarInsets — отступы (на защите не главное)

```java
private void applySystemBarInsets(View root, View toolbar, View keypad) {
    int toolbarHeight = toolbar.getMinimumHeight();   // обычная высота тулбара
    ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {  // система сообщает
        Insets bars = windowInsets.getInsets(                              // размеры своих панелей
                WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
        v.setPadding(bars.left, 0, bars.right, 0);           // отступы слева/справа
        toolbar.setMinimumHeight(toolbarHeight + bars.top);  // тулбар выше на высоту статус-бара
        toolbar.setPadding(0, bars.top, 0, 0);               // заголовок ниже статус-бара
        keypad.setPadding(0, 0, 0, bars.bottom);             // кнопки выше панели навигации
        return WindowInsetsCompat.CONSUMED;                  // «отступы обработаны»
    });
}
```

Этот метод нужен, чтобы на новых Android интерфейс не залезал под часы и кнопки навигации. `(v, windowInsets) -> {...}` — это **лямбда**, короткая запись обработчика.

## setClickListenerOnButtons — один обработчик на все кнопки

```java
private void setClickListenerOnButtons(ViewGroup group) {
    for (int i = 0; i < group.getChildCount(); i++) {  // перебираем всё, что лежит в контейнере
        View child = group.getChildAt(i);              // i-й элемент
        if (child instanceof Button) {                 // если это кнопка —
            child.setOnClickListener(this);            // нажатия на неё обрабатывает наш onClick
        } else if (child instanceof ViewGroup) {       // если это строка с кнопками —
            setClickListenerOnButtons((ViewGroup) child); // заходим внутрь (рекурсия)
        }
    }
}
```

Вместо 30 одинаковых строк для каждой кнопки — один цикл. Код работает с обеими разметками.

## onClick — что делать при нажатии

```java
@Override
public void onClick(View view) {         // view — та кнопка, которую нажали
    int id = view.getId();               // её id
    if (id == R.id.btn_clear) {          // AC
        clear();
    } else if (id == R.id.btn_backspace) {   // ⌫
        backspace();
    } else if (id == R.id.btn_equals) {      // =
        calculate();
    } else if (id == R.id.btn_angle_mode) {  // RAD/DEG
        toggleAngleMode();
    } else if (view.getTag() != null) {      // любая другая кнопка (цифра, +, sin...)
        input(view.getTag().toString());     // вставить текст из её android:tag
    }
    render();                            // после любого нажатия обновить экран
}
```

**Что такое tag:** в XML у кнопки «sin» написано `android:text="sin"` (это видно на кнопке) и `android:tag="sin("` (это вставится в выражение).

## input — ввод символа

```java
private void input(String token) {       // token — текст кнопки, например "7" или "+"
    if (justEvaluated) {                 // если до этого нажали "="
        justEvaluated = false;
        expression = continuesPreviousResult(token) ? result : "";
        // нажали оператор (+, ×) → продолжаем от результата: "5" → "5×"
        // нажали цифру → начинаем новое выражение с нуля
    }
    resultIsError = false;               // при новом вводе старая ошибка исчезает

    if (token.equals(".")) {             // точка — особые правила
        appendDecimalPoint();
    } else if (isBinaryOperator(token)) { // + − × ÷ ^ — особые правила
        appendOperator(token);
    } else {                             // остальное просто дописываем
        append(token);
    }
}
```

`условие ? A : B` — тернарный оператор: если условие верно, берётся A, иначе B.

## appendDecimalPoint — запрет «1.2.3»

```java
private void appendDecimalPoint() {
    boolean hasDigits = false;
    for (int i = expression.length() - 1; i >= 0; i--) { // идём по строке с конца
        char c = expression.charAt(i);                   // i-й символ
        if (c == '.') {
            return;                      // в этом числе уже есть точка — выходим
        }
        if (!Character.isDigit(c)) {
            break;                       // не цифра — число кончилось, остановиться
        }
        hasDigits = true;                // перед точкой есть цифры
    }
    append(hasDigits ? "." : "0.");      // нет цифр → пишем "0." вместо "."
}
```

## appendOperator — правильная вставка операторов

```java
private void appendOperator(String operator) {
    char last = expression.isEmpty() ? 0 : expression.charAt(expression.length() - 1); // последний символ
    if (operator.equals(MINUS) && last != '+' && last != '−') {
        append(operator);               // минус можно почти везде: "−5", "(−3", "2×−3"
        return;
    }
    String base = expression;
    while (!base.isEmpty() && isBinaryOperator(base.substring(base.length() - 1))) {
        base = base.substring(0, base.length() - 1);  // убираем операторы в конце строки
    }
    if (base.isEmpty() || base.endsWith("(")) {
        return;                         // "×" в начале или после "(" бессмысленно — игнор
    }
    expression = base;
    append(operator);                   // "2+" и нажали "×" → станет "2×"
}
```

## append — дописать текст

```java
private void append(String text) {
    if (expression.length() + text.length() > MAX_LENGTH) {    // если слишком длинно —
        Toast.makeText(this, R.string.expression_too_long, Toast.LENGTH_SHORT).show(); // сообщение
        return;
    }
    expression += text;                 // дописываем в конец
}
```

## backspace — стереть

```java
private void backspace() {
    justEvaluated = false;
    resultIsError = false;
    if (expression.isEmpty()) {
        return;                         // нечего стирать
    }
    for (String function : new String[]{"sin(", "cos(", "tan(", "cot(", "log(", "ln("}) {
        if (expression.endsWith(function)) {          // строка кончается на "sin("?
            expression = expression.substring(0, expression.length() - function.length());
            return;                                   // стираем всё "sin(" целиком
        }
    }
    expression = expression.substring(0, expression.length() - 1); // иначе стираем 1 символ
}
```

`substring(0, n)` — кусок строки с начала до n-го символа (не включая его).

## clear — кнопка AC

```java
private void clear() {
    expression = "";        // всё обнуляем
    result = "";
    resultIsError = false;
    justEvaluated = false;
}
```

## calculate — кнопка «=» (здесь обработка ошибок)

```java
private void calculate() {
    if (expression.isEmpty()) {
        return;
    }
    expression = closeParentheses(expression);   // "sin(30" → "sin(30)"
    try {                                        // пробуем посчитать
        result = ExpressionEvaluator.format(ExpressionEvaluator.evaluate(expression, degreeMode));
        resultIsError = false;
        justEvaluated = true;
    } catch (CalculationException e) {           // если вычислитель сообщил об ошибке —
        result = e.getMessage();                 // берём её текст, например "Деление на ноль"
        resultIsError = true;
        justEvaluated = false;
    }
}
```

**try/catch:** если в блоке `try` произошла ошибка (исключение), программа **не падает**, а переходит в `catch`.

## toggleAngleMode — RAD/DEG

```java
private void toggleAngleMode() {
    degreeMode = !degreeMode;    // ! — «наоборот»: true ↔ false
    if (justEvaluated) {
        calculate();             // если результат уже на экране — пересчитать
    }
}
```

## render — вывод на экран

```java
private void render() {
    expressionView.setText(expression);            // показать выражение
    angleModeButton.setText(degreeMode ? R.string.angle_mode_deg : R.string.angle_mode_rad);

    if (resultIsError) {                           // ошибка → красным
        resultView.setTextColor(ContextCompat.getColor(this, R.color.result_error));
        resultView.setText(result);
    } else if (justEvaluated) {                    // результат после "=" → "= 4"
        resultView.setTextColor(ContextCompat.getColor(this, R.color.result_text));
        resultView.setText(getString(R.string.result_format, result)); // "= %1$s" → "= 4"
    } else {                                       // во время ввода → серый предпросмотр
        resultView.setTextColor(ContextCompat.getColor(this, R.color.result_preview));
        String preview = preview();
        resultView.setText(preview == null ? "" : getString(R.string.result_format, preview));
    }
}
```

## preview — предпросмотр во время ввода

```java
private String preview() {
    if (expression.isEmpty()) {
        return null;
    }
    try {
        double value = ExpressionEvaluator.evaluate(closeParentheses(expression), degreeMode);
        return ExpressionEvaluator.format(value);
    } catch (CalculationException e) {
        return null;      // пока ввод не закончен ("2+") — ошибку не показываем
    }
}
```

## Сохранение при повороте — главное для защиты

```java
@Override
protected void onSaveInstanceState(@NonNull Bundle outState) { // вызывается ПЕРЕД уничтожением
    super.onSaveInstanceState(outState);           // родитель сохраняет своё
    Log.d(TAG, "onSaveInstanceState: " + expression);
    outState.putString(KEY_EXPRESSION, expression);    // кладём в Bundle:
    outState.putString(KEY_RESULT, result);            //   ключ → значение
    outState.putBoolean(KEY_RESULT_IS_ERROR, resultIsError);
    outState.putBoolean(KEY_JUST_EVALUATED, justEvaluated);
    outState.putBoolean(KEY_DEGREE_MODE, degreeMode);
}

@Override
protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) { // ПОСЛЕ onStart
    super.onRestoreInstanceState(savedInstanceState);
    expression = savedInstanceState.getString(KEY_EXPRESSION, ""); // достаём по ключу,
    result = savedInstanceState.getString(KEY_RESULT, "");         // "" — если ничего нет
    resultIsError = savedInstanceState.getBoolean(KEY_RESULT_IS_ERROR);
    justEvaluated = savedInstanceState.getBoolean(KEY_JUST_EVALUATED);
    degreeMode = savedInstanceState.getBoolean(KEY_DEGREE_MODE);
    Log.d(TAG, "onRestoreInstanceState: " + expression);
    render();                                      // показать восстановленное
}
```

## Методы жизненного цикла

```java
@Override protected void onStart()   { super.onStart();   Log.d(TAG, "onStart"); }   // экран виден
@Override protected void onResume()  { super.onResume();  Log.d(TAG, "onResume"); }  // экран активен
@Override protected void onPause()   { super.onPause();   Log.d(TAG, "onPause"); }   // теряет фокус
@Override protected void onStop()    { super.onStop();    Log.d(TAG, "onStop"); }    // не виден
@Override protected void onDestroy() { super.onDestroy(); Log.d(TAG, "onDestroy"); } // уничтожается
```

В файле каждый метод записан в несколько строк, суть та же: они только пишут своё имя в журнал, чтобы на защите показать жизненный цикл.

## Вспомогательные методы

```java
private static boolean isBinaryOperator(String token) {     // это + − × ÷ ^ ?
    return token.equals("+") || token.equals(MINUS) || token.equals("×")
            || token.equals("÷") || token.equals("^");
}

private static boolean continuesPreviousResult(String token) { // продолжает ли после "="
    return isBinaryOperator(token) || token.equals("!") || token.equals("%") || token.equals("^2");
}

private static String closeParentheses(String text) {   // дописать недостающие ")"
    int open = 0;                                        // счётчик открытых скобок
    for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i);
        if (c == '(') {
            open++;                                      // "(" → +1
        } else if (c == ')' && open > 0) {
            open--;                                      // ")" → −1
        }
    }
    StringBuilder builder = new StringBuilder(text);
    for (int i = 0; i < open; i++) {
        builder.append(')');                             // дописываем недостающие ")"
    }
    return builder.toString();
}
```

---

# Часть 5. ExpressionEvaluator.java — как считается выражение

## Главная идея: «рекурсивный спуск»

Каждому уровню приоритета соответствует свой метод:

```
parseExpression   →  + −         (самый слабый приоритет)
  parseTerm       →  × ÷
    parseUnary    →  −5 (минус перед числом)
      parsePower  →  ^
        parsePostfix  →  5!  15%
          parsePrimary →  число, π, e, √, sin(...), ( ... )   (самый сильный)
```

Метод слабого приоритета **вызывает** метод сильного. Поэтому сильные операции выполняются первыми.

**Пример `2+3×4`:**

1. `parseExpression` просит у `parseTerm` первое слагаемое.
2. `parseTerm` читает `2`, видит `+` (это не его оператор) и возвращает **2**.
3. `parseExpression` видит `+` и снова зовёт `parseTerm`.
4. `parseTerm` читает `3`, видит `×`, читает `4` и возвращает **12**.
5. Получается 2 + 12 = **14**.

**Скобки:** встретив `(`, `parsePrimary` вызывает `parseExpression` заново, и выражение в скобках считается целиком и раньше всего остального.

## Основные строки

```java
public final class ExpressionEvaluator {             // final — от класса нельзя наследоваться
    private static final double EPSILON = 1e-12;     // «почти ноль»: sin(π) = 1e-16 → считаем 0
    private static final int MAX_FACTORIAL = 170;    // 171! не помещается в double
    private static final String[] NAMES = {          // какие имена функций понимаем
            "sqrt", "sin", "cos", "tan", "cot", "ctg", "abs", "log", "tg", "ln", "lg", "pi", "e"
    };

    public static double evaluate(String expression, boolean degrees) {
        return new Parser(expression, degrees).parse();  // создаём разборщик и запускаем
    }
```

## format — красивый вывод числа

```java
public static String format(double value) {
    if (value == 0) {
        return "0";                                   // убирает "-0"
    }
    BigDecimal rounded = new BigDecimal(value).round(new MathContext(12)).stripTrailingZeros();
    // округлить до 12 значащих цифр и убрать нули в конце: 0.30000000000000004 → 0.3
    double abs = Math.abs(value);
    if (abs >= 1e12 || abs < 1e-6) {
        return rounded.toString();                    // огромные/крошечные → 1.5E+15
    }
    return rounded.toPlainString();                   // обычные → 3.14159265359
}
```

## Парсер — поля

```java
private final String text;     // разбираемая строка
private final boolean degrees; // градусы или радианы
private int pos;               // «курсор»: какой символ сейчас читаем
```

## parse — старт

```java
double parse() {
    skipSpaces();
    if (pos >= text.length()) {                                  // строка пустая
        throw new CalculationException("Введите выражение");
    }
    double value = parseExpression();                            // считаем
    skipSpaces();
    if (pos < text.length()) {                                   // что-то осталось непрочитанным
        char c = text.charAt(pos);
        if (c == ')') {
            throw new CalculationException("Лишняя закрывающая скобка");
        }
        throw new CalculationException("Неожиданный символ «" + c + "»");
    }
    return value;
}
```

`throw` **выбрасывает** ошибку. Её ловит `catch` в `MainActivity.calculate()`.

## Сложение и вычитание

```java
private double parseExpression() {
    double value = parseTerm();                          // первое слагаемое
    while (true) {                                       // пока есть + или −
        if (eat('+')) {
            value = check(value + parseTerm());
        } else if (eat('-') || eat('−')) {
            value = check(value - parseTerm());
        } else {
            return value;                                // операторов больше нет
        }
    }
}
```

## Умножение и деление (тут проверка деления на 0)

```java
private double parseTerm() {
    double value = parseUnary();
    while (true) {
        if (eat('*') || eat('×')) {
            value = check(value * parseUnary());
        } else if (eat('/') || eat('÷')) {
            double divisor = parseUnary();               // делитель
            if (divisor == 0) {
                throw new CalculationException("Деление на ноль");  // ← ОШИБКА
            }
            value = check(value / divisor);
        } else if (startsOperand()) {
            value = check(value * parseUnary());         // неявное умножение: 2π, 2(3+4)
        } else {
            return value;
        }
    }
}
```

## Унарный минус, степень, факториал

```java
private double parseUnary() {
    if (eat('-') || eat('−')) {
        return -parseUnary();     // "−5" → минус пять
    }
    if (eat('+')) {
        return parseUnary();      // "+5" → 5
    }
    return parsePower();
}

private double parsePower() {
    double base = parsePostfix();                 // основание
    if (eat('^')) {
        return power(base, parseUnary());         // показатель: 2^3^2 = 2^9
    }
    return base;
}

private double parsePostfix() {
    double value = parsePrimary();
    while (true) {
        if (eat('!')) {
            value = factorial(value);             // 5! = 120
        } else if (eat('%')) {
            value = value / 100;                  // 15% = 0.15
        } else {
            return value;
        }
    }
}
```

## parsePrimary — «атомы»

```java
private double parsePrimary() {
    skipSpaces();
    if (pos >= text.length()) {
        throw new CalculationException("Выражение не завершено");  // например "2+"
    }
    char c = text.charAt(pos);
    if (eat('(')) {                               // скобка → считаем выражение внутри
        double value = parseExpression();
        expectClosingParenthesis();               // обязательно ")"
        return value;
    }
    if (eat('√')) {
        return sqrt(parseUnary());                // корень
    }
    if (eat('π')) {
        return Math.PI;                           // 3.14159...
    }
    if (isDigit(c) || c == '.') {
        return parseNumber();                     // число
    }
    if (isLatinLetter(c)) {
        return parseName();                       // sin, cos, ln, e, pi...
    }
    if (c == ')') {
        throw new CalculationException("Пустые скобки");
    }
    throw new CalculationException("Пропущено число перед «" + c + "»"); // например "×3"
}
```

## parseNumber и parseName

- **`parseNumber`** читает цифры и одну точку. Вторая точка даёт ошибку «Лишняя точка в числе». Ещё он понимает запись вида `1.5E+12`. В конце `Double.parseDouble(...)` превращает текст в число.
- **`parseName`** ищет в строке одно из имён `NAMES`: для `pi` и `e` возвращает константу; для функции требует `(`, считает аргумент, требует `)` и вызывает `applyFunction`.

## applyFunction — функции и их ошибки

```java
case "sin": return snapToZero(Math.sin(toRadians(x)));  // toRadians: если DEG — градусы в радианы
case "cos": return snapToZero(Math.cos(toRadians(x)));
case "tan":  // если cos ≈ 0 (90°) → "Тангенс не определён"
case "cot":  // если sin ≈ 0 (0°)  → "Котангенс не определён"
case "ln":   requirePositiveForLog(x); return Math.log(x);    // x ≤ 0 → ошибка
case "log":  requirePositiveForLog(x); return Math.log10(x);
case "sqrt": return sqrt(x);                                   // x < 0 → ошибка
case "abs":  return Math.abs(x);                               // модуль
```

`tan` и `cot` в файле занимают по несколько строк: там сначала проверка на ноль, потом деление. Здесь они сокращены до комментариев.

## Методы-проверки

| Метод | Ошибка |
|---|---|
| `sqrt(x)` | x < 0 → «Корень из отрицательного числа» |
| `power(a, b)` | 0 в минусовой степени → «Деление на ноль»; (−8)^0.5 → ошибка |
| `factorial(n)` | дробное или отрицательное → ошибка; > 170 → «Слишком большое число» |
| `requirePositiveForLog(x)` | x ≤ 0 → ошибка логарифма |
| `check(v)` | NaN → «Результат не определён», бесконечность → «Слишком большое число» |

## Мелкие помощники

```java
private boolean eat(char expected) {       // если следующий символ = expected,
    skipSpaces();
    if (pos < text.length() && text.charAt(pos) == expected) {
        pos++;                             // «съедаем» его (двигаем курсор)
        return true;
    }
    return false;
}
```

`skipSpaces()` пропускает пробелы. `startsOperand()` проверяет, начинается ли дальше число или скобка (нужно для неявного умножения).

## CalculationException.java

```java
public class CalculationException extends RuntimeException { // наш тип ошибки
    public CalculationException(String message) {
        super(message);   // сохраняем текст; потом достаём через e.getMessage()
    }
}
```

---

# Часть 6. XML-файлы

## AndroidManifest.xml

```xml
<application android:icon="@mipmap/ic_launcher"        иконка
             android:label="@string/app_name"          название «Калькулятор»
             android:theme="@style/Theme.Calculator">  тема
    <activity android:name=".MainActivity" android:exported="true">   наш экран
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />          главный экран
            <category android:name="android.intent.category.LAUNCHER" /> иконка в меню
        </intent-filter>
    </activity>
</application>
```

**Важно:** `android:configChanges` мы **не** указали. Поэтому при повороте Activity пересоздаётся и подхватывает `layout-land`.

## Разметка (на примере одной строки кнопок)

```xml
<LinearLayout                              контейнер
    android:id="@+id/main"                 @+id — создать id (в коде R.id.main)
    android:layout_width="match_parent"    ширина на весь экран
    android:layout_height="match_parent"   высота на весь экран
    android:orientation="vertical">        дети идут сверху вниз

    <TextView android:id="@+id/expression"   поле выражения
        android:layout_height="0dp"
        android:layout_weight="1.6"          доля свободной высоты
        android:gravity="bottom|end" />      текст прижат вниз-вправо

    <LinearLayout android:id="@+id/keypad" ...>      клавиатура
        <LinearLayout android:orientation="horizontal"   одна строка
                      android:layout_height="0dp" android:layout_weight="1">
            <Button style="@style/CalcKey" android:text="1" android:tag="1" />
            <Button style="@style/CalcKey" android:text="2" android:tag="2" />
            ...
```

- **`layout_weight`:** ставим `0dp` и вес 1 у каждой строки, и свободное место делится поровну. Кнопка «=» имеет вес 2, поэтому она в 2 раза шире.
- **`style="@style/CalcKey"`:** общие настройки всех кнопок (размер шрифта, цвет, ширина) вынесены в `values/themes.xml`, чтобы не повторять их 30 раз.
- **`layout-land`:** те же id, но другая сетка, 7×5. Android сам берёт этот файл при горизонтальном экране.

## Ресурсы

- `values/strings.xml` — тексты. Их хранят отдельно от кода, чтобы приложение можно было перевести на другой язык.
- `values/colors.xml` — цвета, `values-night/colors.xml` — цвета для тёмной темы.
- `values/dimens.xml` и `values-land/dimens.xml` — размер шрифта (в альбоме мельче).

## app/build.gradle.kts

```kotlin
minSdk = 26          // минимальный Android 8.0
targetSdk = 36       // под какую версию рассчитано
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")            // AppCompatActivity
    implementation("com.google.android.material:material:1.12.0")   // красивые кнопки
    testImplementation("junit:junit:4.13.2")                        // для тестов
}
```

Так и **подключаются сторонние библиотеки** (вопрос 10).

---

# Часть 7. Что спросят на защите

## Скорее всего попросят показать

1. **Запустить** приложение и посчитать пример из задания: `12−(sin(3^3)+ln(√4))` → `10.350476891`.
2. **Повернуть экран.** Выражение не пропадает, разметка меняется.
3. **Показать ошибку:** `5÷0` → «Деление на ноль».
4. **Показать, где сохраняются данные:** открыть `onSaveInstanceState` и `onRestoreInstanceState`.
5. **Показать обработчик нажатия:** `implements View.OnClickListener`, `setOnClickListener(this)`, `onClick`.
6. **Показать жизненный цикл** в Logcat: фильтр `tag:MainActivity`, поворот экрана.

## Вопросы по теории (короткие ответы)

| Вопрос | Ответ |
|---|---|
| Что такое Activity? | Один экран приложения с интерфейсом. |
| 4 компонента приложения? | Activity, Service, BroadcastReceiver, ContentProvider. |
| Жизненный цикл? | onCreate → onStart → onResume → onPause → onStop → onDestroy (+ onRestart). |
| Что при повороте? | Activity уничтожается и создаётся заново: onPause → onStop → onSaveInstanceState → onDestroy → onCreate → onStart → onRestoreInstanceState → onResume. |
| Почему пропадают данные? | Создаётся новый объект Activity, поля старого теряются. |
| Где ещё можно восстановить? | В `onCreate`, если `savedInstanceState != null`. |
| Что такое Bundle? | «Коробка» ключ→значение (строки, числа, Parcelable). |
| Почему TextView не сохраняет текст сам? | Сам сохраняет только EditText, а TextView — только с `freezesText="true"`. |
| Альтернатива Bundle? | ViewModel: переживает поворот, но не смерть процесса. |
| Что такое View и ViewGroup? | View — элемент (кнопка, текст), ViewGroup — контейнер (LinearLayout). |
| Основные Layout? | LinearLayout, ConstraintLayout, FrameLayout, RelativeLayout, GridLayout. |
| match_parent / wrap_content? | По размеру родителя / по размеру содержимого. |
| dp, sp, px? | px — пиксели; dp — не зависят от плотности экрана; sp — как dp, но учитывают размер шрифта в настройках (для текста). |
| gravity / layout_gravity? | Положение содержимого внутри View / положение самой View в родителе. |
| Что такое R? | Автосгенерированный класс с id всех ресурсов. |
| Структура проекта? | manifests, java, res (layout, values, drawable, mipmap), Gradle-файлы. |
| Зачем манифест? | Описывает приложение: компоненты, главный экран, разрешения, иконку. |
| Как подключить библиотеку? | Строка `implementation("...")` в `build.gradle`, затем Sync. |
| Как Android выбирает layout-land? | По квалификатору папки ресурсов (land = альбомная ориентация). |
| Как учитывается приоритет? | Рекурсивный спуск: свой метод на каждый уровень приоритета. |
| Как обрабатываются ошибки? | Вычислитель бросает `CalculationException`, Activity ловит её в try/catch и показывает текст. |
| Почему не библиотека? | Разрешено, но не обязательно; свой парсер даёт понятные ошибки на русском. |

## Могут попросить что-то изменить прямо на защите

Это частый приём. Потренируйся заранее:

- **Поменять цвет:** `res/values/colors.xml`, например `equals_background`.
- **Показать Toast при нажатии «=»:** в `calculate()` добавить строку `Toast.makeText(this, "Считаю", Toast.LENGTH_SHORT).show();`
- **Добавить функцию, например arcsin** — три шага ниже.

1. В массив `NAMES` добавить `"asin"` в самое начало.
2. В `applyFunction` добавить новый `case` (код ниже).
3. В разметку добавить кнопку: `<Button style="@style/CalcKey.Function" android:text="asin" android:tag="asin(" />`. Кнопке не нужен свой обработчик: его назначит цикл `setClickListenerOnButtons`.

```java
case "asin":
    if (x < -1 || x > 1) throw new CalculationException("asin определён на [-1; 1]");
    return degrees ? Math.toDegrees(Math.asin(x)) : Math.asin(x);
```

**Последний совет:** до защиты запусти приложение и пройди Части 4 и 7 с открытым кодом в Android Studio. Попробуй сам добавить asin по шагам выше: после этого ты точно сможешь объяснить любую строку.
