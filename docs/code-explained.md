# Разбор кода калькулятора (подготовка к защите)

## 0. Где код

```
aliplexxx_code/
├── settings.gradle.kts          ← какие модули есть в проекте и откуда качать библиотеки
├── build.gradle.kts             ← версия Android Gradle Plugin
├── gradle.properties            ← настройки Gradle
├── gradlew, gradle/wrapper/     ← Gradle Wrapper (скачивает нужную версию Gradle)
└── app/                         ← модуль приложения
    ├── build.gradle.kts         ← minSdk, targetSdk, зависимости (библиотеки)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml                          ← манифест
        │   ├── java/com/example/calculator/
        │   │   ├── MainActivity.java                       ← ЭКРАН: кнопки, ввод, поворот
        │   │   ├── ExpressionEvaluator.java                ← ВЫЧИСЛЕНИЕ выражения
        │   │   └── CalculationException.java               ← класс ошибки вычисления
        │   └── res/
        │       ├── layout/activity_main.xml                ← разметка (портрет)
        │       ├── layout-land/activity_main.xml           ← разметка (альбом)
        │       ├── values/strings.xml, colors.xml,
        │       │   dimens.xml, themes.xml                  ← строки, цвета, размеры, стили
        │       ├── values-land/dimens.xml                  ← размеры шрифта в альбомной ориентации
        │       ├── values-night/colors.xml                 ← цвета тёмной темы
        │       ├── drawable/                               ← иконка «стереть», картинка для иконки приложения
        │       └── mipmap-anydpi-v26/                      ← иконка приложения
        └── test/java/.../ExpressionEvaluatorTest.java       ← unit-тесты
```

В Android Studio слева переключи вид панели на **Android**. Тогда код будет в `app → java → com.example.calculator`,
а ресурсы — в `app → res`.

## 1. Как всё работает (общая картина)

1. Пользователь нажимает на иконку. Android смотрит в **AndroidManifest.xml**, находит Activity с
   `MAIN/LAUNCHER`, то есть `MainActivity`, создаёт её и вызывает `onCreate()`.
2. В `onCreate()` вызывается `setContentView(R.layout.activity_main)`. Система сама выбирает файл:
   в портретной ориентации `res/layout/activity_main.xml`, в альбомной — `res/layout-land/activity_main.xml`.
3. Каждая кнопка в XML хранит в `android:tag` текст, который она вставляет, например у кнопки «sin» это `sin(`.
4. При нажатии вызывается `onClick()`. Он меняет строку `expression` и вызывает `render()`, который показывает её на экране.
5. При нажатии «=» вызывается `ExpressionEvaluator.evaluate(...)`. Он разбирает строку и считает ответ.
   При ошибке выбрасывается `CalculationException` с текстом ошибки, и этот текст выводится красным.
6. При повороте экрана Activity **уничтожается и создаётся заново**. Перед уничтожением данные кладутся в `Bundle`
   в методе `onSaveInstanceState()`, после пересоздания достаются в `onRestoreInstanceState()`.

---

## 2. MainActivity.java — построчно

### Пакет и импорты (строки 1–21)

```java
package com.example.calculator;
```
Пакет — «папка», в которой лежит класс. Совпадает с `namespace` в `app/build.gradle.kts`.

```java
import android.graphics.Color;          // константы цветов (Color.TRANSPARENT)
import android.os.Bundle;               // Bundle — «словарь» ключ→значение для сохранения состояния
import android.util.Log;                // вывод сообщений в Logcat
import android.view.View;               // базовый класс всех элементов экрана
import android.view.ViewGroup;          // контейнер для других View (LinearLayout — это ViewGroup)
import android.widget.Button;           // кнопка
import android.widget.TextView;         // текстовое поле
import android.widget.Toast;            // всплывающее сообщение внизу экрана

import androidx.activity.EdgeToEdge;    // рисование приложения «от края до края» (под статус-баром)
import androidx.activity.SystemBarStyle;// стиль системных панелей (светлые/тёмные значки)
import androidx.annotation.NonNull;     // аннотация «параметр не может быть null»
import androidx.appcompat.app.AppCompatActivity; // базовый класс Activity с поддержкой старых версий Android
import androidx.core.content.ContextCompat;      // безопасное получение цвета из ресурсов
import androidx.core.graphics.Insets;            // размеры системных отступов (статус-бар, навигация)
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar; // верхняя панель с заголовком
```
`android.*` — классы самой системы, `androidx.*` и `com.google.android.material.*` — библиотеки,
подключённые в `app/build.gradle.kts`.

### Объявление класса (строка 23)

```java
public class MainActivity extends AppCompatActivity implements View.OnClickListener {
```
- `extends AppCompatActivity`: наш экран **наследуется** от Activity и получает весь её жизненный цикл.
- `implements View.OnClickListener`: класс сам обрабатывает нажатия, то есть обязан иметь метод `onClick(View)`.

