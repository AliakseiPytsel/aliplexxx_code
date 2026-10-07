# Ответы на контрольные вопросы (ЛР №2)

**1. Основные строительные блоки (View) интерфейса.**
`View` — базовый класс любого элемента экрана. Основные виджеты: `TextView` (текст), `EditText` (поле ввода),
`Button` / `ImageButton`, `ImageView`, `CheckBox`, `RadioButton`, `Switch`, `ProgressBar`, `SeekBar`, `Spinner`,
списки `RecyclerView` / `ListView`, `WebView`. В этой работе используются `TextView` (выражение и результат),
`Button` (клавиши) и `MaterialToolbar` (верхняя панель).

**2. Основные Layout / ViewGroup.**
`ViewGroup` — контейнер для других View. Основные: `LinearLayout` (элементы в строку или столбец, веса `layout_weight`),
`ConstraintLayout` (позиционирование привязками), `RelativeLayout`, `FrameLayout` (элементы друг над другом),
`GridLayout` / `TableLayout` (сетка), `CoordinatorLayout`, `ScrollView`. В калькуляторе сетка кнопок собрана
из вложенных `LinearLayout` с весами.

**3. Атрибуты View / ViewGroup.**
`android:id`, `layout_width` / `layout_height` (`match_parent`, `wrap_content`, `0dp` + вес), `layout_weight`,
`layout_margin*`, `padding*`, `gravity` (выравнивание содержимого), `layout_gravity` (положение в родителе),
`background`, `visibility`, `text`, `textSize`, `textColor`, `hint`, `tag`, `contentDescription`, `onClick`;
для `LinearLayout` — `orientation`; для `ConstraintLayout` — `layout_constraint*`. Повторяющиеся атрибуты удобно
выносить в стили (`res/values/themes.xml`, стиль `CalcKey`).

**4. Структура Android-проекта.**
- `settings.gradle.kts` — список модулей и репозиториев; `build.gradle.kts` (корневой) — версии плагинов;
  `gradle.properties`, `gradle/wrapper/` — настройки Gradle.
- `app/build.gradle.kts` — настройки модуля: `namespace`, `minSdk`/`targetSdk`, зависимости.
- `app/src/main/AndroidManifest.xml` — манифест.
- `app/src/main/java/...` — исходный код (Activity и другие классы).
- `app/src/main/res/` — ресурсы: `layout/` (разметки), `layout-land/` (разметки для альбомной ориентации),
  `values/` (строки, цвета, размеры, стили), `drawable/` (графика), `mipmap/` (иконки приложения).
- `app/src/test/` — unit-тесты, `app/src/androidTest/` — инструментальные тесты.
- Класс `R` генерируется автоматически и содержит id всех ресурсов (`R.layout.activity_main`, `R.id.result`).

**5. Основные компоненты приложения.**
`Activity` (экран), `Service` (фоновая работа без интерфейса), `BroadcastReceiver` (приём системных и других
широковещательных сообщений), `ContentProvider` (предоставление данных другим приложениям). Все они объявляются
в манифесте, а связываются между собой через `Intent`.

**6. Activity.**
Activity — один экран приложения с интерфейсом, через который пользователь взаимодействует с приложением.
Это класс-наследник `Activity` / `AppCompatActivity`. Он объявляется в манифесте; главная Activity получает
`intent-filter` с `MAIN` / `LAUNCHER`. Интерфейс задаётся в `onCreate()` через `setContentView(R.layout...)`.
Activity управляется системой по жизненному циклу. При смене конфигурации (например, при повороте) она
уничтожается и создаётся заново.

**7. Жизненный цикл Activity.**
`onCreate()` → `onStart()` → `onResume()` (Activity активна) → `onPause()` → `onStop()` → `onDestroy()`;
из остановленного состояния при возврате: `onRestart()` → `onStart()` → `onResume()`.
Состояния: created, started (видна), resumed (активна, в фокусе), paused (частично перекрыта), stopped (не видна),
destroyed. В `MainActivity` все эти методы пишут сообщения в Logcat с тегом `MainActivity`.

**8. Обработчик нажатия.**
Способы:
1. Реализовать интерфейс `View.OnClickListener` и назначить его через `button.setOnClickListener(...)`.
   Можно передать анонимный класс, лямбду `v -> {...}` или саму Activity (`this`).
2. Указать метод в XML: `android:onClick="methodName"` (метод `public void methodName(View v)` в Activity).

В калькуляторе Activity реализует `View.OnClickListener`. Один обработчик назначается всем кнопкам в цикле, а
вставляемый текст берётся из `android:tag` кнопки.

**9. Сохранение данных при изменении конфигурации.**
Перед уничтожением Activity система вызывает `onSaveInstanceState(Bundle outState)`. В него кладутся данные
(`outState.putString("expression", expression)`). Новая Activity получает этот же `Bundle` в
`onCreate(savedInstanceState)` и в `onRestoreInstanceState(Bundle)` (вызывается после `onStart()`, только если
`Bundle` не пустой). Оттуда данные читаются (`getString(...)`), после чего экран перерисовывается.
Обычный `TextView` сам свой текст не сохраняет, поэтому сохранение в калькуляторе реализовано явно.
Альтернатива — `ViewModel`, который переживает пересоздание Activity.

**10. Подключение сторонних библиотек.**
Через Gradle: в `app/build.gradle.kts` в блок `dependencies` добавляется строка вида
`implementation("группа:артефакт:версия")`, например `implementation("com.google.android.material:material:1.12.0")`.
Затем нажимается **Sync Now**, и Gradle скачивает библиотеку из репозиториев, указанных в `settings.gradle.kts`
(`google()`, `mavenCentral()`). Например, mXparser подключается как
`implementation("org.mariuszgromada.math:MathParser.org-mXparser:<версия>")`, а актуальную версию можно найти
на Maven Central. Также можно положить `.jar` / `.aar` в папку `app/libs` и подключить его через
`implementation(files("libs/имя.jar"))`. В этом проекте вычисления реализованы собственным парсером
(`ExpressionEvaluator`), а через Gradle подключены AndroidX и Material Components.
