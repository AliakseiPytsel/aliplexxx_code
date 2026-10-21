# Сохранение данных при повороте, история и XML-разметки

# Часть 1. Проверка кода: что было и что изменилось

**Что было сделано правильно и осталось как есть:**

- вычисления с приоритетом операций, функции и обработка ошибок (`ExpressionEvaluator`, 8 unit-тестов проходят);
- две разметки: `layout/` (портрет) и `layout-land/` (альбом);
- в манифесте нет `android:configChanges`, поэтому при повороте Activity честно пересоздаётся;
- выражение, результат и режим RAD/DEG сохранялись в `onSaveInstanceState`.

**Что добавлено и переделано:**

1. **История вычислений.** Каждое успешное вычисление записывается как строка `"2+3 = 5"` в список `ArrayList<String> history`.
2. **История видна на экране.** В портретной ориентации над выражением появилось поле с историей. В обеих ориентациях на верхней панели есть кнопка **«История»**: она открывает окно со всеми записями. Нажатие на запись возвращает её выражение для правки.
3. **История сохраняется при повороте** вместе с остальными данными: `putStringArrayList` в `Bundle`.
4. **Восстановление перенесено в `onCreate` с проверкой `if (savedInstanceState != null)`.** Это классический вариант, который обычно ждут на защите.
5. **Добавлены все нужные проверки на `null`:** сам Bundle, список истории из Bundle, поле истории (в альбомной разметке его нет), окно истории.
6. **Если окно истории было открыто во время поворота, оно открывается снова.** Сам факт «окно открыто» тоже сохраняется в Bundle.

Проверка: код скомпилирован на заглушках Android API, поворот смоделирован программно (сохранение в Bundle → новая Activity → восстановление). История, выражение и открытое окно восстанавливаются, а при пустом Bundle приложение не падает.

---

# Часть 2. Что происходит при повороте телефона

Поворот экрана — это **изменение конфигурации** (configuration change). При этом Android:

1. **уничтожает** текущую Activity (объект `MainActivity` удаляется вместе со всеми полями);
2. **создаёт новую** Activity, которая загружает разметку под новую ориентацию (`layout-land`).

Поэтому без сохранения всё введённое пропало бы: новый объект создаётся с пустыми полями `expression = ""`, `history = new ArrayList<>()`.

**Порядок вызовов при повороте** (виден в Logcat по фильтру `tag:MainActivity`):

```
onPause()
onStop()
onSaveInstanceState(outState)     ← кладём данные в Bundle
onDestroy()                       ← старая Activity уничтожена
onCreate(savedInstanceState)      ← новая Activity, Bundle НЕ null → восстанавливаем
onStart()
onRestoreInstanceState(Bundle)    ← второй вариант места восстановления
onResume()
```

На Android 9 и новее `onSaveInstanceState` вызывается после `onStop`, на старых — перед ним.

**Почему не `android:configChanges="orientation|screenSize"`?** С ним Activity не пересоздаётся, а вызывается `onConfigurationChanged()`. Но тогда не подхватится `layout-land`, и всё пришлось бы перестраивать вручную. Задание требует именно сохранять данные при пересоздании, а Google рекомендует `configChanges` только в особых случаях.

---

# Часть 3. Что такое onSaveInstanceState

`onSaveInstanceState(Bundle outState)` — метод Activity, который **система вызывает сама**, когда Activity может быть уничтожена не по воле пользователя и потом восстановлена.

**Когда вызывается:**

- поворот экрана и другие изменения конфигурации (смена языка, тёмная тема);
- нажатие «Домой» или переход в другое приложение (система может потом убить процесс ради памяти).

**Когда НЕ вызывается:**

- пользователь нажал «Назад» или вызван `finish()`: экран закрыт навсегда, восстанавливать нечего.

**Что делаем внутри:** кладём в `outState` всё, без чего нельзя восстановить экран.