### Константы (строки 25–35)

```java
private static final String TAG = "MainActivity";
```
Метка для Logcat: по ней фильтруются наши сообщения.

```java
private static final String KEY_EXPRESSION = "expression";
private static final String KEY_RESULT = "result";
private static final String KEY_RESULT_IS_ERROR = "result_is_error";
private static final String KEY_JUST_EVALUATED = "just_evaluated";
private static final String KEY_DEGREE_MODE = "degree_mode";
```
Ключи для `Bundle`: под этими именами данные сохраняются при повороте и по ним же достаются обратно.
`static final` значит константа, общая для класса.

```java
private static final int MAX_LENGTH = 200;   // максимальная длина выражения
private static final String MINUS = "−";     // «красивый» минус с кнопки (не дефис "-")
```

### Поля — ссылки на View (строки 37–39)

```java
private TextView expressionView;   // верхнее поле: вводимое выражение
private TextView resultView;       // нижнее поле: результат или ошибка
private Button angleModeButton;    // кнопка RAD/DEG (меняем её текст)
```

### Поля — состояние калькулятора (строки 41–45)

```java
private String expression = "";          // то, что ввёл пользователь, например "2+sin(30"
private String result = "";              // последний результат ("4") или текст ошибки
private boolean resultIsError = false;   // true — в result лежит текст ошибки
private boolean justEvaluated = false;   // true — только что нажали "=" и получили ответ
private boolean degreeMode = false;      // false — радианы, true — градусы
```
Именно эти 5 переменных сохраняются при повороте экрана. Всё, что видно на экране, строится из них.

### onCreate (строки 47–65)

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
```
`@Override` — переопределяем метод родителя. `onCreate` — первый метод жизненного цикла.
`savedInstanceState` равен `null` при первом запуске и содержит сохранённые данные после поворота.

```java
    super.onCreate(savedInstanceState);
```
Обязательный вызов метода родителя: Activity выполняет свою внутреннюю инициализацию.

```java
    Log.d(TAG, "onCreate");
```
Пишет «onCreate» в Logcat (d = debug). Так на защите можно показать порядок вызовов.

```java
    EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));
```
Приложение рисуется на весь экран, в том числе под статус-баром. Статус-бар прозрачный, его значки светлые,
потому что верхняя панель приложения тёмная.

```java
    setContentView(R.layout.activity_main);
```
Связывает Activity с XML-разметкой. `R.layout.activity_main` — автоматически созданный id файла.
Android сам выбирает `layout/` или `layout-land/` в зависимости от ориентации.

```java
    MaterialToolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
```
`findViewById` находит в разметке элемент по `android:id`. Тулбар становится «шапкой» (ActionBar) экрана.

```java
    expressionView = findViewById(R.id.expression);
    resultView = findViewById(R.id.result);
    angleModeButton = findViewById(R.id.btn_angle_mode);
    ViewGroup keypad = findViewById(R.id.keypad);
