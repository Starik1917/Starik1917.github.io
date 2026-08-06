package com.starik1917.aitelegramnative

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat

class MainActivity : Activity() {

    companion object {
        const val ACTION_STATUS = "com.starik1917.aitelegramnative.STATUS"
        private const val PICK_GGUF = 1001
        private const val MODEL_FILE = "Qwen3-8B-Q4_K_M.gguf"
        private const val MODEL_URL = "https://huggingface.co/Qwen/Qwen3-8B-GGUF/resolve/main/Qwen3-8B-Q4_K_M.gguf?download=true"
    }

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    private lateinit var tokenField: EditText
    private lateinit var delayField: EditText
    private lateinit var pauseField: EditText
    private lateinit var promptField: EditText
    private lateinit var modelStatus: TextView
    private lateinit var serviceStatus: TextView
    private lateinit var logView: TextView
    private lateinit var downloadButton: Button

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra("status") ?: return
            serviceStatus.text = status
            val log = intent.getStringExtra("log")
            if (!log.isNullOrBlank()) appendLog(log)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadSettings()
        requestNotificationPermissionIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(statusReceiver, filter)
        }
    }

    override fun onStop() {
        try { unregisterReceiver(statusReceiver) } catch (_: Exception) {}
        super.onStop()
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(32))
        }
        val scroll = ScrollView(this).apply { addView(root) }

        root.addView(TextView(this).apply {
            text = "AI Telegram — Context v2"
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Нативный llama.cpp • постоянная память чатов • защита от повторов"
            textSize = 15f
            setPadding(0, dp(4), 0, dp(18))
        })

        serviceStatus = cardText("Остановлен")
        root.addView(serviceStatus)

        root.addView(section("Локальная модель"))
        modelStatus = cardText("Модель не выбрана")
        root.addView(modelStatus)

        root.addView(button("Выбрать уже скачанный .GGUF") { chooseModel() })
        downloadButton = button("Скачать Qwen3-8B Q4_K_M (~5,0 ГБ)") { downloadOfficialModel() }
        root.addView(downloadButton)
        root.addView(TextView(this).apply {
            text = "Рекомендуется Qwen3-8B Q4_K_M. Старый Qwen3-4B тоже можно оставить — приложение принимает любой совместимый GGUF."
            textSize = 13f
            setPadding(0, dp(6), 0, dp(18))
        })

        root.addView(section("Telegram Business"))
        tokenField = edit("Telegram Bot Token", singleLine = true)
        root.addView(tokenField)

        delayField = edit("Задержка ответа, сек.", singleLine = true)
        root.addView(delayField)
        pauseField = edit("Пауза после моего ручного ответа, мин.", singleLine = true)
        root.addView(pauseField)

        root.addView(section("Как отвечать"))
        promptField = edit("Инструкция для ИИ", singleLine = false).apply {
            minLines = 7
            gravity = Gravity.TOP
        }
        root.addView(promptField)

        root.addView(button("Запустить автоответы") { startAutoReply() })
        root.addView(button("Остановить") {
            stopService(Intent(this, AutoReplyService::class.java))
            serviceStatus.text = "Остановлен"
            appendLog("Сервис остановлен вручную")
        })

        root.addView(section("Память переписки"))
        root.addView(TextView(this).apply {
            text = "Приложение хранит до 120 последних сообщений каждого чата локально в SQLite и после перезапуска продолжает помнить контекст. Telegram Bot API не отдаёт старую историю, которая была до получения ботом обновлений."
            textSize = 13f
            setPadding(0, 0, 0, dp(8))
        })
        root.addView(button("Очистить память выбранного чата") { showClearChatDialog() })
        root.addView(button("Очистить память ВСЕХ чатов") { confirmClearAll() })

        root.addView(button("Разрешить работу в фоне (OriginOS)") { requestBackgroundAllowance() })
        root.addView(TextView(this).apply {
            text = "На OriginOS дополнительно включи для этого приложения автозапуск и высокий/неограниченный фоновый расход батареи, а затем закрепи его в списке недавних."
            textSize = 13f
            setPadding(0, dp(6), 0, dp(18))
        })

        root.addView(section("Журнал"))
        logView = TextView(this).apply {
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xfff1f3f5.toInt())
            text = "Готово."
        }
        root.addView(logView)

        setContentView(scroll)
    }

    private fun loadSettings() {
        tokenField.setText(prefs.getString("bot_token", "") ?: "")
        delayField.setText(prefs.getString("delay_sec", "3.5") ?: "3.5")
        pauseField.setText(prefs.getString("manual_pause_min", "10") ?: "10")
        promptField.setText(
            prefs.getString(
                "persona",
                "Пиши по-русски естественно, коротко и по делу, как обычный человек в личной переписке. Отвечай от первого лица владельца аккаунта. Учитывай смысл всей переписки, а не только последнее сообщение. Не повторяй дословно ни собеседника, ни свои предыдущие ответы. Если человек пишет «а?», «что?», «в смысле?», объясни предыдущую мысль другими словами. Не выдумывай факты обо мне: если информации не хватает, задай короткий уточняющий вопрос."
            )
        )
        refreshModelStatus()
    }

    private fun saveSettings() {
        prefs.edit()
            .putString("bot_token", tokenField.text.toString().trim())
            .putString("delay_sec", delayField.text.toString().trim().replace(',', '.'))
            .putString("manual_pause_min", pauseField.text.toString().trim().replace(',', '.'))
            .putString("persona", promptField.text.toString().trim())
            .apply()
    }

    private fun chooseModel() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, PICK_GGUF)
    }

    @Deprecated("Deprecated in Android API; retained for API 24+ compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_GGUF || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}
        prefs.edit().putString("model_uri", uri.toString()).remove("model_path").apply()
        refreshModelStatus()
        appendLog("Выбран локальный GGUF: ${uri.lastPathSegment ?: uri}")
    }

    private fun downloadOfficialModel() {
        val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (dir == null) {
            toast("Android не дал каталог для модели")
            return
        }
        val file = File(dir, MODEL_FILE)
        if (file.exists() && file.length() > 4_000_000_000L) {
            prefs.edit().putString("model_path", file.absolutePath).remove("model_uri").apply()
            refreshModelStatus()
            appendLog("Готовая Qwen3-8B уже найдена: ${formatBytes(file.length())}")
            return
        }

        downloadButton.isEnabled = false
        serviceStatus.text = "Скачивание Qwen3-8B…"
        appendLog("Запускаю системную загрузку Qwen3-8B Q4_K_M")

        uiScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (file.exists()) file.delete()
                    val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                    val request = DownloadManager.Request(Uri.parse(MODEL_URL))
                        .setTitle("Qwen3-8B Q4_K_M")
                        .setDescription("Локальная модель для AI Telegram")
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(false)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalFilesDir(this@MainActivity, Environment.DIRECTORY_DOWNLOADS, MODEL_FILE)

                    val id = dm.enqueue(request)
                    var done = false
                    while (!done) {
                        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                            if (!c.moveToFirst()) throw IllegalStateException("DownloadManager потерял задачу")
                            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            val got = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            withContext(Dispatchers.Main) {
                                serviceStatus.text = if (total > 0) {
                                    "Скачивание: ${(got * 100 / total).coerceIn(0, 100)}% • ${formatBytes(got)} / ${formatBytes(total)}"
                                } else "Скачивание: ${formatBytes(got)}"
                            }
                            when (status) {
                                DownloadManager.STATUS_SUCCESSFUL -> done = true
                                DownloadManager.STATUS_FAILED -> {
                                    val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                                    throw IllegalStateException("DownloadManager error $reason")
                                }
                            }
                        }
                        if (!done) delay(1000)
                    }
                }

                if (!file.exists() || file.length() < 4_000_000_000L) {
                    throw IllegalStateException("Файл Qwen3-8B не появился или скачался не полностью")
                }
                prefs.edit().putString("model_path", file.absolutePath).remove("model_uri").apply()
                refreshModelStatus()
                serviceStatus.text = "Qwen3-8B скачана"
                appendLog("Qwen3-8B скачана: ${formatBytes(file.length())}")
            } catch (e: Exception) {
                serviceStatus.text = "Ошибка загрузки модели"
                appendLog("Ошибка загрузки: ${e.message}")
            } finally {
                downloadButton.isEnabled = true
            }
        }
    }

    private fun startAutoReply() {
        saveSettings()
        val token = tokenField.text.toString().trim()
        if (token.isBlank()) {
            toast("Вставь Telegram Bot Token")
            return
        }
        if ((prefs.getString("model_path", "") ?: "").isBlank() && (prefs.getString("model_uri", "") ?: "").isBlank()) {
            toast("Сначала выбери или скачай GGUF-модель")
            return
        }

        val intent = Intent(this, AutoReplyService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        serviceStatus.text = "Запуск…"
        appendLog("Запускаю нативный сервис Context v2")
    }

    private fun showClearChatDialog() {
        val db = ChatDb(this)
        val chats = try { db.recentChats(30) } finally { db.close() }
        if (chats.isEmpty()) {
            toast("Сохранённых чатов пока нет")
            return
        }
        val labels = chats.map { "${it.second}  •  ${it.first}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Очистить память какого чата?")
            .setItems(labels) { _, which ->
                val target = chats[which]
                ChatDb(this).use { it.clearChat(target.first) }
                appendLog("Очищена память чата ${target.second}")
                toast("Память чата очищена")
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle("Очистить всю память?")
            .setMessage("Будет удалена только локальная история AI Telegram. Сообщения в самом Telegram не удалятся.")
            .setPositiveButton("Очистить") { _, _ ->
                ChatDb(this).use { it.clearAll() }
                appendLog("Очищена память всех чатов")
                toast("Локальная память очищена")
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun refreshModelStatus() {
        val path = prefs.getString("model_path", "") ?: ""
        val uri = prefs.getString("model_uri", "") ?: ""
        modelStatus.text = when {
            path.isNotBlank() -> {
                val f = File(path)
                if (f.exists()) "✓ ${f.name} • ${formatBytes(f.length())}" else "Файл модели не найден"
            }
            uri.isNotBlank() -> "✓ Выбран GGUF через Android Files"
            else -> "Модель не выбрана"
        }
    }

    private fun requestBackgroundAllowance() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                    return
                }
            }
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001)
        }
    }

    private fun appendLog(text: String) {
        val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        logView.append("\n[$now] $text")
    }

    private fun section(text: String) = TextView(this).apply {
        this.text = text
        textSize = 19f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(18), 0, dp(8))
    }

    private fun cardText(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setPadding(dp(14), dp(14), dp(14), dp(14))
        setBackgroundColor(0xffeef3f8.toInt())
    }

    private fun edit(hint: String, singleLine: Boolean) = EditText(this).apply {
        this.hint = hint
        this.isSingleLine = singleLine
        textSize = 16f
        setPadding(dp(12), dp(12), dp(12), dp(12))
    }

    private fun button(text: String, click: (View) -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 16f
        setOnClickListener(click)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun formatBytes(v: Long): String {
        if (v <= 0) return "0 Б"
        val gb = v / 1024.0 / 1024.0 / 1024.0
        return if (gb >= 1) DecimalFormat("0.00").format(gb) + " ГБ" else DecimalFormat("0").format(v / 1024.0 / 1024.0) + " МБ"
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