```java
@Override
protected void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);          // 1. родитель сохраняет своё (состояние View с id)
    outState.putString(KEY_EXPRESSION, expression);          // строка
    outState.putString(KEY_RESULT, result);                  // строка
    outState.putBoolean(KEY_RESULT_IS_ERROR, resultIsError); // логическое
    outState.putBoolean(KEY_JUST_EVALUATED, justEvaluated);
    outState.putBoolean(KEY_DEGREE_MODE, degreeMode);
    outState.putStringArrayList(KEY_HISTORY, history);       // список строк — ИСТОРИЯ
    outState.putBoolean(KEY_HISTORY_DIALOG_OPEN,             // было ли открыто окно истории
            historyDialog != null && historyDialog.isShowing());
    Log.d(TAG, "onSaveInstanceState: " + outState);
}
```

- `super.onSaveInstanceState(outState)` вызывается обязательно: так Android сохраняет состояние стандартных View с `android:id` (например, текст в `EditText`).
- `historyDialog != null && historyDialog.isShowing()`: сначала проверяем, что окно вообще создавалось (не `null`), и только потом спрашиваем, открыто ли оно. Если `historyDialog == null`, вторая часть не выполняется (это называется «короткое замыкание» `&&`), и ошибки `NullPointerException` не будет.

---

# Часть 4. В какой форме сохраняются данные: Bundle

**Bundle** — это контейнер «ключ → значение», похожий на словарь (`Map`). Ключ — строка, значение — данные простого типа.

**Как это работает внутри:**

1. Мы кладём данные в Bundle методами `putXxx(ключ, значение)`.
2. Android **сериализует** Bundle (превращает в набор байтов — `Parcel`) и хранит его в **системном процессе**, а не в нашем приложении.
3. При создании новой Activity система возвращает этот Bundle в `onCreate(savedInstanceState)` и `onRestoreInstanceState(...)`.

Поэтому данные переживают не только поворот, но и ситуацию, когда система **убила процесс** приложения в фоне, а пользователь потом вернулся.

**Что можно положить в Bundle:**

| Метод | Тип | Пример |
|---|---|---|
| `putString` / `getString` | строка | выражение `"2+3"` |
| `putInt`, `putLong`, `putDouble`, `putFloat` | числа | счётчик, результат |
| `putBoolean` / `getBoolean` | true/false | режим градусов |
| `putStringArrayList` / `getStringArrayList` | список строк `ArrayList<String>` | **история** |
| `putIntArray`, `putStringArray` | массивы | |
| `putParcelable`, `putSerializable` | свои объекты | объект, реализующий `Parcelable` |
| `putBundle` | вложенный Bundle | |

**Что лежит в нашем Bundle:**

| Ключ | Тип | Что значит |
|---|---|---|
| `"expression"` | String | вводимое выражение |
| `"result"` | String | результат или текст ошибки |
| `"result_is_error"` | boolean | показывать ли результат красным |
| `"just_evaluated"` | boolean | только что нажали «=» |
| `"degree_mode"` | boolean | градусы (true) или радианы (false) |
| `"history"` | `ArrayList<String>` | история, например `["2+3 = 5", "5×4 = 20"]` |
| `"history_dialog_open"` | boolean | было ли открыто окно истории |

Почему `ArrayList`, а не просто `List`: метод `putStringArrayList` принимает именно `ArrayList<String>`, поэтому поле истории объявлено как `ArrayList<String>`.

**Ограничение:** Bundle рассчитан на **небольшие** данные (десятки килобайт). Слишком большой Bundle (около 1 МБ) вызывает ошибку `TransactionTooLargeException`. Поэтому история ограничена 50 записями (`MAX_HISTORY = 50`).

Ключи вынесены в константы (`KEY_HISTORY = "history"`), чтобы при сохранении и восстановлении использовалась одна и та же строка и не было опечаток.

---

# Часть 5. Восстановление и проверка на null

## Где можно восстановить данные

| Место | Bundle может быть null? | Когда вызывается |
|---|---|---|
| `onCreate(Bundle savedInstanceState)` | **Да**: при первом запуске он `null` | всегда, первым |
| `onRestoreInstanceState(Bundle)` | Нет, вызывается только если есть что восстанавливать | после `onStart()` |