```
Сохраняем ссылки на элементы, с которыми будем работать.

```java
    applySystemBarInsets(findViewById(R.id.main), toolbar, keypad);  // отступы от системных панелей
    setClickListenerOnButtons(keypad);                               // назначить обработчик всем кнопкам
    render();                                                        // нарисовать начальное состояние
}
```

### applySystemBarInsets (строки 67–80)

Нужен, чтобы кнопки не оказались под панелью навигации, а заголовок — под статус-баром.
```java
int toolbarHeight = toolbar.getMinimumHeight();          // обычная высота тулбара (56dp)
ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {   // система сообщает размеры панелей
    Insets bars = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()); // статус-бар, навигация, вырез камеры
    v.setPadding(bars.left, 0, bars.right, 0);            // отступы слева/справа (в альбомной ориентации там навигация)
    toolbar.setMinimumHeight(toolbarHeight + bars.top);   // тулбар становится выше на высоту статус-бара
    toolbar.setPadding(0, bars.top, 0, 0);                // а его заголовок сдвигается вниз
    keypad.setPadding(0, 0, 0, bars.bottom);              // клавиатура приподнимается над панелью навигации
    return WindowInsetsCompat.CONSUMED;                   // отступы обработаны
});
```
`(v, windowInsets) -> {...}` — **лямбда**, то есть короткая запись анонимного класса-обработчика.
К теме лабораторной это не относится и нужно только для красоты на новых версиях Android.

### setClickListenerOnButtons (строки 82–92)

```java
private void setClickListenerOnButtons(ViewGroup group) {
    for (int i = 0; i < group.getChildCount(); i++) {   // перебираем всех детей контейнера
        View child = group.getChildAt(i);
        if (child instanceof Button) {                  // если это кнопка —
            child.setOnClickListener(this);             // её нажатия обрабатывает эта Activity (метод onClick)
        } else if (child instanceof ViewGroup) {        // если это вложенный контейнер (строка кнопок) —
            setClickListenerOnButtons((ViewGroup) child); // заходим внутрь (рекурсия)
        }
    }
}
```
Не нужно писать 30 строк `findViewById(...).setOnClickListener(...)`: обработчик назначается всем кнопкам в цикле.
Код работает с обеими разметками, хотя кнопки в них расположены по-разному.

### onClick — обработчик нажатий (строки 94–109)

```java
@Override
public void onClick(View view) {          // view — кнопка, которую нажали
    int id = view.getId();
    if (id == R.id.btn_clear) {           // AC
        clear();
    } else if (id == R.id.btn_backspace) {// ⌫
        backspace();
    } else if (id == R.id.btn_equals) {   // =
        calculate();
    } else if (id == R.id.btn_angle_mode) { // RAD/DEG
        toggleAngleMode();
    } else if (view.getTag() != null) {   // любая другая кнопка: цифра, оператор, функция
        input(view.getTag().toString());  // вставляем текст из android:tag
    }
    render();                             // после любого нажатия перерисовываем экран
}
```
Используется `if/else`, а не `switch`: в новых версиях Android Gradle Plugin id ресурсов не являются
константами, и `switch (id)` по ним не компилируется.

### input — ввод символа (строки 111–126)

```java
private void input(String token) {
    if (justEvaluated) {                    // если перед этим нажали "="
        justEvaluated = false;
        expression = continuesPreviousResult(token) ? result : "";
    }
```
Поведение после «=» как у обычного калькулятора. Нажали оператор (`+`, `×`, `!`…), значит продолжаем
считать от результата (`5` → `5×`). Нажали цифру, значит начинаем новое выражение.
`условие ? A : B` — тернарный оператор («если условие, то A, иначе B»).

```java
    resultIsError = false;                  // при новом вводе убираем старую ошибку
    if (token.equals(".")) {
        appendDecimalPoint();               // точка — особые правила
    } else if (isBinaryOperator(token)) {
        appendOperator(token);              // + − × ÷ ^ — особые правила
    } else {
        append(token);                      // всё остальное просто дописываем
    }
}
```

### appendDecimalPoint (строки 128–141)

```java
boolean hasDigits = false;
for (int i = expression.length() - 1; i >= 0; i--) {  // идём с конца строки назад
    char c = expression.charAt(i);
    if (c == '.') return;                // в текущем числе уже есть точка — вторую не ставим
    if (!Character.isDigit(c)) break;    // дошли до не-цифры — число закончилось
    hasDigits = true;
}
append(hasDigits ? "." : "0.");          // перед точкой нет цифр — пишем "0."
```
Не даёт ввести `1.2.3`, а `.` в начале числа превращает в `0.`.

### appendOperator (строки 143–160)

```java
char last = expression.isEmpty() ? 0 : expression.charAt(expression.length() - 1); // последний символ
if (operator.equals(MINUS) && last != '+' && last != '−') {
    append(operator);       // минус можно ставить почти везде: "−5", "(−3", "2×−3"
    return;
}
String base = expression;
while (!base.isEmpty() && isBinaryOperator(base.substring(base.length() - 1))) {
    base = base.substring(0, base.length() - 1);   // убираем операторы в конце
}
if (base.isEmpty() || base.endsWith("(")) {
    return;                 // "×" в начале или после "(" не имеет смысла — игнорируем
}
expression = base;
append(operator);           // "2+" и нажали "×" → получится "2×" (оператор заменился)
```

### append (строки 162–168)

```java
if (expression.length() + text.length() > MAX_LENGTH) {
    Toast.makeText(this, R.string.expression_too_long, Toast.LENGTH_SHORT).show(); // всплывающее сообщение
    return;
}
expression += text;   // дописываем текст в конец строки
```

### backspace (строки 170–184)

```java
justEvaluated = false;
resultIsError = false;
if (expression.isEmpty()) return;     // нечего стирать
for (String function : new String[]{"sin(", "cos(", "tan(", "cot(", "log(", "ln("}) {
    if (expression.endsWith(function)) {                         // строка кончается на "sin("?
        expression = expression.substring(0, expression.length() - function.length()); // стираем целиком
        return;
    }
}
expression = expression.substring(0, expression.length() - 1);  // иначе стираем 1 символ
```
`substring(0, n)` — часть строки с 0-го по n-й символ (не включая n-й).

### clear (строки 186–191)
AC: обнуляет все поля состояния.

### calculate — кнопка «=» (строки 193–207)

```java
if (expression.isEmpty()) return;
expression = closeParentheses(expression);   // "sin(30" → "sin(30)"
try {
    result = ExpressionEvaluator.format(ExpressionEvaluator.evaluate(expression, degreeMode));
    resultIsError = false;
    justEvaluated = true;
} catch (CalculationException e) {           // вычислитель сообщил об ошибке
    result = e.getMessage();                 // например "Деление на ноль"
    resultIsError = true;
    justEvaluated = false;
}
```
**Обработка ошибок:** `try { ... } catch (...) { ... }`. Если внутри `try` выброшено исключение, программа
не падает, а переходит в `catch`, и мы показываем текст ошибки.

### toggleAngleMode (строки 209–214)
Переключает `degreeMode`. Если на экране уже показан результат, пересчитывает его в новом режиме.

### render — отрисовка (строки 216–233)

```java
expressionView.setText(expression);    // показать выражение
angleModeButton.setText(degreeMode ? R.string.angle_mode_deg : R.string.angle_mode_rad);

