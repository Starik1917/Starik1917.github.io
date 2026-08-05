package com.starik1917.soundpad2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQUEST_MEDIA = 2001;
    private static final String PREFS = "soundpad_prefs_v5";
    private static final String KEY_PADS = "pads";

    private final ArrayList<PadItem> pads = new ArrayList<>();
    private final Set<MediaPlayer> players = new HashSet<>();
    private SharedPreferences prefs;
    private GridLayout grid;
    private TextView emptyView;
    private TextView countView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(9, 9, 15));
        getWindow().setNavigationBarColor(Color.rgb(9, 9, 15));
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadPads();
        setContentView(buildUi());
        renderPads();
    }

    private View buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(18), dp(18), dp(18));
        page.setBackgroundColor(Color.rgb(9, 9, 15));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_wave);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        logoLp.rightMargin = dp(10);
        header.addView(logo, logoLp);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        titles.addView(text("SoundPad", 28, Color.WHITE, true));
        countView = text("", 12, Color.rgb(171, 163, 196), false);
        titles.addView(countView);
        page.addView(header);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams toolbarLp = new LinearLayout.LayoutParams(-1, dp(52));
        toolbarLp.topMargin = dp(16);
        page.addView(toolbar, toolbarLp);

        TextView add = actionButton("＋  ADD", true);
        add.setOnClickListener(v -> openMediaPicker());
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(0, -1, 1f);
        addLp.rightMargin = dp(8);
        toolbar.addView(add, addLp);

        TextView stop = actionButton("■  STOP ALL", false);
        stop.setOnClickListener(v -> stopAll());
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(0, -1, 1f);
        stopLp.leftMargin = dp(8);
        toolbar.addView(stop, stopLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        scrollLp.topMargin = dp(18);
        page.addView(scroll, scrollLp);

        FrameLayout content = new FrameLayout(this);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setUseDefaultMargins(false);
        content.addView(grid, new FrameLayout.LayoutParams(-1, -2));

        emptyView = text("Нажми ADD и выбери MP3, аудио или видео.\nИз видео SoundPad проиграет только звук.", 15,
                Color.rgb(150, 143, 173), false);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(24), dp(80), dp(24), dp(80));
        content.addView(emptyView, new FrameLayout.LayoutParams(-1, -2));

        TextView hint = text("Тап — воспроизвести   •   удержание — переименовать / удалить", 11,
                Color.rgb(112, 106, 132), false);
        hint.setGravity(Gravity.CENTER);
        page.addView(hint);
        return page;
    }

    private void openMediaPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*", "video/*"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_MEDIA);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_MEDIA || resultCode != RESULT_OK || data == null) return;
        int added = 0;
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) if (addUri(clip.getItemAt(i).getUri())) added++;
        } else if (data.getData() != null && addUri(data.getData())) {
            added++;
        }
        if (added > 0) {
            savePads();
            renderPads();
            Toast.makeText(this, "Добавлено: " + added, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean addUri(Uri uri) {
        if (uri == null) return false;
        for (PadItem item : pads) if (item.uri.equals(uri.toString())) return false;
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        String name = queryDisplayName(uri);
        if (name == null || name.trim().isEmpty()) name = "Sound " + (pads.size() + 1);
        String mime = getContentResolver().getType(uri);
        if (mime == null) mime = "media";
        pads.add(new PadItem(name, uri.toString(), mime, queryDuration(uri)));
        return true;
    }

    private void renderPads() {
        grid.removeAllViews();
        emptyView.setVisibility(pads.isEmpty() ? View.VISIBLE : View.GONE);
        countView.setText(pads.isEmpty() ? "пэды пока не добавлены" : pads.size() + " " + pluralPads(pads.size()));
        int cardWidth = (getResources().getDisplayMetrics().widthPixels - dp(54)) / 2;

        for (int i = 0; i < pads.size(); i++) {
            final int index = i;
            PadItem item = pads.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(16), dp(15), dp(16), dp(15));
            card.setBackground(cardBackground());
            card.setOnClickListener(v -> playPad(item, card));
            card.setOnLongClickListener(v -> { showPadMenu(index); return true; });

            card.addView(text(item.mime.startsWith("video/") ? "VIDEO → AUDIO" : "AUDIO", 10,
                    Color.rgb(179, 160, 255), true));
            TextView name = text(trimName(item.name), 16, Color.WHITE, true);
            name.setMaxLines(2);
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(-1, -2);
            nameLp.topMargin = dp(9);
            card.addView(name, nameLp);
            TextView meta = text(item.durationMs > 0 ? formatDuration(item.durationMs) : "нажми, чтобы играть", 11,
                    Color.rgb(146, 137, 170), false);
            LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(-1, -2);
            metaLp.topMargin = dp(7);
            card.addView(meta, metaLp);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cardWidth;
            lp.height = dp(126);
            lp.setMargins(i % 2 == 0 ? 0 : dp(8), dp(6), i % 2 == 0 ? dp(8) : 0, dp(6));
            grid.addView(card, lp);
        }
    }

    private void playPad(PadItem item, View card) {
        MediaPlayer player = new MediaPlayer();
        players.add(player);
        try {
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(this, Uri.parse(item.uri));
            player.setOnPreparedListener(mp -> {
                card.setScaleX(0.97f);
                card.setScaleY(0.97f);
                card.animate().scaleX(1f).scaleY(1f).setDuration(130).start();
                mp.start();
            });
            player.setOnCompletionListener(this::releasePlayer);
            player.setOnErrorListener((mp, what, extra) -> {
                releasePlayer(mp);
                Toast.makeText(this, "Не удалось воспроизвести этот формат", Toast.LENGTH_SHORT).show();
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            releasePlayer(player);
            Toast.makeText(this, "Файл недоступен или формат не поддерживается", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopAll() {
        for (MediaPlayer player : players.toArray(new MediaPlayer[0])) releasePlayer(player);
        Toast.makeText(this, "Остановлено", Toast.LENGTH_SHORT).show();
    }

    private void releasePlayer(MediaPlayer player) {
        players.remove(player);
        try { player.stop(); } catch (Exception ignored) {}
        try { player.reset(); } catch (Exception ignored) {}
        try { player.release(); } catch (Exception ignored) {}
    }

    private void showPadMenu(int index) {
        PadItem item = pads.get(index);
        new AlertDialog.Builder(this)
                .setTitle(item.name)
                .setItems(new String[]{"Переименовать", "Удалить"}, (dialog, which) -> {
                    if (which == 0) renamePad(index);
                    else deletePad(index);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void renamePad(int index) {
        PadItem item = pads.get(index);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(item.name);
        input.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(20), 0, dp(20), 0);
        box.addView(input, new FrameLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle("Название пэда")
                .setView(box)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty()) {
                        item.name = value;
                        savePads();
                        renderPads();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void deletePad(int index) {
        pads.remove(index);
        savePads();
        renderPads();
    }

    private void loadPads() {
        pads.clear();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_PADS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                pads.add(new PadItem(o.optString("name", "Sound"), o.optString("uri", ""),
                        o.optString("mime", "media"), o.optLong("duration", 0)));
            }
        } catch (Exception ignored) { pads.clear(); }
    }

    private void savePads() {
        JSONArray arr = new JSONArray();
        try {
            for (PadItem item : pads) {
                JSONObject o = new JSONObject();
                o.put("name", item.name);
                o.put("uri", item.uri);
                o.put("mime", item.mime);
                o.put("duration", item.durationMs);
                arr.put(o);
            }
        } catch (Exception ignored) {}
        prefs.edit().putString(KEY_PADS, arr.toString()).apply();
    }

    private String queryDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return uri.getLastPathSegment();
    }

    private long queryDuration(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            String raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return raw == null ? 0 : Long.parseLong(raw);
        } catch (Exception ignored) {
            return 0;
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private TextView actionButton(String label, boolean primary) {
        TextView view = text(label, 13, primary ? Color.WHITE : Color.rgb(215, 209, 232), true);
        view.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        if (primary) bg.setColor(Color.rgb(120, 86, 255));
        else {
            bg.setColor(Color.rgb(28, 27, 38));
            bg.setStroke(dp(1), Color.rgb(67, 62, 83));
        }
        view.setBackground(bg);
        return view;
    }

    private GradientDrawable cardBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(22, 20, 31));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.rgb(45, 39, 63));
        return bg;
    }

    private String trimName(String name) {
        if (name == null) return "Sound";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot >= name.length() - 6) return name.substring(0, dot);
        return name;
    }

    private String formatDuration(long ms) {
        long total = Math.max(0, ms / 1000);
        long minutes = total / 60;
        long seconds = total % 60;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    private String pluralPads(int n) {
        int mod10 = n % 10, mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) return "пэд";
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return "пэда";
        return "пэдов";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        for (MediaPlayer player : players.toArray(new MediaPlayer[0])) releasePlayer(player);
        super.onDestroy();
    }

    private static class PadItem {
        String name;
        final String uri;
        final String mime;
        final long durationMs;
        PadItem(String name, String uri, String mime, long durationMs) {
            this.name = name;
            this.uri = uri;
            this.mime = mime;
            this.durationMs = durationMs;
        }
    }
}