Мы восстанавливаем в `onCreate`, поэтому **обязательна проверка на null**:

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    // ... findViewById ...

    // Первый запуск: savedInstanceState == null, восстанавливать нечего.
    // После поворота: Activity создана заново, а в Bundle лежат данные из onSaveInstanceState.
    if (savedInstanceState != null) {
        restoreState(savedInstanceState);
    }
    render();                         // показать данные на экране
    if (historyDialogWasOpen) {
        showHistoryDialog();          // окно было открыто до поворота — открыть снова
    }
}
```

**Почему без проверки приложение упадёт:** при первом запуске `savedInstanceState == null`, и вызов `savedInstanceState.getString(...)` даст `NullPointerException`. Приложение закроется с ошибкой.

## Метод restoreState — чтение данных из Bundle

```java
private void restoreState(@NonNull Bundle state) {
    expression = state.getString(KEY_EXPRESSION, "");       // "" — значение по умолчанию
    result = state.getString(KEY_RESULT, "");
    resultIsError = state.getBoolean(KEY_RESULT_IS_ERROR, false);
    justEvaluated = state.getBoolean(KEY_JUST_EVALUATED, false);
    degreeMode = state.getBoolean(KEY_DEGREE_MODE, false);
    ArrayList<String> savedHistory = state.getStringArrayList(KEY_HISTORY);
    if (savedHistory != null) {        // getStringArrayList вернёт null, если ключа нет
        history = savedHistory;
    }
    historyDialogWasOpen = state.getBoolean(KEY_HISTORY_DIALOG_OPEN, false);
}
```

- `getString(ключ, "")`: второй аргумент — **значение по умолчанию**. Если ключа нет, вернётся `""`, а не `null`.
- `getBoolean(ключ, false)`: если ключа нет, вернётся `false`.
- `getStringArrayList(ключ)` **не имеет** значения по умолчанию и может вернуть `null`. Поэтому проверяем `if (savedHistory != null)`, иначе `history` стал бы `null`, и следующий вызов `history.add(...)` уронил бы приложение.

## onRestoreInstanceState — второй вариант

```java
@Override
protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    Log.d(TAG, "onRestoreInstanceState");
}
```

Здесь можно было бы восстанавливать те же данные (проверка на `null` не нужна, Bundle здесь никогда не `null`). Мы оставили метод только для лога, чтобы показать порядок вызовов. **Ответ на защите:** «восстанавливать можно в двух местах. В `onCreate` нужна проверка на null, потому что при первом запуске Bundle пустой. В `onRestoreInstanceState` Bundle никогда не null, и он вызывается после `onStart`».

## Все проверки на null в программе

| Где | Почему может быть null |
|---|---|
| `if (savedInstanceState != null)` в `onCreate` | при первом запуске Bundle нет |
| `if (savedHistory != null)` в `restoreState` | ключа `"history"` может не быть в Bundle |
| `if (historyView != null && historyScroll != null)` в `render` | в альбомной разметке поля истории нет, `findViewById` вернул null |
| `if (historyView != null)` в `onCreate` | то же: обработчик нажатия ставим только если поле есть |
| `historyDialog != null && historyDialog.isShowing()` | окно истории могли ни разу не открыть |
| `if (historyDialog != null)` в `onDestroy` | то же |

---

# Часть 6. Как работает история

## Хранение

```java
private static final int MAX_HISTORY = 50;
private ArrayList<String> history = new ArrayList<>(); // записи "выражение = результат", старые в начале
```

## Запись в историю — при успешном нажатии «=»

```java
private void calculate() {
    ...
    try {
        result = ExpressionEvaluator.format(ExpressionEvaluator.evaluate(expression, degreeMode));
        resultIsError = false;
        justEvaluated = true;
        addToHistory(getString(R.string.history_entry, expression, result)); // "%1$s = %2$s" → "2+3 = 5"
    } catch (CalculationException e) {
        ...                                   // ошибки в историю не попадают
    }
}