if (resultIsError) {                   // ошибка → красный текст
    resultView.setTextColor(ContextCompat.getColor(this, R.color.result_error));
    resultView.setText(result);
} else if (justEvaluated) {            // результат после "=" → обычный цвет, "= 4"
    resultView.setTextColor(ContextCompat.getColor(this, R.color.result_text));
    resultView.setText(getString(R.string.result_format, result));  // строка "= %1$s" → "= 4"
} else {                               // во время ввода → серый предпросмотр
    resultView.setTextColor(ContextCompat.getColor(this, R.color.result_preview));
    String preview = preview();
    resultView.setText(preview == null ? "" : getString(R.string.result_format, preview));
}
```
Главная идея: **экран строится только из переменных состояния**. Поэтому после поворота достаточно
восстановить переменные и вызвать `render()`.

### preview (строки 235–246)
Пробует посчитать выражение во время ввода. При ошибке возвращает `null` и ничего не показывает:
пока пользователь не закончил ввод, `2+` — это ещё не ошибка.

### onSaveInstanceState — сохранение (строки 249–258)

```java
@Override
protected void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);            // родитель сохраняет своё (например, текст в EditText)
    Log.d(TAG, "onSaveInstanceState: " + expression);
    outState.putString(KEY_EXPRESSION, expression); // кладём строку под ключом "expression"
    outState.putString(KEY_RESULT, result);
    outState.putBoolean(KEY_RESULT_IS_ERROR, resultIsError);
    outState.putBoolean(KEY_JUST_EVALUATED, justEvaluated);
    outState.putBoolean(KEY_DEGREE_MODE, degreeMode);
}
```
Система вызывает этот метод **перед** уничтожением Activity при повороте.

### onRestoreInstanceState — восстановление (строки 260–270)

```java
@Override
protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    expression = savedInstanceState.getString(KEY_EXPRESSION, ""); // "" — значение по умолчанию
    result = savedInstanceState.getString(KEY_RESULT, "");
    resultIsError = savedInstanceState.getBoolean(KEY_RESULT_IS_ERROR);
    justEvaluated = savedInstanceState.getBoolean(KEY_JUST_EVALUATED);
    degreeMode = savedInstanceState.getBoolean(KEY_DEGREE_MODE);
    Log.d(TAG, "onRestoreInstanceState: " + expression);
    render();                                                       // показать восстановленное
}
```
Вызывается **после `onStart()`** и только если есть что восстанавливать (Bundle не null).

### Методы жизненного цикла (строки 274–302)
`onStart`, `onResume`, `onPause`, `onStop`, `onDestroy` только вызывают `super` и пишут своё имя в Logcat.
Это нужно для демонстрации жизненного цикла.

### Вспомогательные методы (строки 306–333)
- `isBinaryOperator(token)` — `true` для `+ − × ÷ ^`.
- `continuesPreviousResult(token)` — `true` для операторов, которые после «=» продолжают считать от результата.
- `closeParentheses(text)` — считает незакрытые `(` и дописывает нужное количество `)`.

---

## 3. ExpressionEvaluator.java — как считается выражение

### Идея: рекурсивный спуск

Строка `2+3×4` должна дать 14, а не 20: умножение выполняется раньше сложения. Для этого каждому
**уровню приоритета** отводится свой метод. Метод низкого приоритета вызывает метод более высокого:

```
parseExpression   +  −           (самый низкий приоритет)
  └ parseTerm     ×  ÷
     └ parseUnary унарный минус: −5
        └ parsePower  ^           (2^3^2 = 2^9, справа налево)
           └ parsePostfix  !  %   (5!, 15%)
              └ parsePrimary  число, π, e, √, функция(...), (...)   (самый высокий)
