package com.example.monitoringapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.constraintlayout.widget.ConstraintLayout
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var connectionStatus: TextView
    private lateinit var lastUpdate: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var forceButton: Button
    private lateinit var logText: TextView
    private lateinit var clearLogsButton: Button

    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            addLog("✅ Все разрешения получены")
            updateStatus("Все разрешения получены\nЗапуск сервиса...")
            startMonitoringService()
        } else {
            addLog("❌ Не все разрешения получены")
            showPermissionDeniedDialog()
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "=== MainActivity СОЗДАН ===")

        createSimpleLayout()

        addLog("📱 Приложение запущено")
        addLog("🔍 Ожидание запуска мониторинга...")
        updateStatus("Инициализация...")
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        addLog("🔄 Приложение активно")
    }

    private fun createSimpleLayout() {
        val layout = ConstraintLayout(this).apply {
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(40, 60, 40, 40)
            setBackgroundColor(0xFFF5F5F5.toInt())
        }

        // Заголовок
        val titleText = TextView(this).apply {
            id = TextView.generateViewId()
            text = "📱 Monitoring App"
            textSize = 24f
            setTextColor(0xFF333333.toInt())
            setPadding(0, 0, 0, 20)
        }

        statusText = TextView(this).apply {
            id = TextView.generateViewId()
            text = "Статус: Инициализация..."
            textSize = 16f
            setTextColor(0xFF000000.toInt())
            setPadding(0, 0, 0, 10)
        }

        connectionStatus = TextView(this).apply {
            id = TextView.generateViewId()
            text = "🔴 Не подключено к серверу"
            textSize = 14f
            setTextColor(0xFFe53e3e.toInt())
            setPadding(0, 0, 0, 5)
        }

        lastUpdate = TextView(this).apply {
            id = TextView.generateViewId()
            text = "Последнее обновление: --:--:--"
            textSize = 12f
            setTextColor(0xFF666666.toInt())
            setPadding(0, 0, 0, 20)
        }

        startButton = Button(this).apply {
            id = Button.generateViewId()
            text = "🚀 ЗАПУСТИТЬ МОНИТОРИНГ"
            setBackgroundColor(0xFF4CAF50.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(50, 25, 50, 25)
            textSize = 14f
        }

        stopButton = Button(this).apply {
            id = Button.generateViewId()
            text = "⏹️ ОСТАНОВИТЬ МОНИТОРИНГ"
            setBackgroundColor(0xFFF44336.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(50, 25, 50, 25)
            textSize = 14f
            isEnabled = false
            alpha = 0.5f
        }

        forceButton = Button(this).apply {
            id = Button.generateViewId()
            text = "📡 ПРОВЕРИТЬ СОЕДИНЕНИЕ"
            setBackgroundColor(0xFF2196F3.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(50, 20, 50, 20)
            textSize = 12f
        }

        clearLogsButton = Button(this).apply {
            id = Button.generateViewId()
            text = "🧹 ОЧИСТИТЬ ЛОГИ"
            setBackgroundColor(0xFFFF9800.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(50, 15, 50, 15)
            textSize = 12f
        }

        logText = TextView(this).apply {
            id = TextView.generateViewId()
            text = "Логи:\n- Готов к работе\n- Нажмите 'Запустить мониторинг'"
            textSize = 12f
            setTextColor(0xFF666666.toInt())
            setPadding(0, 30, 0, 0)
            setBackgroundColor(0xFFEEEEEE.toInt())
            setPadding(20, 20, 20, 20)
        }

        layout.addView(titleText)
        layout.addView(statusText)
        layout.addView(connectionStatus)
        layout.addView(lastUpdate)
        layout.addView(startButton)
        layout.addView(stopButton)
        layout.addView(forceButton)
        layout.addView(clearLogsButton)
        layout.addView(logText)

        setupConstraints(layout, titleText)
        setContentView(layout)
        setupClickListeners()

        Log.d(TAG, "=== Layout создан успешно ===")
    }

    private fun setupConstraints(layout: ConstraintLayout, titleText: TextView) {
        // Title constraints
        val titleParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.WRAP_CONTENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        }

        // Status constraints
        val statusParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topToBottom = titleText.id
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topMargin = 20
        }

        // Connection status constraints
        val connectionParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topToBottom = statusText.id
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topMargin = 5
        }

        // Last update constraints
        val lastUpdateParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topToBottom = connectionStatus.id
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topMargin = 5
        }

        // Start button constraints
        val startParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topToBottom = lastUpdate.id
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topMargin = 20
        }

        // Stop button constraints
        val stopParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topToBottom = startButton.id
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topMargin = 10
        }

        // Force button constraints
        val forceParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topToBottom = stopButton.id
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topMargin = 10
        }

        // Clear logs button constraints
        val clearParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topToBottom = forceButton.id
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topMargin = 10
        }

        // Log text constraints
        val logParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
        ).apply {
            topToBottom = clearLogsButton.id
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            topMargin = 20
        }

        titleText.layoutParams = titleParams
        statusText.layoutParams = statusParams
        connectionStatus.layoutParams = connectionParams
        lastUpdate.layoutParams = lastUpdateParams
        startButton.layoutParams = startParams
        stopButton.layoutParams = stopParams
        forceButton.layoutParams = forceParams
        clearLogsButton.layoutParams = clearParams
        logText.layoutParams = logParams
    }

    private fun setupClickListeners() {
        startButton.setOnClickListener {
            addLog("🔄 Запрос на запуск мониторинга")
            checkPermissions()
        }

        stopButton.setOnClickListener {
            addLog("🛑 Запрос на остановку мониторинга")
            stopMonitoringService()
        }

        forceButton.setOnClickListener {
            addLog("📡 Принудительная проверка соединения")
            testConnection()
        }

        clearLogsButton.setOnClickListener {
            addLog("🧹 Очистка логов")
            clearLogs()
        }
    }

    private fun testConnection() {
        addLog("🌐 Тестирование соединения с сервером...")
        updateConnectionStatus("🔄 Проверка соединения...", 0xFFFF9800.toInt())

        // Запускаем сервис для теста соединения
        val intent = Intent(this, MonitoringService::class.java).apply {
            putExtra("test_connection", true)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            addLog("📤 Запрос отправлен на сервер")
        } catch (e: Exception) {
            addLog("❌ Ошибка запуска теста: ${e.message}")
        }
    }

    private fun checkPermissions() {
        try {
            val missingPermissions = requiredPermissions.filter { permission ->
                ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
            }

            if (missingPermissions.isEmpty()) {
                addLog("✅ Все разрешения уже предоставлены")
                updateStatus("Все разрешения получены")
                startMonitoringService()
            } else {
                addLog("📋 Запрос разрешений: ${missingPermissions.size} шт.")
                updateStatus("Запрос разрешений...")
                permissionLauncher.launch(missingPermissions.toTypedArray())
            }
        } catch (e: Exception) {
            addLog("❌ Ошибка проверки разрешений: ${e.message}")
        }
    }

    private fun startMonitoringService() {
        addLog("🚀 Запуск сервиса мониторинга...")
        updateStatus("Запуск сервиса...")

        val intent = Intent(this, MonitoringService::class.java)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
                addLog("✅ Сервис запущен как foreground service")
            } else {
                startService(intent)
                addLog("✅ Сервис запущен")
            }
            updateStatus("Сервис мониторинга активен")
            updateButtonStates(true)
            addLog("📡 Сервис активен - ожидание команд")
            updateConnectionStatus("🟢 Подключено к серверу", 0xFF4CAF50.toInt())
        } catch (e: Exception) {
            addLog("❌ Ошибка запуска: ${e.message}")
            updateStatus("Ошибка запуска")
            updateConnectionStatus("🔴 Ошибка подключения", 0xFFe53e3e.toInt())
        }
    }

    private fun stopMonitoringService() {
        addLog("🛑 Остановка сервиса...")
        updateStatus("Остановка сервиса...")

        try {
            val intent = Intent(this, MonitoringService::class.java)
            stopService(intent)

            updateStatus("Сервис остановлен")
            updateButtonStates(false)
            updateConnectionStatus("🔴 Отключено от сервера", 0xFFe53e3e.toInt())
            addLog("✅ Сервис остановлен")
            addLog("🔍 Для возобновления нажмите 'Запустить мониторинг'")
        } catch (e: Exception) {
            addLog("❌ Ошибка остановки: ${e.message}")
        }
    }

    private fun updateServiceStatus() {
        // Простая проверка - всегда считаем что сервис не запущен при старте
        updateStatus("Готов к работе")
        updateButtonStates(false)
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            try {
                statusText.text = "Статус: $message"
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка обновления статуса", e)
            }
        }
    }

    private fun updateConnectionStatus(message: String, color: Int) {
        runOnUiThread {
            try {
                connectionStatus.text = message
                connectionStatus.setTextColor(color)
                lastUpdate.text = "Последнее обновление: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())}"
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка обновления статуса соединения", e)
            }
        }
    }

    private fun addLog(message: String) {
        runOnUiThread {
            try {
                val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                val newLog = "$timestamp - $message"
                val currentText = logText.text.toString()

                if (currentText.startsWith("Логи:\n")) {
                    logText.text = currentText + "\n" + newLog
                } else {
                    logText.text = "Логи:\n" + newLog
                }

                // Прокручиваем к самому низу
                logText.post {
                    try {
                        val scrollAmount = logText.layout?.getLineTop(logText.lineCount) ?: 0 - logText.height
                        if (scrollAmount > 0) {
                            logText.scrollTo(0, scrollAmount)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка прокрутки логов", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка добавления лога", e)
            }
        }
    }

    private fun clearLogs() {
        runOnUiThread {
            try {
                logText.text = "Логи:\n- Логи очищены\n- Готов к работе"
                addLog("🧹 Логи очищены")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка очистки логов", e)
            }
        }
    }

    private fun updateButtonStates(serviceRunning: Boolean) {
        runOnUiThread {
            try {
                startButton.isEnabled = !serviceRunning
                stopButton.isEnabled = serviceRunning
                forceButton.isEnabled = serviceRunning

                startButton.alpha = if (serviceRunning) 0.5f else 1.0f
                stopButton.alpha = if (serviceRunning) 1.0f else 0.5f
                forceButton.alpha = if (serviceRunning) 1.0f else 0.5f

                if (serviceRunning) {
                    startButton.text = "✅ МОНИТОРИНГ АКТИВЕН"
                    stopButton.text = "⏹️ ОСТАНОВИТЬ МОНИТОРИНГ"
                    forceButton.text = "📡 ПРОВЕРИТЬ СОЕДИНЕНИЕ"
                } else {
                    startButton.text = "🚀 ЗАПУСТИТЬ МОНИТОРИНГ"
                    stopButton.text = "⏹️ МОНИТОРИНГ ОСТАНОВЛЕН"
                    forceButton.text = "📡 ПРОВЕРИТЬ СОЕДИНЕНИЕ"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка обновления кнопок", e)
            }
        }
    }

    private fun showPermissionDeniedDialog() {
        addLog("❌ Не все разрешения предоставлены")

        try {
            AlertDialog.Builder(this)
                .setTitle("Требуются разрешения")
                .setMessage("Для работы приложения необходимы все запрошенные разрешения:\n\n• 📍 Доступ к местоположению\n• 📷 Доступ к камере\n• 🎤 Доступ к микрофону\n• 🔔 Показ уведомлений\n\nБез них мониторинг невозможен.")
                .setPositiveButton("🔄 Повторить") { _, _ ->
                    addLog("🔄 Повторный запрос разрешений")
                    checkPermissions()
                }
                .setNegativeButton("⚙️ Настройки") { _, _ ->
                    addLog("⚙️ Открытие настроек приложения")
                    openAppSettings()
                }
                .setNeutralButton("❌ Отмена") { dialog, _ ->
                    dialog.dismiss()
                    updateStatus("Мониторинг не запущен - нет разрешений")
                    addLog("❌ Пользователь отменил запрос разрешений")
                }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            addLog("❌ Ошибка показа диалога разрешений")
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            addLog("📱 Открыты настройки приложения")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка открытия настроек: ${e.message}")
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_SETTINGS)
                startActivity(intent)
                addLog("📱 Открыты общие настройки приложения")
            } catch (e2: Exception) {
                addLog("❌ Не удалось открыть настройки")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        try {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                addLog("✅ Разрешения получены (через onRequestPermissionsResult)")
                startMonitoringService()
            } else {
                addLog("❌ Разрешения отклонены (через onRequestPermissionsResult)")
                showPermissionDeniedDialog()
            }
        } catch (e: Exception) {
            addLog("❌ Ошибка обработки разрешений")
        }
    }
}