private void addToHistory(String entry) {
    int size = history.size();
    if (size > 0 && history.get(size - 1).equals(entry)) {
        return;                    // повторное нажатие "=" не дублирует запись
    }
    history.add(entry);            // добавляем в конец
    if (history.size() > MAX_HISTORY) {
        history.remove(0);         // самые старые записи удаляем
    }
}
```

## Показ на экране

```java
// в render():
if (historyView != null && historyScroll != null) {          // в альбомной разметке поля нет
    historyView.setText(TextUtils.join("\n", history));       // записи через перенос строки
    historyScroll.post(() -> historyScroll.fullScroll(View.FOCUS_DOWN)); // прокрутить к последней
}
```

- `TextUtils.join("\n", history)` склеивает все строки списка, ставя между ними перенос строки.
- Поле истории лежит внутри `ScrollView`, поэтому длинную историю можно прокручивать пальцем.
- `post(...)` выполняет прокрутку после того, как текст разложен по строкам, иначе высота текста ещё неизвестна.

## Кнопка «История» на верхней панели (меню)

Файл `res/menu/main_menu.xml`:

```xml
<menu xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    <item
        android:id="@+id/action_history"
        android:title="@string/history"
        app:showAsAction="always" />      всегда показывать на панели, а не в «⋮»
</menu>
```

```java
@Override
public boolean onCreateOptionsMenu(Menu menu) {               // система просит создать меню
    getMenuInflater().inflate(R.menu.main_menu, menu);         // загрузить его из XML
    return true;
}

@Override
public boolean onOptionsItemSelected(@NonNull MenuItem item) { // нажали пункт меню
    if (item.getItemId() == R.id.action_history) {
        showHistoryDialog();
        return true;                                           // нажатие обработано
    }
    return super.onOptionsItemSelected(item);
}
```

## Окно истории

```java
private void showHistoryDialog() {
    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.history)                    // заголовок «История»
            .setPositiveButton(R.string.close, null);      // кнопка «Закрыть»
    if (history.isEmpty()) {
        builder.setMessage(R.string.history_empty);        // «История пока пустая»
    } else {
        String[] items = new String[history.size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = history.get(history.size() - 1 - i);    // переворачиваем: новые сверху
        }
        builder.setItems(items, (dialog, which) -> useHistoryEntry(items[which])); // нажали запись
        builder.setNeutralButton(R.string.history_clear, (dialog, which) -> {      // «Очистить»
            history.clear();
            render();
        });
    }
    historyDialog = builder.show();                        // показать и запомнить окно
}

private void useHistoryEntry(String entry) {               // "2+3 = 5" → в поле ввода "2+3"
    int separator = entry.lastIndexOf(HISTORY_SEPARATOR);  // HISTORY_SEPARATOR = " = "
    expression = separator >= 0 ? entry.substring(0, separator) : entry;
    result = "";
    resultIsError = false;
    justEvaluated = false;
    render();
}
```

## Окно истории и поворот

Окно (`AlertDialog`) принадлежит старой Activity. При повороте она уничтожается, поэтому:

1. в `onSaveInstanceState` сохраняем, было ли окно открыто (`"history_dialog_open"`);
2. в `onDestroy` закрываем окно (`historyDialog.dismiss()`), иначе в Logcat будет ошибка **WindowLeaked** («утечка окна»);
3. в `onCreate` новой Activity, если окно было открыто, открываем его снова.

```java
@Override
protected void onDestroy() {
    if (historyDialog != null) {
        historyDialog.dismiss();
    }
    super.onDestroy();
}
```

## Что увидишь при проверке

1. В портретной ориентации посчитай `2+3 =` и `5×4 =`. Над выражением появятся строки истории.
2. Поверни телефон. В альбомной ориентации поля истории нет, но кнопка «История» открывает окно со всеми записями.
3. Поверни обратно. История на месте.
4. Открой «История», поверни телефон, и окно откроется снова.
5. В Logcat (`tag:MainActivity`) видно:

```
onSaveInstanceState: Bundle[{history=[2+3 = 5, 5×4 = 20], expression=..., ...}]
onDestroy
onCreate, savedInstanceState = Bundle[...]
restoreState: expression = ..., history = 2
onStart
onRestoreInstanceState
onResume
```

## Что будет, если закрыть приложение совсем

Bundle хранит данные **только пока жив этот экран**: при повороте, сворачивании, убийстве процесса в фоне. Если пользователь закрыл приложение кнопкой «Назад» или смахнул его из списка недавних, Bundle удаляется, и история пропадёт. Это нормально и соответствует заданию (сохранение при изменении конфигурации).

Если преподаватель спросит, как хранить историю **навсегда**, ответ: использовать постоянное хранилище, например **SharedPreferences** (небольшие данные «ключ-значение» в файле), базу данных **SQLite/Room** или обычный файл. Пример с SharedPreferences:

```java
// сохранить (например, в onPause):
getSharedPreferences("calc", MODE_PRIVATE).edit()
        .putString("history", TextUtils.join("\n", history))
        .apply();