```

Пример `2+3×4`: `parseExpression` просит у `parseTerm` первое слагаемое. `parseTerm` читает `2`,
видит `+`, а это не его оператор, поэтому возвращает 2. `parseExpression` съедает `+` и снова вызывает `parseTerm`.
Тот читает `3`, видит `×`, читает `4` и возвращает 12. Итог: 2 + 12 = 14.

Скобки: `parsePrimary`, увидев `(`, вызывает `parseExpression` с самого начала, то есть выражение в скобках
считается целиком и раньше всего остального.

### Строки 22–45

```java
public final class ExpressionEvaluator {                 // final — от класса нельзя наследоваться
    private static final double EPSILON = 1e-12;          // «почти ноль»: sin(π) даёт 1.2e-16, считаем это 0
    private static final int MAX_FACTORIAL = 170;         // 171! больше максимального double
    private static final String[] NAMES = {...};          // имена функций и констант, которые понимаем
    private ExpressionEvaluator() {}                      // приватный конструктор: объект не создаём, всё static

    public static double evaluate(String expression, boolean degrees) {
        return new Parser(expression, degrees).parse();   // главный метод: создаём разборщик и запускаем
    }
```

### format (строки 47–58): превращает число в красивую строку

```java
if (value == 0) return "0";                              // заодно убирает "-0"
BigDecimal rounded = new BigDecimal(value).round(new MathContext(12)).stripTrailingZeros();
```
Округляет до 12 значащих цифр и убирает лишние нули: `0.1+0.2` в double равно `0.30000000000000004`,
а покажется `0.3`. Очень большие и очень маленькие числа выводятся как `1.5E+15`.

### Parser (строки 60–322)

```java
private final String text;     // разбираемая строка
private final boolean degrees; // градусы или радианы
private int pos;               // текущая позиция в строке («курсор»)
```

**parse()** (строки 70–85) — точка входа. Проверяет, что строка не пустая, вызывает `parseExpression()`, а
затем проверяет, что строка разобрана до конца. Если остался символ, например лишняя `)`, выдаёт ошибку.

**parseExpression()** — сложение/вычитание:
```java
double value = parseTerm();                  // первое слагаемое
while (true) {
    if (eat('+')) value = check(value + parseTerm());           // нашли + → прибавляем следующее слагаемое
    else if (eat('-') || eat('−')) value = check(value - parseTerm());
    else return value;                       // операторов + − больше нет — готово
}
```

**parseTerm()** — умножение/деление:
```java
} else if (eat('/') || eat('÷')) {
    double divisor = parseUnary();
    if (divisor == 0) throw new CalculationException("Деление на ноль");  // ← обработка деления на 0
    value = check(value / divisor);
} else if (startsOperand()) {
    value = check(value * parseUnary());     // неявное умножение: 2π, 2(3+4)
}
```
`throw` выбрасывает исключение, и выполнение сразу переходит в `catch` в `MainActivity.calculate()`.

**parseUnary()** — `−5`, `−(2+3)`: если видим минус, считаем то, что после него, и меняем знак.

**parsePower()** — `base ^ exponent`. Показатель читается через `parseUnary()`, поэтому работают `2^−1` и
`2^3^2` = 2^9 (степень правоассоциативна).

**parsePostfix()** — после числа могут идти `!` (факториал) и `%` (делит на 100).

**parsePrimary()** — самый «атом»:
- `(` → `parseExpression()`, затем обязательно `)`, иначе ошибка «Не хватает закрывающей скобки»;
- `√` → корень из следующего выражения;
- `π` → `Math.PI`;
- цифра или точка → `parseNumber()`;
- латинская буква → `parseName()` (функция или константа);
- иначе — ошибка «Пропущено число перед …» (например, выражение `×3`).

**parseNumber()** — читает цифры и одну точку (вторая точка вызывает ошибку «Лишняя точка в числе»), а также
экспоненту `E+12`. Затем `Double.parseDouble` превращает текст в число.

**parseName()** — ищет в начале оставшейся строки одно из имён `NAMES` (`sin`, `cos`, `ln`, `pi`, `e`…).
Для `pi` и `e` возвращает константу, для функции требует `(`, считает аргумент, требует `)` и вызывает `applyFunction`.

**applyFunction()** — `switch` по имени функции:
- `sin`, `cos` → `Math.sin/cos`, перед этим `toRadians` переводит градусы в радианы, если включён DEG;
- `tan` → sin/cos, а если cos ≈ 0 (90°), ошибка «Тангенс не определён»;
- `cot` → cos/sin, а если sin ≈ 0, ошибка;
- `ln` → `Math.log`, `log` → `Math.log10`; если x ≤ 0, ошибка;
- `sqrt` → корень, `abs` → модуль.

**eat(char)** — если следующий символ совпадает с ожидаемым, «съедает» его (`pos++`) и возвращает `true`.
**skipSpaces()** пропускает пробелы. **startsOperand()** проверяет, начинается ли дальше число, скобка или
функция (для неявного умножения).

### Проверки и функции с ошибками (строки 324–388)

| Метод | Что проверяет |
|---|---|
| `sqrt(x)` | x < 0 → «Корень из отрицательного числа» |
| `power(a, b)` | 0 в отрицательной степени → «Деление на ноль»; отрицательное число в дробной степени → ошибка (кроме нечётных корней: (−8)^(1/3) = −2) |
| `factorial(n)` | n дробное или отрицательное → ошибка; n > 170 → «Слишком большое число» |
| `requirePositiveForLog(x)` | x ≤ 0 → ошибка логарифма |
| `snapToZero(v)` | очень маленькое число считает нулём (sin(180°) = 0, а не 1.2e-16) |
| `check(v)` | `NaN` → «Результат не определён», `Infinity` → «Слишком большое число» |

## 4. CalculationException.java

```java
public class CalculationException extends RuntimeException {   // наследник стандартного исключения
    public CalculationException(String message) {
        super(message);                                         // сохраняем текст, достаём через getMessage()
    }
}
```
Свой класс ошибки нужен, чтобы отличать «ошибку пользователя в выражении» от настоящих сбоев программы.

---

## 5. XML-файлы

### AndroidManifest.xml

```xml
<application
    android:allowBackup="true"                 разрешить резервное копирование данных
    android:icon="@mipmap/ic_launcher"         иконка приложения
    android:label="@string/app_name"           название под иконкой («Калькулятор»)
    android:roundIcon="@mipmap/ic_launcher_round"
    android:supportsRtl="true"                 поддержка языков справа-налево
    android:theme="@style/Theme.Calculator">   тема оформления из themes.xml
    <activity android:name=".MainActivity" android:exported="true">   наш экран; exported — его можно запустить извне (лаунчером)
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />          это главная точка входа
            <category android:name="android.intent.category.LAUNCHER" /> показывать иконку в списке приложений
        </intent-filter>
    </activity>
