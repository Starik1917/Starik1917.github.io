package ru.starik.chuvakide;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.text.method.ScrollingMovementMethod;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(13, 15, 20);
    private static final int PANEL = Color.rgb(23, 26, 33);
    private static final int PANEL2 = Color.rgb(31, 35, 44);
    private static final int TEXT = Color.rgb(232, 235, 242);
    private static final int MUTED = Color.rgb(145, 151, 166);
    private static final int ACCENT = Color.rgb(115, 103, 240);
    private static final int OK = Color.rgb(98, 210, 150);
    private static final int ERR = Color.rgb(255, 112, 112);

    private CodeEditor editor;
    private TextView console;
    private TextView fileNameLabel;
    private SharedPreferences prefs;
    private String currentName = "main.чувак";

    private static final String HELLO = "# первый чувак-код\nпечать(\"привет, мир!\")\nпечать(\"чувак жив\")";
    private static final String LOOP = "x = 1\nпока x <= 5:\n    печать(\"чувак №\" + x)\n    x = x + 1\nпечать(\"готово\")";
    private static final String IF_EXAMPLE = "очки = 1917\nесли очки >= 1000:\n    печать(\"легендарный чувак\")\nиначе:\n    печать(\"надо ещё чуваков\")";
    private static final String LIST_EXAMPLE = "числа = [1, 2, 3, 4]\nпечать(числа)\nпечать(\"длина: \" + длина(числа))";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("chuvak_ide", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildUi();
        currentName = prefs.getString("last_name", "main.чувак");
        editor.setText(prefs.getString("last_source", HELLO));
        updateFileLabel();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("ЧУВАК IDE", 22, TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));
        TextView badge = text("PYTHON, НО ХУЖЕ", 10, MUTED, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(round(PANEL2, dp(12), 0));
        header.addView(badge, new LinearLayout.LayoutParams(dp(116), dp(30)));
        root.addView(header);

        fileNameLabel = text("", 12, MUTED, false);
        fileNameLabel.setPadding(dp(2), 0, 0, dp(7));
        root.addView(fileNameLabel);

        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        addButton(tools, "▶ Запуск", ACCENT, v -> runCode());
        addButton(tools, "→ Чувак", PANEL2, v -> encodeCode());
        addButton(tools, "← Текст", PANEL2, v -> decodeCode());
        addButton(tools, "+ Новый", PANEL2, v -> newFile());
        addButton(tools, "Сохранить", PANEL2, v -> saveAs());
        addButton(tools, "Открыть", PANEL2, v -> openFile());
        addButton(tools, "Примеры", PANEL2, v -> examples());
        addButton(tools, "?", PANEL2, v -> help());
        hsv.addView(tools);
        root.addView(hsv, new LinearLayout.LayoutParams(-1, dp(48)));

        editor = new CodeEditor(this);
        editor.setTextColor(TEXT);
        editor.setTextSize(15);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setBackground(round(PANEL, dp(14), Color.rgb(42, 47, 58)));
        editor.setPadding(dp(52), dp(12), dp(12), dp(12));
        editor.setHorizontallyScrolling(true);
        editor.setHorizontalScrollBarEnabled(true);
        editor.setVerticalScrollBarEnabled(true);
        editor.setSingleLine(false);
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        root.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView consoleTitle = text("ВЫВОД", 11, MUTED, true);
        consoleTitle.setPadding(dp(2), dp(9), 0, dp(5));
        root.addView(consoleTitle);

        console = text("Нажми «Запуск». Процессор морально готов.", 13, MUTED, false);
        console.setTypeface(Typeface.MONOSPACE);
        console.setTextIsSelectable(true);
        console.setMovementMethod(new ScrollingMovementMethod());
        console.setGravity(Gravity.TOP | Gravity.START);
        console.setPadding(dp(12), dp(10), dp(12), dp(10));
        console.setBackground(round(Color.rgb(10, 12, 16), dp(12), Color.rgb(38, 42, 52)));
        root.addView(console, new LinearLayout.LayoutParams(-1, dp(128)));

        setContentView(root);

        editor.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){ prefs.edit().putString("last_source", s.toString()).apply(); editor.invalidate(); }
            public void afterTextChanged(Editable e){}
        });
    }

    private void runCode() {
        hideKeyboard();
        try {
            String result = new MiniInterpreter().run(editor.getText().toString());
            console.setTextColor(OK);
            console.setText(result);
        } catch (MiniInterpreter.LangException e) {
            console.setTextColor(ERR);
            console.setText("✕ " + e.getMessage());
        } catch (Throwable e) {
            console.setTextColor(ERR);
            console.setText("✕ Внутренняя ошибка: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void encodeCode() {
        editor.setText(ChuvakCodec.encode(editor.getText().toString()));
        editor.setSelection(editor.length());
        console.setTextColor(MUTED);
        console.setText("Исходник превращён в чувак-код. Он всё ещё запускается. По какой-то причине.");
    }

    private void decodeCode() {
        try {
            editor.setText(ChuvakCodec.decode(editor.getText().toString()));
            editor.setSelection(editor.length());
            console.setTextColor(MUTED);
            console.setText("Чувак-код декодирован обратно в читаемый текст.");
        } catch (IllegalArgumentException e) {
            console.setTextColor(ERR);
            console.setText("✕ " + e.getMessage());
        }
    }

    private void newFile() {
        currentName = "новый.чувак";
        editor.setText(HELLO);
        updateFileLabel();
        console.setTextColor(MUTED);
        console.setText("Новый файл создан локально.");
    }

    private void saveAs() {
        final EditText name = new EditText(this);
        name.setText(currentName);
        name.setSingleLine(true);
        name.setSelectAllOnFocus(true);
        name.setTextColor(Color.BLACK);
        new AlertDialog.Builder(this)
            .setTitle("Сохранить программу")
            .setView(name)
            .setPositiveButton("Сохранить", (d,w) -> {
                String n = name.getText().toString().trim();
                if (n.isEmpty()) n = "main.чувак";
                currentName = n;
                Set<String> names = new HashSet<>(prefs.getStringSet("file_names", new HashSet<>()));
                names.add(n);
                prefs.edit().putStringSet("file_names", names).putString("file_"+n, editor.getText().toString()).putString("last_name", n).apply();
                updateFileLabel();
                console.setTextColor(OK);
                console.setText("✓ Сохранено: " + n);
            })
            .setNegativeButton("Отмена", null).show();
    }

    private void openFile() {
        Set<String> set = prefs.getStringSet("file_names", new HashSet<>());
        if (set.isEmpty()) {
            console.setTextColor(MUTED);
            console.setText("Сохранённых программ пока нет.");
            return;
        }
        List<String> names = new ArrayList<>(set);
        Collections.sort(names);
        String[] arr = names.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Открыть программу").setItems(arr, (d, which) -> {
            currentName = arr[which];
            editor.setText(prefs.getString("file_"+currentName, ""));
            prefs.edit().putString("last_name", currentName).apply();
            updateFileLabel();
            console.setTextColor(OK);
            console.setText("✓ Открыто: " + currentName);
        }).setNegativeButton("Отмена", null).show();
    }

    private void examples() {
        String[] names = {"Привет, мир", "Цикл", "Если / иначе", "Список"};
        String[] code = {HELLO, LOOP, IF_EXAMPLE, LIST_EXAMPLE};
        new AlertDialog.Builder(this).setTitle("Примеры").setItems(names, (d, which) -> {
            currentName = "пример_" + (which + 1) + ".чувак";
            editor.setText(code[which]);
            updateFileLabel();
            console.setTextColor(MUTED);
            console.setText("Пример загружен. Его можно кодировать кнопкой «→ Чувак» и запускать в обоих видах.");
        }).show();
    }

    private void help() {
        String msg = "ЧУВАК-КОД v0.1\n\n" +
            "Язык похож на Python и выполняется полностью локально.\n\n" +
            "Команды:\n" +
            "печать(выражение)\n" +
            "если условие:\n    ...\nиначе:\n    ...\n" +
            "пока условие:\n    ...\n\n" +
            "Есть переменные, числа, строки, списки, + - * / %, == != < <= > >=, и / или / не, истина / ложь и длина(...).\n\n" +
            "Кнопка «→ Чувак» заменяет каждую русскую букву N повторами слова «чувак», где N — номер буквы русского алфавита с Ё. Символы разделяются |. Такой исходник можно запускать напрямую.\n\n" +
            "Защита: нет доступа к shell, сети или файлам системы; цикл принудительно обрывается после 10 000 итераций.";
        TextView body = text(msg, 14, Color.BLACK, false);
        body.setPadding(dp(20), dp(8), dp(20), dp(8));
        body.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        new AlertDialog.Builder(this).setTitle("Справка").setView(scroll).setPositiveButton("Понял", null).show();
    }

    private void updateFileLabel() {
        fileNameLabel.setText("● " + currentName + "   •   локально");
    }

    private void addButton(LinearLayout row, String label, int color, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(TEXT);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setBackground(round(color, dp(12), 0));
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(38));
        lp.setMargins(0, dp(3), dp(7), dp(5));
        row.addView(b, lp);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void hideKeyboard() { ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(editor.getWindowToken(), 0); }

    @Override protected void onPause() {
        super.onPause();
        prefs.edit().putString("last_source", editor.getText().toString()).putString("last_name", currentName).apply();
    }

    private final class CodeEditor extends EditText {
        private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        CodeEditor(Context c) {
            super(c);
            numberPaint.setColor(Color.rgb(92, 99, 118));
            numberPaint.setTextSize(dp(11));
            numberPaint.setTypeface(Typeface.MONOSPACE);
            dividerPaint.setColor(Color.rgb(45, 50, 62));
            dividerPaint.setStrokeWidth(dp(1));
        }
        @Override protected void onDraw(Canvas canvas) {
            android.text.Layout layout = getLayout();
            if (layout != null) {
                int first = Math.max(0, layout.getLineForVertical(getScrollY()));
                int last = Math.min(layout.getLineCount()-1, layout.getLineForVertical(getScrollY()+getHeight()));
                float fixedX = getScrollX();
                for (int i=first;i<=last;i++) {
                    float y = getExtendedPaddingTop() + layout.getLineBaseline(i);
                    canvas.drawText(String.valueOf(i+1), fixedX + dp(8), y, numberPaint);
                }
                canvas.drawLine(fixedX + dp(43), getScrollY(), fixedX + dp(43), getScrollY()+getHeight(), dividerPaint);
            }
            super.onDraw(canvas);
        }
    }
}