// загрузить (в onCreate):
String saved = getSharedPreferences("calc", MODE_PRIVATE).getString("history", "");
if (!saved.isEmpty()) {
    history = new ArrayList<>(Arrays.asList(saved.split("\n")));
}
```

**Отличие:** Bundle — временное состояние экрана (поворот), SharedPreferences — постоянные данные, которые остаются после перезапуска приложения.

**Ещё одна альтернатива — ViewModel.** Это объект, который не уничтожается при повороте, поэтому данные в нём просто остаются. Но при убийстве процесса системой ViewModel пропадает, а Bundle сохраняется.

---

# Часть 7. XML-разметки (Layouts): какие бывают и зачем

**Layout** (`ViewGroup`) — контейнер, который **расставляет** внутри себя элементы (`View`): кнопки, тексты, другие контейнеры. Разные Layout расставляют элементы по-разному.

## LinearLayout — в линию

Ставит элементы **в один ряд**: горизонтально или вертикально.

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal">      horizontal — в строку, vertical — в столбец

    <Button
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"          вес: делит свободное место
        android:text="1" />

    <Button
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="2"          эта кнопка в 2 раза шире
        android:text="=" />
</LinearLayout>
```

Главные атрибуты:

- `android:orientation` — `horizontal` или `vertical`;
- `android:layout_weight` — вес элемента. Свободное место делится пропорционально весам. Ставят вместе с `layout_width="0dp"` (или `layout_height="0dp"` для вертикального);
- `android:gravity` — как расположено содержимое внутри;
- `android:layout_gravity` — как элемент расположен в родителе.

**Зачем:** самый простой контейнер для списков элементов и рядов кнопок. **В нашем калькуляторе:** весь экран — вертикальный `LinearLayout`, а клавиатура — вертикальный `LinearLayout` из горизонтальных строк, где у каждой кнопки вес 1, а у «=» вес 2.

## TableLayout — таблица

Ставит элементы **в строки и столбцы**, как таблица. Каждая строка — `TableRow`, а элементы в `TableRow` — ячейки. Ширина столбца подстраивается под самую широкую ячейку.

```xml
<TableLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:stretchColumns="*">            растянуть все столбцы на всю ширину

    <TableRow>
        <Button android:text="7" />
        <Button android:text="8" />
        <Button android:text="9" />
        <Button android:text="÷" />
    </TableRow>

    <TableRow>
        <Button android:text="AC" />
        <Button android:text="⌫" />
        <Button
            android:layout_span="2"        ячейка на 2 столбца (как colspan в HTML)
            android:text="=" />
    </TableRow>
</TableLayout>
```

Атрибуты `TableLayout`:

- `android:stretchColumns="*"` или `"0,2"` — какие столбцы растянуть, чтобы заполнить ширину (`*` — все);
- `android:shrinkColumns` — какие столбцы можно сжимать, если не хватает места;
- `android:collapseColumns` — какие столбцы скрыть.

Атрибуты ячейки внутри `TableRow`:

- `android:layout_span="2"` — ячейка занимает 2 столбца;
- `android:layout_column="1"` — поставить ячейку в конкретный столбец (нумерация с 0).

Особенности: у `TableRow` всегда `layout_width="match_parent"`, а у ячеек ширина задаётся таблицей. Отдельных линий-границ у таблицы нет: их рисуют фоном или отступами.

**Зачем:** формы «подпись — поле», таблицы данных, клавиатуры калькулятора. **Калькулятор можно было сделать на TableLayout:** `TableRow` вместо горизонтальных `LinearLayout` и `layout_span="2"` вместо `layout_weight="2"` у кнопки «=». Мы выбрали `LinearLayout` с весами, потому что он одинаково делит и ширину, и высоту: строки кнопок растягиваются на всю высоту экрана.