</application>
```
`android:configChanges` **специально не указан**. Поэтому при повороте Activity пересоздаётся, загружает
альбомную разметку, а данные восстанавливаются из Bundle. Именно это требуется в задании.

### layout/activity_main.xml (портрет)

```xml
<LinearLayout                                  корневой контейнер: элементы друг под другом
    android:id="@+id/main"                     @+id — создать id "main" (в коде R.id.main)
    android:layout_width="match_parent"        ширина = ширина родителя (весь экран)
    android:layout_height="match_parent"
    android:background="@color/keypad_background"
    android:orientation="vertical"             вертикально
    tools:context=".MainActivity">             подсказка для редактора Studio, на работу не влияет
```

```xml
<com.google.android.material.appbar.MaterialToolbar      верхняя панель с заголовком
    android:id="@+id/toolbar"
    android:layout_height="wrap_content"       высота по содержимому
    android:minHeight="?attr/actionBarSize"    но не меньше стандартной высоты панели
    app:title="@string/app_name" />
```

```xml
<TextView
    android:id="@+id/expression"               поле выражения
    style="@style/CalcDisplay"                 общие настройки из стиля (фон, отступы, автоподбор шрифта)
    android:layout_weight="1.6"                доля свободной высоты (см. ниже про веса)
    android:gravity="bottom|end"               текст прижат вниз и вправо
    android:hint="@string/expression_hint"     серый "0", когда поле пустое
    android:maxLines="3"
    app:autoSizeMaxTextSize="44sp" />          шрифт уменьшается, если выражение длинное
```

```xml
<View android:layout_height="1dp" android:background="@color/display_divider" />   линия-разделитель
```

Поле `result` устроено так же. Затем идёт клавиатура:

```xml
<LinearLayout android:id="@+id/keypad" android:layout_height="0dp" android:layout_weight="6.2"
              android:orientation="vertical">         столбец из строк
    <LinearLayout android:layout_height="0dp" android:layout_weight="1"
                  android:orientation="horizontal">    одна строка кнопок
        <Button style="@style/CalcKey" android:text="1" android:tag="1" />
        <Button style="@style/CalcKey" android:text="2" android:tag="2" />
        ...
