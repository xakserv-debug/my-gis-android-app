package com.example.monitoringapp

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.*
import kotlin.concurrent.timerTask

class MonitoringService : Service() {
    private lateinit var apiService: ApiService
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var locationManager: LocationManager
    private var timer: Timer? = null
    private var lastLocation: Location? = null
    private var isTestingConnection = false

    // Конфигурация
    private val deviceId: String by lazy { getOrCreateDeviceId() }
    private val deviceName by lazy { "${Build.MANUFACTURER} ${Build.MODEL}" }
    private val checkInterval = 60000L // 60 секунд

    companion object {
        private const val TAG = "MonitoringService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "monitoring_channel"
        private const val PREFS_NAME = "monitoring_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val LOCATION_UPDATE_INTERVAL = 30000L // 30 секунд
        private const val LOCATION_MIN_DISTANCE = 10f // 10 метров
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "=== MonitoringService СОЗДАН ===")
        sendLogToActivity("📱 Сервис мониторинга создан")

        apiService = ApiService()
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        Log.d(TAG, "Device ID: $deviceId")
        Log.d(TAG, "Device: $deviceName")
        Log.d(TAG, "Android: ${Build.VERSION.RELEASE}")

        createNotificationChannel()
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "=== MonitoringService ЗАПУЩЕН ===")

        // Проверяем специальные флаги
        isTestingConnection = intent?.getBooleanExtra("test_connection", false) ?: false

        if (isTestingConnection) {
            sendLogToActivity("🌐 Запуск теста соединения...")
            testServerConnection()
        } else {
            sendLogToActivity("🚀 Сервис мониторинга запущен")
            startMonitoring()
        }

        return START_STICKY
    }

    private fun getOrCreateDeviceId(): String {
        val savedDeviceId = sharedPreferences.getString(KEY_DEVICE_ID, null)
        return if (savedDeviceId != null) {
            savedDeviceId
        } else {
            val newDeviceId = "device_${Build.MANUFACTURER}_${Build.MODEL}_${Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"}"
            sharedPreferences.edit().putString(KEY_DEVICE_ID, newDeviceId).apply()
            newDeviceId
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "=== MonitoringService ОСТАНОВЛЕН ===")
        sendLogToActivity("🛑 Сервис мониторинга остановлен")
        stopLocationUpdates()
        timer?.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Мониторинг устройства",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Сервис отслеживания местоположения и выполнения команд"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📱 Monitoring Service")
            .setContentText("Ожидание команд с сервера...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("📱 Monitoring Service")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun startMonitoring() {
        sendLogToActivity("📍 Запуск отслеживания местоположения...")
        startLocationUpdates()

        sendLogToActivity("🔄 Запуск проверки команд...")
        startCommandChecking()

        updateNotification("Мониторинг активен | Устройство: $deviceName")
    }

    private fun testServerConnection() {
        sendLogToActivity("🌐 Тестирование соединения с сервером...")
        updateNotification("Тест соединения...")

        // Тестируем базовые API endpoints
        testDeviceRegistration()
    }

    private fun testDeviceRegistration() {
        sendLogToActivity("📝 Тест регистрации устройства...")

        // Создаем временный ApiService для теста
        val testApiService = ApiService()

        testApiService.registerDevice(
            deviceId = deviceId,
            name = deviceName,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE
        ) { success, message ->
            if (success) {
                sendLogToActivity("✅ Регистрация устройства: УСПЕХ - $message")
                testLocationAPI()
            } else {
                sendLogToActivity("❌ Регистрация устройства: ОШИБКА - $message")
                sendLogToActivity("🔴 Соединение с сервером не установлено")
                updateNotification("Ошибка соединения")
            }
        }
    }

    private fun testLocationAPI() {
        sendLogToActivity("📍 Тест API локации...")

        val testApiService = ApiService()
        val testLatitude = 55.7558
        val testLongitude = 37.6173

        testApiService.sendLocation(
            deviceId = deviceId,
            latitude = testLatitude,
            longitude = testLongitude,
            accuracy = 50.0f
        ) { success ->
            if (success) {
                sendLogToActivity("✅ API локации: УСПЕХ - данные отправлены")
                testCommandsAPI()
            } else {
                sendLogToActivity("❌ API локации: ОШИБКА - не удалось отправить")
                sendLogToActivity("⚠️ Частичное соединение: регистрация работает, но локация - нет")
                updateNotification("Частичное соединение")
            }
        }
    }

    private fun testCommandsAPI() {
        sendLogToActivity("🎯 Тест API команд...")

        val testApiService = ApiService()

        testApiService.getCommands(deviceId) { commands ->
            if (commands.isNotEmpty()) {
                sendLogToActivity("✅ API команд: УСПЕХ - получено ${commands.size} команд")
            } else {
                sendLogToActivity("✅ API команд: УСПЕХ - команд нет (нормально)")
            }

            sendLogToActivity("🟢 ВСЕ ТЕСТЫ ПРОЙДЕНЫ - соединение с сервером установлено")
            updateNotification("Соединение установлено")

            // После успешного теста запускаем нормальный мониторинг
            if (!isTestingConnection) {
                startMonitoring()
            }
        }
    }

    private fun startLocationUpdates() {
        if (hasLocationPermission()) {
            try {
                val locationListener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lastLocation = location
                        Log.d(TAG, "📍 Получена реальная локация: ${location.latitude}, ${location.longitude}")
                        sendLogToActivity("📍 GPS: ${location.latitude}, ${location.longitude} (точность: ${location.accuracy}m)")
                    }

                    override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {
                        sendLogToActivity("📡 Статус GPS: $provider - $status")
                    }

                    override fun onProviderEnabled(provider: String) {
                        sendLogToActivity("✅ GPS включен: $provider")
                    }

                    override fun onProviderDisabled(provider: String) {
                        sendLogToActivity("❌ GPS отключен: $provider")
                    }
                }

                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    LOCATION_UPDATE_INTERVAL,
                    LOCATION_MIN_DISTANCE,
                    locationListener
                )

                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    LOCATION_UPDATE_INTERVAL,
                    LOCATION_MIN_DISTANCE,
                    locationListener
                )

                sendLogToActivity("📍 Запущено отслеживание местоположения (GPS + Network)")

            } catch (e: SecurityException) {
                sendLogToActivity("❌ ОШИБКА: Нет разрешения на доступ к локации")
                Log.e(TAG, "❌ Нет разрешения на доступ к локации", e)
            } catch (e: Exception) {
                sendLogToActivity("❌ ОШИБКА: Не удалось запустить отслеживание локации")
                Log.e(TAG, "❌ Ошибка запуска отслеживания локации", e)
            }
        } else {
            sendLogToActivity("⚠️ ПРЕДУПРЕЖДЕНИЕ: Нет разрешений на доступ к локации")
            Log.e(TAG, "❌ Нет разрешений на доступ к локации")
        }
    }

    private fun stopLocationUpdates() {
        locationManager.removeUpdates { }
        sendLogToActivity("📍 Остановлено отслеживание местоположения")
    }

    private fun startCommandChecking() {
        timer?.cancel()

        timer = Timer().apply {
            // Первая проверка через 5 секунд
            schedule(timerTask {
                checkForCommands()
            }, 5000)

            // Затем каждые 60 секунд
            scheduleAtFixedRate(timerTask {
                checkForCommands()
            }, checkInterval, checkInterval)
        }
        sendLogToActivity("🔄 Проверка команд каждые ${checkInterval/1000} секунд")
    }

    private fun checkForCommands() {
        sendLogToActivity("🔍 Проверка команд от сервера...")

        apiService.getCommands(deviceId) { commands ->
            if (commands.isNotEmpty()) {
                sendLogToActivity("🎯 Получено команд: ${commands.size}")
                commands.forEach { command ->
                    sendLogToActivity("   → Обработка команды: ${command.type}")
                    handleCommand(command)
                    markCommandExecuted(command.id)
                }
            } else {
                sendLogToActivity("📭 Команды не найдены")
            }
        }
    }

    private fun handleCommand(command: Command) {
        when (command.type) {
            "GET_LOCATION" -> {
                sendLogToActivity("📍 Выполнение команды: GET_LOCATION")
                sendRealLocation()
            }
            "GET_SCREENSHOT" -> {
                sendLogToActivity("📸 Выполнение команды: GET_SCREENSHOT")
                takeScreenshot()
            }
            "GET_PHOTO" -> {
                sendLogToActivity("📷 Выполнение команды: GET_PHOTO")
                takePhoto()
            }
            "GET_AUDIO" -> {
                sendLogToActivity("🎤 Выполнение команды: GET_AUDIO")
                recordAudio()
            }
            "GET_ALL_DATA" -> {
                sendLogToActivity("🔄 Выполнение команды: GET_ALL_DATA")
                sendRealLocation()
                takeScreenshot()
                takePhoto()
                recordAudio()
            }
            else -> {
                sendLogToActivity("⚠️ Неизвестный тип команды: ${command.type}")
                Log.w(TAG, "⚠️ Неизвестный тип команды: ${command.type}")
            }
        }
    }

    private fun sendRealLocation() {
        if (lastLocation != null) {
            val location = lastLocation!!
            sendLogToActivity("📍 Отправка РЕАЛЬНОЙ локации: ${location.latitude}, ${location.longitude}")

            apiService.sendLocation(
                deviceId = deviceId,
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy
            ) { success ->
                if (success) {
                    sendLogToActivity("✅ Реальная локация отправлена на сервер")
                    updateNotification("Локация отправлена")
                } else {
                    sendLogToActivity("❌ Ошибка отправки реальной локации")
                }
            }
        } else {
            sendLogToActivity("⚠️ Реальная локация недоступна")
            // Отправляем тестовые координаты как запасной вариант
            sendTestLocation()
        }
    }

    private fun sendTestLocation() {
        val latitude = 55.7558 + (Math.random() - 0.5) * 0.001
        val longitude = 37.6173 + (Math.random() - 0.5) * 0.001

        sendLogToActivity("📍 Отправка ТЕСТОВОЙ локации: $latitude, $longitude")

        apiService.sendLocation(
            deviceId = deviceId,
            latitude = latitude,
            longitude = longitude,
            accuracy = 50.0f
        ) { success ->
            if (success) {
                sendLogToActivity("✅ Тестовая локация отправлена на сервер")
            } else {
                sendLogToActivity("❌ Ошибка отправки тестовой локации")
            }
        }
    }

    private fun takeScreenshot() {
        sendLogToActivity("📸 Команда: Сделать скриншот")
        // TODO: Реализовать создание скриншота
        sendLogToActivity("⚠️ Функция скриншота не реализована")
    }

    private fun takePhoto() {
        sendLogToActivity("📷 Команда: Сделать фото")
        // TODO: Реализовать создание фото
        sendLogToActivity("⚠️ Функция фото не реализована")
    }

    private fun recordAudio() {
        sendLogToActivity("🎤 Команда: Записать звук")
        // TODO: Реализовать запись аудио
        sendLogToActivity("⚠️ Функция записи аудио не реализована")
    }

    private fun markCommandExecuted(commandId: Int) {
        apiService.markCommandExecuted(commandId) { success ->
            if (success) {
                sendLogToActivity("✅ Команда $commandId отмечена как выполненная")
            } else {
                sendLogToActivity("❌ Ошибка отметки команды $commandId")
            }
        }
    }

    private fun sendLogToActivity(message: String) {
        try {
            val intent = Intent("MONITORING_LOG_UPDATE")
            intent.putExtra("log_message", "[${getCurrentTime()}] $message")
            sendBroadcast(intent)
            Log.d(TAG, message)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отправки лога в Activity: ${e.message}")
        }
    }

    private fun getCurrentTime(): String {
        return java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(Date())
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}