## GridLayout — сетка

Сетка с заданным числом строк и столбцов, без вложенных строк.

```xml
<GridLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:columnCount="4"
    android:rowCount="5">

    <Button
        android:layout_width="0dp"
        android:layout_columnWeight="1"     растянуть по ширине
        android:text="7" />
    <Button
        android:layout_columnSpan="2"       на 2 столбца
        android:layout_gravity="fill"
        android:text="=" />
</GridLayout>
```

**Зачем:** клавиатуры, галереи, плитки, то есть когда нужна ровная сетка и не хочется вкладывать много `LinearLayout`.

## RelativeLayout — относительно друг друга

Элементы располагаются **относительно родителя или других элементов**.

```xml
<RelativeLayout ...>
    <TextView
        android:id="@+id/title"
        android:layout_alignParentTop="true"          прижать к верху родителя
        android:layout_centerHorizontal="true" />     по центру по горизонтали
    <Button
        android:layout_below="@id/title"              под элементом title
        android:layout_alignParentEnd="true" />       прижать вправо
</RelativeLayout>
```

**Зачем:** раньше был основным для сложных экранов, сейчас его заменил `ConstraintLayout`.

## ConstraintLayout — привязками (ограничениями)

Каждый элемент **привязывается** сторонами к родителю или к другим элементам. Это самый гибкий Layout, и он используется по умолчанию в новых проектах Android Studio (его разбирали в методичке к ЛР №1).

```xml
<androidx.constraintlayout.widget.ConstraintLayout ...>
    <TextView
        android:id="@+id/hello"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />      по центру экрана
    <Button
        app:layout_constraintTop_toBottomOf="@id/hello"
        app:layout_constraintStart_toStartOf="@id/hello" /> под текстом
</androidx.constraintlayout.widget.ConstraintLayout>
```

Нужна хотя бы одна привязка по вертикали и одна по горизонтали. Есть цепочки (chains), направляющие (Guideline), проценты.

**Зачем:** сложные экраны **без вложенности**. Плоская разметка быстрее отрисовывается, и её удобно делать мышкой в визуальном редакторе.

## FrameLayout — стопкой

Элементы кладутся **друг на друга** (в левый верхний угол или по `layout_gravity`).

```xml
<FrameLayout ...>
    <ImageView android:src="@drawable/photo" />          снизу картинка
    <TextView android:layout_gravity="bottom|center" />  сверху подпись
</FrameLayout>
```

**Зачем:** наложение элементов (подпись на картинке, индикатор загрузки поверх контента), контейнер для фрагментов.

## ScrollView — прокрутка

Позволяет **прокручивать** содержимое, которое не помещается на экран. Внутри должен быть **ровно один** дочерний элемент (обычно `LinearLayout`). `HorizontalScrollView` прокручивает по горизонтали.

```xml
<ScrollView
    android:layout_width="match_parent"
    android:layout_height="0dp"
    android:layout_weight="1.3"
    android:fillViewport="true">           растянуть содержимое на всю высоту, если его мало
    <TextView android:id="@+id/history" ... />
</ScrollView>
```

**В нашем калькуляторе:** поле истории лежит в `ScrollView`, чтобы длинную историю можно было прокрутить.

## Другие контейнеры

- **CoordinatorLayout** — для «умного» поведения: панель, которая прячется при прокрутке, плавающая кнопка, которая уезжает от всплывающего сообщения.
- **RecyclerView** — для длинных списков: показывает только видимые элементы и переиспользует их. История в настоящем приложении на сотни записей делалась бы на нём.
- **CardView, MaterialCardView** — карточка с тенью и скруглёнными углами.

## Сравнение: что когда использовать

| Layout | Как расставляет | Когда использовать |
|---|---|---|
| LinearLayout | в строку или столбец, веса | простые ряды, формы, ряды кнопок |
| TableLayout | строки `TableRow` и столбцы | таблицы, формы «подпись — поле», клавиатуры |
| GridLayout | ровная сетка | клавиатуры, плитки |
| RelativeLayout | относительно других элементов | старые проекты |
| ConstraintLayout | привязки сторон | сложные экраны без вложенности (стандарт сейчас) |
| FrameLayout | друг на друга | наложение, контейнер для фрагментов |
| ScrollView | прокрутка одного дочернего элемента | длинное содержимое |