```
- `android:text` — что написано на кнопке, `android:tag` — что вставится в выражение
  (у «xʸ» текст `xʸ`, а tag `^`; у «sin» текст `sin`, а tag `sin(`).
- **Веса (`layout_weight`)**: `layout_height="0dp"` и вес 1 у каждой строки означают, что свободная высота делится
  поровну. Внутри строки у каждой кнопки `layout_width="0dp"` и вес 1 (задано в стиле), поэтому кнопки
  одинаковой ширины. У «=» вес 2, она в два раза шире.
- Портрет: сверху 3 строки функций по 5 кнопок (вес 0.8, чуть ниже), снизу цифровой блок 4×5, как на рисунке в задании.

### layout-land/activity_main.xml (альбом)
Те же id, но другая сетка: 7 столбцов × 5 строк, и дисплей ниже. Папка `layout-land` — это
**квалификатор ресурсов**: Android сам берёт файл отсюда, когда экран повёрнут горизонтально.
Код Activity не меняется, потому что id одинаковые.

### values/strings.xml
Все тексты интерфейса (`app_name`, `RAD`, `DEG`, `= %1$s`). В коде они берутся как `R.string.имя`.
Тексты хранят в ресурсах, а не в коде, чтобы приложение можно было перевести на другой язык.

### values/colors.xml и values-night/colors.xml
Цвета по именам. В тёмной теме телефона Android берёт цвета из `values-night`.

### values/dimens.xml и values-land/dimens.xml
Размер шрифта кнопок: 26sp в портрете и 18sp в альбоме (кнопки там меньше).

### values/themes.xml — тема и стили
```xml
<style name="Theme.Calculator" parent="Theme.Material3.DayNight.NoActionBar">   тема приложения (Material 3, без стандартной шапки — свою даём тулбаром)
<style name="CalcKey" parent="Widget.Material3.Button.TextButton">             стиль обычной клавиши
    layout_width=0dp, layout_weight=1      кнопки делят строку поровну
    insetTop/insetBottom=0, cornerRadius=0 убрать отступы и скругления — кнопки стыкуются в сетку
    textSize=@dimen/key_text_size          размер берётся из dimens (свой для альбома)
<style name="CalcKey.Function">            наследник CalcKey (через точку): меньше шрифт и другой фон
<style name="CalcKey.Equals">              кнопка "=": тёмный фон, белый текст
<style name="CalcDisplay">                 общие настройки двух полей дисплея
```
Стиль — набор атрибутов, который можно применить ко многим View, чтобы не повторять их 30 раз.

### drawable/ и mipmap-anydpi-v26/
`ic_backspace.xml` — векторная иконка ⌫ на кнопке стирания. `ic_launcher_*` — иконка приложения
(фон + векторный рисунок «+ − × =»).

---

## 6. Gradle-файлы

**settings.gradle.kts**
```kotlin
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } } // откуда качать плагины
dependencyResolutionManagement { repositories { google(); mavenCentral() } }        // откуда качать библиотеки
rootProject.name = "Calculator"
include(":app")                                                                     // в проекте один модуль — app
```

**build.gradle.kts (корневой)**
```kotlin
plugins { id("com.android.application") version "8.13.0" apply false }   // версия Android Gradle Plugin
```

**app/build.gradle.kts**
```kotlin
plugins { id("com.android.application") }      // этот модуль — Android-приложение
android {
    namespace = "com.example.calculator"        // пакет класса R
    compileSdk = 36                             // с какой версией Android API компилируем
    defaultConfig {
        applicationId = "com.example.calculator"// уникальный id приложения на устройстве
        minSdk = 26                             // минимальная версия Android: 8.0
        targetSdk = 36                          // под какую версию приложение рассчитано
        versionCode = 1; versionName = "1.0"    // номер версии
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11 ... }  // версия языка Java
}
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")               // AppCompatActivity
    implementation("androidx.activity:activity:1.10.1")                // EdgeToEdge
    implementation("com.google.android.material:material:1.12.0")      // Material-кнопки, Toolbar
    testImplementation("junit:junit:4.13.2")                           // JUnit — только для тестов
}
```

**gradle.properties**: `android.useAndroidX=true` — использовать библиотеки AndroidX;
`org.gradle.jvmargs=-Xmx2048m` — сколько памяти дать Gradle.

## 7. ExpressionEvaluatorTest.java
Unit-тесты на JUnit 4 запускаются на компьютере без эмулятора. Каждый метод с `@Test` проверяет вычислитель:
`assertEquals(14, rad("2+3×4"), DELTA)` значит «ожидаем 14, допускаем погрешность DELTA».
`assertError("5÷0", "Деление на ноль")` значит «ожидаем такую ошибку».

---

## 8. Что могут спросить по теории (кроме 10 вопросов из задания — они в `answers.md`)

**Почему при повороте экрана данные пропадают, если ничего не делать?**
Поворот — это изменение конфигурации. Android уничтожает Activity (`onPause → onStop → onDestroy`) и создаёт
новую (`onCreate → onStart → onResume`), а все поля старого объекта теряются. Поэтому их сохраняют в
`onSaveInstanceState`.

**Порядок вызовов при повороте?**
`onPause → onStop → onSaveInstanceState → onDestroy → onCreate → onStart → onRestoreInstanceState → onResume`.
(На Android до 9 `onSaveInstanceState` вызывался до `onStop`.)

**Где ещё можно восстановить данные, кроме `onRestoreInstanceState`?**
В `onCreate(savedInstanceState)`: проверить `if (savedInstanceState != null)` и прочитать оттуда.

**Какие данные можно положить в Bundle?**
Примитивы (`int`, `boolean`, `double`…), `String`, массивы, а также объекты, реализующие `Parcelable` или `Serializable`.
Bundle предназначен для небольших данных.

**Почему EditText сохраняет текст сам, а TextView — нет?**
Стандартные View с `android:id` сами сохраняют своё состояние. EditText сохраняет текст, а TextView — только при
`android:freezesText="true"`. В калькуляторе используются TextView, поэтому сохраняем вручную.

**Что такое `android:configChanges` и почему он не используется?**
Он говорит системе не пересоздавать Activity при повороте, а вызвать `onConfigurationChanged()`. Тогда не
подхватится `layout-land`, и придётся всё менять вручную. Google рекомендует этот способ только в особых случаях.

**Что будет, если нажать «Домой»? А «Назад»?**
«Домой»: `onPause → onStop` (+ `onSaveInstanceState`). Activity остаётся в памяти, при возврате вызываются
`onRestart → onStart → onResume`. «Назад»: `onPause → onStop → onDestroy`, и состояние не сохраняется,
потому что пользователь сам закрыл экран.

**Что такое ViewModel и чем отличается от onSaveInstanceState?**
ViewModel — объект, который переживает поворот: он не пересоздаётся, и в нём можно держать большие данные.
Но если система убьёт процесс, ViewModel пропадёт, а Bundle из `onSaveInstanceState` сохранится.

**Как Android выбирает разметку для альбомной ориентации?**
По квалификаторам папок ресурсов: `layout-land` для альбомной ориентации, `values-night` для тёмной темы,
`values-ru` для русского языка, `drawable-hdpi` для плотности экрана. Имя файла должно совпадать.

**Что такое класс R?**
Автоматически сгенерированный класс с целочисленными id всех ресурсов: `R.layout.activity_main`,
`R.id.result`, `R.string.app_name`, `R.color...`.

**Что такое `@+id/имя` и `@id/имя`?**
`@+id` создаёт новый id, `@id` ссылается на существующий. `@string/…`, `@color/…` — ссылки на ресурсы.

**Чем отличаются dp, sp, px?**
`px` — реальные пиксели. `dp` — независимые от плотности пиксели (на любом экране выглядят примерно одинаково).
`sp` — как dp, но учитывают размер шрифта в настройках телефона, поэтому текст задают в `sp`.

**`match_parent` и `wrap_content`?**
`match_parent` — растянуть по размеру родителя, `wrap_content` — по размеру содержимого.
`0dp` + `layout_weight` — разделить свободное место пропорционально весам.

**`gravity` и `layout_gravity`?**
`gravity` — как содержимое расположено внутри View (текст прижат вправо). `layout_gravity` — как сама View
расположена внутри родителя.

**Что такое Context?**
Доступ к ресурсам и системным службам приложения. Activity является Context, поэтому мы передаём `this` в
`Toast.makeText(this, ...)` и `ContextCompat.getColor(this, ...)`.

**Что такое Intent?**
«Намерение» — сообщение для запуска компонента: другой Activity, Service, Broadcast. Пример:
`startActivity(new Intent(this, SecondActivity.class))`.

**minSdk, targetSdk, compileSdk?**
`minSdk` — минимальная версия Android, на которую ставится приложение. `targetSdk` — версия, под поведение
которой приложение протестировано. `compileSdk` — версия API, с которой компилируется код.

**Что такое Gradle?**
Система сборки. Компилирует код, скачивает библиотеки и собирает APK (установочный файл приложения).

**Что такое AndroidX / AppCompat?**
Библиотеки поддержки от Google. `AppCompatActivity` даёт одинаковое поведение и новые возможности на старых версиях Android.

**Что такое Logcat?**
Окно журнала сообщений устройства. `Log.d(TAG, "текст")` пишет туда отладочные сообщения.

**Что такое Toast?**
Короткое всплывающее сообщение внизу экрана, которое исчезает само. В калькуляторе показывается, когда выражение слишком длинное.

**Как калькулятор учитывает приоритет операций?**
Рекурсивным спуском: на каждый уровень приоритета свой метод (`parseExpression` → `parseTerm` → `parsePower` …),
и метод низкого приоритета вызывает метод высокого. Скобки разбираются рекурсивным вызовом `parseExpression`.
(Другой известный способ — алгоритм сортировочной станции Дейкстры с переводом в обратную польскую запись.)

**Как обрабатываются ошибки?**
Вычислитель при недопустимой операции выбрасывает `CalculationException` с понятным текстом. `MainActivity`
ловит его в `try/catch` и показывает текст красным. Приложение не падает.

**Почему не использовали библиотеку (EvalEx, mXparser)?**
Задание разрешает библиотеки, но не требует. Свой парсер полностью контролирует сообщения об ошибках
(на русском) и не тянет лишних зависимостей. Подключение библиотеки — одна строка в `dependencies`
(см. вопрос 10 в `answers.md`).

**Как реализован обработчик нажатий?**
`MainActivity implements View.OnClickListener`. В `onCreate` всем кнопкам в цикле назначается
`setOnClickListener(this)`. В `onClick` по id определяется специальная кнопка, а у остальных берётся
`android:tag` с текстом для вставки.

**Что делает `super.onCreate(savedInstanceState)`?**
Вызывает реализацию родительского класса. Без этого Activity не инициализируется, и система выбросит исключение.

**Что такое `@Override`?**
Аннотация, которая говорит, что метод переопределяет метод родителя. Если имя написано с ошибкой,
компилятор об этом сообщит.