## Общие атрибуты View и Layout

| Атрибут | Значение |
|---|---|
| `android:id="@+id/имя"` | id, по нему `findViewById(R.id.имя)` |
| `android:layout_width / layout_height` | `match_parent` (как у родителя), `wrap_content` (по содержимому), число в `dp` |
| `android:layout_margin` | внешний отступ (от соседей) |
| `android:padding` | внутренний отступ (от края до содержимого) |
| `android:gravity` | положение содержимого внутри |
| `android:layout_gravity` | положение элемента в родителе |
| `android:background` | фон |
| `android:visibility` | `visible`, `invisible` (невидим, но место занимает), `gone` (нет совсем) |
| `android:text`, `textSize`, `textColor` | текст, размер в `sp`, цвет |
| `android:hint` | серая подсказка, когда поле пустое |
| `android:tag` | любые свои данные (у нас — вставляемый текст кнопки) |
| `style="@style/..."` | набор атрибутов из `themes.xml` |

---

# Часть 8. Вопросы на защите по этой теме

| Вопрос | Ответ |
|---|---|
| Что происходит при повороте? | Изменение конфигурации: Activity уничтожается и создаётся заново, поля обнуляются. |
| Как сохранить данные? | Переопределить `onSaveInstanceState(Bundle)` и положить данные в Bundle методами `putXxx`. |
| В какой форме сохраняются? | В объекте **Bundle**: пары «ключ (String) → значение». Система сериализует его в Parcel и хранит в системном процессе. |
| Что можно положить в Bundle? | Примитивы, String, массивы, `ArrayList<String>`, `ArrayList<Integer>`, Parcelable, Serializable, вложенный Bundle. |
| Как сохраняется история? | `outState.putStringArrayList("history", history)`, где `history` — `ArrayList<String>`. |
| Где восстанавливаются данные? | В `onCreate(savedInstanceState)` с проверкой на null или в `onRestoreInstanceState(Bundle)`. |
| Зачем проверка на null? | При первом запуске `savedInstanceState == null`, без проверки будет `NullPointerException`. |
| Какие ещё проверки на null есть? | `getStringArrayList` может вернуть null; `findViewById` возвращает null, если элемента нет в текущей разметке; диалог мог не создаваться. |
| Зачем значение по умолчанию в `getString(key, "")`? | Чтобы при отсутствии ключа получить `""`, а не `null`. |
| Когда вызывается `onRestoreInstanceState`? | После `onStart()`, только если Bundle не пустой. |
| Когда НЕ вызывается `onSaveInstanceState`? | При нажатии «Назад» и вызове `finish()`. |
| Есть ли ограничение размера Bundle? | Да, он для небольших данных; при ~1 МБ будет `TransactionTooLargeException`. |
| Почему `super.onSaveInstanceState(outState)`? | Родитель сохраняет состояние стандартных View с id (например, текст EditText). |
| Сохранится ли история после закрытия приложения? | Нет, Bundle временный. Для постоянного хранения нужны SharedPreferences, база данных или файл. |
| Чем ViewModel отличается от Bundle? | ViewModel переживает поворот без сохранения, но пропадает при убийстве процесса; Bundle сохраняется и тогда. |
| Почему не `configChanges`? | Тогда не загрузится `layout-land`, и Google не рекомендует этот способ. |
| Как Android выбирает `layout-land`? | По квалификатору папки ресурсов: `land` — альбомная ориентация. |
| Чем TableLayout отличается от LinearLayout? | TableLayout выравнивает элементы по столбцам (строки `TableRow`, `stretchColumns`, `layout_span`), а LinearLayout ставит элементы в одну линию с весами. |
| Чем GridLayout отличается от TableLayout? | В GridLayout сетка задаётся числом строк и столбцов без `TableRow`, ячейки могут занимать несколько строк. |
| Какой Layout используется по умолчанию в новых проектах? | ConstraintLayout. |
